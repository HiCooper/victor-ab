package com.gateflow.victor.service.analysis;

import com.gateflow.victor.domain.dto.PowerAnalysisRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * 实验功效计算服务 - 预估样本量和运行时间
 */
@Service
@Slf4j
public class PowerAnalysisService {

    private static final double Z_ALPHA_05 = 1.96;    // 双侧 alpha=0.05
    private static final double Z_ALPHA_10 = 1.645;   // 双侧 alpha=0.10
    private static final double Z_BETA_20 = 0.84;     // power=0.80 (beta=0.20)
    private static final double Z_BETA_10 = 1.28;     // power=0.90 (beta=0.10)

    /**
     * 计算每组所需样本量
     *
     * @param baselineConversion 历史基准转化率 (0-1)
     * @param mde                最小可检测效应量 (相对值, 如 0.05 表示 5% 相对提升)
     * @param alpha              显著性水平
     * @param power              统计功效
     * @return 每组所需样本量
     */
    public long calculateSampleSize(double baselineConversion, double mde, double alpha, double power) {
        double zAlpha = getZAlpha(alpha);
        double zBeta = getZBeta(power);

        double p1 = baselineConversion;
        double p2 = baselineConversion * (1 + mde);
        double pBar = (p1 + p2) / 2;

        // 标准样本量公式 (两比例检验)
        double numerator = Math.pow(zAlpha * Math.sqrt(2 * pBar * (1 - pBar)) + zBeta * Math.sqrt(p1 * (1 - p1) + p2 * (1 - p2)), 2);
        double denominator = Math.pow(p2 - p1, 2);

        long sampleSizePerGroup = (long) Math.ceil(numerator / denominator);

        log.info("Power analysis: baseline={}, mde={}, alpha={}, power={}, sampleSizePerGroup={}",
                baselineConversion, mde, alpha, power, sampleSizePerGroup);

        return sampleSizePerGroup;
    }

    /**
     * 计算考虑流量分配后的总样本量
     *
     * @param baselineConversion    基准转化率
     * @param mde                   最小可检测效应量
     * @param alpha                 显著性水平
     * @param power                 统计功效
     * @param controlTrafficPercent 对照组流量占比 (10-90)
     * @return 实验所需总样本量
     */
    public long calculateTotalSampleSize(double baselineConversion, double mde, double alpha, double power, int controlTrafficPercent) {
        long sampleSizePerGroup = calculateSampleSize(baselineConversion, mde, alpha, power);

        // 对照组样本量
        long controlSampleSize = sampleSizePerGroup;
        // 实验组样本量 (根据流量比例调整)
        long treatmentSampleSize = (long) Math.ceil(sampleSizePerGroup * (100.0 - controlTrafficPercent) / controlTrafficPercent);

        return controlSampleSize + treatmentSampleSize;
    }

    /**
     * 计算当前样本量下的统计功效
     *
     * @param baselineConversion 基准转化率
     * @param mde                效应量
     * @param alpha              显著性水平
     * @param sampleSizePerGroup 每组样本量
     * @return 统计功效 (0-1)
     */
    public double calculateCurrentPower(double baselineConversion, double mde, double alpha, long sampleSizePerGroup) {
        double p1 = baselineConversion;
        double p2 = baselineConversion * (1 + mde);
        double pBar = (p1 + p2) / 2;

        double seNull = Math.sqrt(2 * pBar * (1 - pBar) / sampleSizePerGroup);
        double seAlt = Math.sqrt((p1 * (1 - p1) + p2 * (1 - p2)) / sampleSizePerGroup);

        double zAlpha = getZAlpha(alpha);
        double criticalValue = zAlpha * seNull;
        double effectSize = Math.abs(p2 - p1);

        // 计算功效
        double zBeta = (effectSize - criticalValue) / seAlt;
        double power = normalCDF(zBeta);

        return Math.max(0, Math.min(1, power));
    }

    /**
     * 预估实验完成时间
     *
     * @param sampleSizePerGroup    每组所需样本量
     * @param controlTrafficPercent 对照组流量占比
     * @param dailyUsers            日均UV
     * @return 预估实验天数
     */
    public int estimateExperimentDuration(long sampleSizePerGroup, int controlTrafficPercent, long dailyUsers) {
        if (dailyUsers <= 0) {
            return -1; // 无法预估
        }

        // 每日进入对照组的用户数
        long dailyControlUsers = (long) (dailyUsers * controlTrafficPercent / 100.0);

        // 所需天数
        int days = (int) Math.ceil((double) sampleSizePerGroup / dailyControlUsers);

        // 至少运行 7 天以覆盖完整周周期
        return Math.max(days, 7);
    }

    /**
     * 执行完整的功效分析
     */
    public Map<String, Object> runPowerAnalysis(PowerAnalysisRequest request) {
        double alpha = request.getAlpha() != null ? request.getAlpha() : 0.05;
        double power = request.getPower() != null ? request.getPower() : 0.80;
        int controlPercent = request.getControlTrafficPercent() != null ? request.getControlTrafficPercent() : 50;

        long sampleSizePerGroup = calculateSampleSize(
                request.getBaselineConversion(),
                request.getMde(),
                alpha,
                power
        );

        long totalSampleSize = calculateTotalSampleSize(
                request.getBaselineConversion(),
                request.getMde(),
                alpha,
                power,
                controlPercent
        );

        int estimatedDays = -1;
        if (request.getDailyUsers() != null && request.getDailyUsers() > 0) {
            estimatedDays = estimateExperimentDuration(sampleSizePerGroup, controlPercent, request.getDailyUsers());
        }

        double currentPower = calculateCurrentPower(
                request.getBaselineConversion(),
                request.getMde(),
                alpha,
                sampleSizePerGroup
        );

        Map<String, Object> result = new HashMap<>();
        result.put("sampleSizePerGroup", sampleSizePerGroup);
        result.put("totalSampleSize", totalSampleSize);
        result.put("controlTrafficPercent", controlPercent);
        result.put("treatmentTrafficPercent", 100 - controlPercent);
        result.put("estimatedDays", estimatedDays);
        result.put("estimatedEndDate", estimatedDays > 0 ? LocalDate.now().plusDays(estimatedDays).toString() : null);
        result.put("currentPower", Math.round(currentPower * 10000) / 100.0);
        result.put("alpha", alpha);
        result.put("targetPower", power);
        result.put("baselineConversion", request.getBaselineConversion());
        result.put("mde", request.getMde());
        result.put("recommendation", generateRecommendation(sampleSizePerGroup, estimatedDays, currentPower));
        return result;
    }

    private String generateRecommendation(long sampleSize, int days, double power) {
        if (days > 0 && days > 60) {
            return "实验周期过长 (" + days + " 天)，建议增大 MDE 或提高日均UV";
        } else if (sampleSize > 1000000) {
            return "所需样本量过大，建议检查 baselineConversion 和 MDE 设置";
        } else if (power < 0.7) {
            return "统计功效偏低，建议增大样本量或调整 MDE";
        } else {
            return "实验配置合理，可以开始实验";
        }
    }

    private double getZAlpha(double alpha) {
        return alpha <= 0.01 ? 2.576 : (alpha <= 0.05 ? Z_ALPHA_05 : Z_ALPHA_10);
    }

    private double getZBeta(double power) {
        return power >= 0.90 ? Z_BETA_10 : Z_BETA_20;
    }

    private static final org.apache.commons.math3.distribution.NormalDistribution STANDARD_NORMAL =
            new org.apache.commons.math3.distribution.NormalDistribution(0, 1);

    private double normalCDF(double x) {
        return STANDARD_NORMAL.cumulativeProbability(x);
    }
}
