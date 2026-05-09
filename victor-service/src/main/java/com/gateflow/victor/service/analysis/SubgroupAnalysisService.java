package com.gateflow.victor.service.analysis;

import com.gateflow.victor.domain.dto.SubgroupAnalysisResponse;
import com.gateflow.victor.domain.entity.Experiment;
import com.gateflow.victor.domain.entity.Variant;
import com.gateflow.victor.infra.mapper.ExperimentMapper;
import com.gateflow.victor.infra.mapper.VariantMapper;
import com.gateflow.victor.stats.config.StatsClickHouseConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 人群拆分分析服务 - 分析实验效果在不同用户群体中的差异
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubgroupAnalysisService {

    private final StatsClickHouseConfig clickHouseConfig;
    private final ExperimentMapper experimentMapper;
    private final VariantMapper variantMapper;

    /**
     * 按平台拆分分析
     */
    public SubgroupAnalysisResponse analyzeByPlatform(Long experimentId, LocalDate startDate, LocalDate endDate) {
        return analyzeByDimension(experimentId, "platform", startDate, endDate);
    }

    /**
     * 按通用维度拆分分析
     * 
     * @param experimentId 实验ID
     * @param dimension 拆分维度 (platform, device_id 等)
     * @param startDate 开始日期
     * @param endDate 结束日期
     */
    public SubgroupAnalysisResponse analyzeByDimension(Long experimentId, String dimension, 
                                                        LocalDate startDate, LocalDate endDate) {
        Experiment experiment = experimentMapper.selectById(experimentId);
        if (experiment == null) {
            throw new IllegalArgumentException("Experiment not found: " + experimentId);
        }

        SubgroupAnalysisResponse response = new SubgroupAnalysisResponse();
        response.setExperimentId(experimentId);
        response.setExperimentKey(experiment.getExpId());
        response.setExperimentName(experiment.getName());
        response.setDimension(dimension);
        response.setStartDate(startDate);
        response.setEndDate(endDate);
        response.setPrimaryMetric(experiment.getPrimaryMetric());

        List<Variant> variants = variantMapper.selectActiveVariants(experiment.getExpId());
        if (variants.size() < 2) {
            response.setSubgroups(new ArrayList<>());
            return response;
        }

        String controlVariant = variants.get(0).getBucketId();
        String treatmentVariant = variants.size() > 1 ? variants.get(1).getBucketId() : null;

        // 从 ClickHouse 查询分维度数据
        Map<String, SubgroupAnalysisResponse.SubgroupResult> subgroups = 
            querySubgroupStats(experiment.getExpId(), dimension, startDate, endDate, controlVariant, treatmentVariant);

        // 计算统计检验
        for (SubgroupAnalysisResponse.SubgroupResult sg : subgroups.values()) {
            if (sg.getControlUsers() > 0 && sg.getTreatmentUsers() > 0) {
                ZTestResult zTest = runZTest(
                    (long) (sg.getControlConversionRate() * sg.getControlUsers()),
                    sg.getControlUsers(),
                    (long) (sg.getTreatmentConversionRate() * sg.getTreatmentUsers()),
                    sg.getTreatmentUsers()
                );
                
                sg.setPValue(zTest.pValue);
                sg.setSignificant(zTest.pValue < 0.05);
                sg.setConfidenceInterval(String.format("[%.2f, %.2f]", zTest.ciLower, zTest.ciUpper));
                sg.setInterpretation(interpretResult(zTest.pValue, sg.getLift()));
            }
        }

        response.setSubgroups(new ArrayList<>(subgroups.values()));

        // 总体效果
        SubgroupAnalysisResponse.SubgroupResult overall = computeOverall(subgroups);
        if (overall != null) {
            response.setOverallLift(overall.getLift());
            response.setOverallPValue(String.valueOf(overall.getPValue()));
        }

        return response;
    }

    private Map<String, SubgroupAnalysisResponse.SubgroupResult> querySubgroupStats(
            String expId, String dimension, LocalDate startDate, LocalDate endDate,
            String controlVariant, String treatmentVariant) {

        Map<String, SubgroupAnalysisResponse.SubgroupResult> results = new HashMap<>();

        // 查询各维度的用户数和转化数
        String sql = """
            SELECT 
                JSONExtractString(properties, ?) as subgroup,
                variant,
                sum(unique_users) as total_users,
                sum(conversions) as total_conversions
            FROM victor.experiment_metrics
            WHERE exp_id = ?
              AND metric_date >= ?
              AND metric_date <= ?
              AND variant IN (?, ?)
            GROUP BY subgroup, variant
            ORDER BY subgroup, variant
            """;

        try (Connection conn = clickHouseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, dimension);
            ps.setString(2, expId);
            ps.setDate(3, Date.valueOf(startDate));
            ps.setDate(4, Date.valueOf(endDate));
            ps.setString(5, controlVariant);
            ps.setString(6, treatmentVariant);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String subgroup = rs.getString("subgroup");
                    if (subgroup == null || subgroup.isEmpty() || subgroup.equals("null")) {
                        subgroup = "Unknown";
                    }

                    String variant = rs.getString("variant");
                    long users = rs.getLong("total_users");
                    long conversions = rs.getLong("total_conversions");
                    double conversionRate = users > 0 ? (double) conversions / users : 0;

                    results.computeIfAbsent(subgroup, k -> {
                        SubgroupAnalysisResponse.SubgroupResult sg = new SubgroupAnalysisResponse.SubgroupResult();
                        sg.setGroupValue(k);
                        return sg;
                    });

                    SubgroupAnalysisResponse.SubgroupResult sg = results.get(subgroup);
                    if (controlVariant.equals(variant)) {
                        sg.setControlUsers((int) users);
                        sg.setControlConversionRate(conversionRate);
                    } else if (treatmentVariant.equals(variant)) {
                        sg.setTreatmentUsers((int) users);
                        sg.setTreatmentConversionRate(conversionRate);
                    }
                    sg.setTotalUsers(sg.getControlUsers() + sg.getTreatmentUsers());
                }
            }
        } catch (SQLException e) {
            log.error("Failed to query subgroup stats for expId={}, dimension={}", expId, dimension, e);
        }

        // 计算 Lift
        for (SubgroupAnalysisResponse.SubgroupResult sg : results.values()) {
            if (sg.getControlConversionRate() > 0) {
                double lift = (sg.getTreatmentConversionRate() - sg.getControlConversionRate()) 
                    / sg.getControlConversionRate() * 100;
                sg.setLift(String.format("%+.1f%%", lift));
            }
        }

        return results;
    }

    private ZTestResult runZTest(long controlConversions, int controlTotal, 
                                   long treatmentConversions, int treatmentTotal) {
        double p1 = (double) controlConversions / controlTotal;
        double p2 = (double) treatmentConversions / treatmentTotal;
        double pPool = (double) (controlConversions + treatmentConversions) / (controlTotal + treatmentTotal);

        double se = Math.sqrt(pPool * (1 - pPool) * (1.0 / controlTotal + 1.0 / treatmentTotal));
        double z = (p2 - p1) / se;
        double pValue = 2 * (1 - normalCDF(Math.abs(z))); // 双侧检验

        // 95% CI for lift
        double seDiff = Math.sqrt(p1 * (1 - p1) / controlTotal + p2 * (1 - p2) / treatmentTotal);
        double lift = p2 - p1;
        double ciLower = p1 > 0 ? (lift - 1.96 * seDiff) / p1 * 100 : 0;
        double ciUpper = p1 > 0 ? (lift + 1.96 * seDiff) / p1 * 100 : 0;

        ZTestResult result = new ZTestResult();
        result.pValue = Math.round(pValue * 10000) / 10000.0;
        result.ciLower = Math.round(ciLower * 100) / 100.0;
        result.ciUpper = Math.round(ciUpper * 100) / 100.0;
        return result;
    }

    private String interpretResult(double pValue, String lift) {
        if (pValue < 0.01) {
            return "极显著";
        } else if (pValue < 0.05) {
            return "显著";
        } else if (pValue < 0.1) {
            return "边缘显著";
        } else {
            return "不显著";
        }
    }

    private SubgroupAnalysisResponse.SubgroupResult computeOverall(
            Map<String, SubgroupAnalysisResponse.SubgroupResult> subgroups) {
        int totalControlUsers = 0;
        int totalTreatmentUsers = 0;
        double weightedControlConv = 0;
        double weightedTreatmentConv = 0;

        for (SubgroupAnalysisResponse.SubgroupResult sg : subgroups.values()) {
            totalControlUsers += sg.getControlUsers();
            totalTreatmentUsers += sg.getTreatmentUsers();
            weightedControlConv += sg.getControlConversionRate() * sg.getControlUsers();
            weightedTreatmentConv += sg.getTreatmentConversionRate() * sg.getTreatmentUsers();
        }

        if (totalControlUsers == 0 || totalTreatmentUsers == 0) {
            return null;
        }

        SubgroupAnalysisResponse.SubgroupResult overall = new SubgroupAnalysisResponse.SubgroupResult();
        overall.setGroupValue("Overall");
        overall.setControlUsers(totalControlUsers);
        overall.setTreatmentUsers(totalTreatmentUsers);
        overall.setTotalUsers(totalControlUsers + totalTreatmentUsers);
        overall.setControlConversionRate(weightedControlConv / totalControlUsers);
        overall.setTreatmentConversionRate(weightedTreatmentConv / totalTreatmentUsers);

        if (overall.getControlConversionRate() > 0) {
            double lift = (overall.getTreatmentConversionRate() - overall.getControlConversionRate())
                / overall.getControlConversionRate() * 100;
            overall.setLift(String.format("%+.1f%%", lift));
        }

        return overall;
    }

    private double normalCDF(double x) {
        double a1 = 0.254829592;
        double a2 = -0.284496736;
        double a3 = 1.421413741;
        double a4 = -1.453152027;
        double a5 = 1.061405429;
        double p = 0.3275911;

        int sign = x < 0 ? -1 : 1;
        x = Math.abs(x) / Math.sqrt(2);

        double t = 1.0 / (1.0 + p * x);
        double y = 1.0 - (((((a5 * t + a4) * t) + a3) * t + a2) * t + a1) * t * Math.exp(-x * x);

        return 0.5 * (1.0 + sign * y);
    }

    private static class ZTestResult {
        double pValue;
        double ciLower;
        double ciUpper;
    }
}
