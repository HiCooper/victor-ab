package com.gateflow.victor.service.analysis;

import com.gateflow.victor.domain.dto.BayesianAnalysisRequest;
import com.gateflow.victor.domain.dto.BayesianAnalysisResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Random;

/**
 * 贝叶斯分析服务
 * 使用Beta-Bernoulli共轭模型进行转化率比较
 */
@Slf4j
@Service
public class BayesianAnalysisService {

    private final Random random = new Random();

    /**
     * 执行贝叶斯分析
     * 使用蒙特卡洛模拟计算后验分布
     */
    public BayesianAnalysisResponse analyze(BayesianAnalysisRequest request) {
        int controlSamples = request.getControlSamples();
        int controlConversions = request.getControlConversions();
        int treatmentSamples = request.getTreatmentSamples();
        int treatmentConversions = request.getTreatmentConversions();

        // Beta-Bernoulli共轭: Beta(alpha, beta) 先验，其中 alpha=1, beta=1 (均匀先验)
        // 后验: Beta(alpha + conversions, beta + (samples - conversions))
        double controlAlpha = 1.0 + controlConversions;
        double controlBeta = 1.0 + (controlSamples - controlConversions);
        double treatmentAlpha = 1.0 + treatmentConversions;
        double treatmentBeta = 1.0 + (treatmentSamples - treatmentConversions);

        // 蒙特卡洛模拟 (100000次)
        int simulations = 100000;
        double[] controlSamplesMcmc = sampleBeta(controlAlpha, controlBeta, simulations);
        double[] treatmentSamplesMcmc = sampleBeta(treatmentAlpha, treatmentBeta, simulations);

        // 计算关键指标
        double probabilityBetter = calculateProbabilityBetter(treatmentSamplesMcmc, controlSamplesMcmc);
        double expectedLoss = calculateExpectedLoss(treatmentSamplesMcmc, controlSamplesMcmc);
        double lift = calculateLift(treatmentSamplesMcmc, controlSamplesMcmc);
        double[] liftCi = calculateCredibleInterval(treatmentSamplesMcmc, controlSamplesMcmc);

        // 建议
        String recommendation = getRecommendation(probabilityBetter, expectedLoss, request.getLossThreshold());
        boolean canStop = "STOP_BEST".equals(recommendation) || "STOP_REJECT".equals(recommendation);

        BayesianAnalysisResponse response = new BayesianAnalysisResponse();
        response.setLift(lift);
        response.setLiftCiLower(liftCi[0]);
        response.setLiftCiUpper(liftCi[1]);
        response.setProbabilityBetter(probabilityBetter);
        response.setExpectedLoss(expectedLoss);
        response.setRequiredSamples(estimateRequiredSamples(request));
        response.setCanStop(canStop);
        response.setRecommendation(recommendation);
        response.setSummary(generateSummary(response, request));

        return response;
    }

    /**
     * 从Beta分布采样 (使用Box-Muller变换)
     */
    private double[] sampleBeta(double alpha, double beta, int n) {
        double[] samples = new double[n];
        for (int i = 0; i < n; i++) {
            // Beta分布 = Gamma(alpha,1) / (Gamma(alpha,1) + Gamma(beta,1))
            double x = gammaSample(alpha, 1.0);
            double y = gammaSample(beta, 1.0);
            samples[i] = x / (x + y);
        }
        return samples;
    }

    /**
     * Gamma分布采样 (使用Marsaglia和Tang的近似)
     */
    private double gammaSample(double shape, double scale) {
        if (shape < 1) {
            return gammaSample(1 + shape, scale) * Math.pow(random.nextDouble(), 1.0 / shape);
        }
        double d = shape - 1.0 / 3.0;
        double c = 1.0 / Math.sqrt(9.0 * d);
        while (true) {
            double x, v;
            do {
                x = normalSample();
                v = 1.0 + c * x;
            } while (v <= 0);
            v = v * v * v;
            double u = random.nextDouble();
            if (u < 1.0 - 0.0331 * (x * x) * (x * x)) {
                return d * v * scale;
            }
            if (Math.log(u) < 0.5 * x * x + d * (1.0 - v + Math.log(v))) {
                return d * v * scale;
            }
        }
    }

    /**
     * 标准正态分布采样
     */
    private double normalSample() {
        double u1 = random.nextDouble();
        double u2 = random.nextDouble();
        return Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2);
    }

    /**
     * 计算实验组更好于控制组的概率
     */
    private double calculateProbabilityBetter(double[] treatment, double[] control) {
        int count = 0;
        for (int i = 0; i < treatment.length; i++) {
            if (treatment[i] > control[i]) {
                count++;
            }
        }
        return (double) count / treatment.length;
    }

    /**
     * 计算期望损失 (选择实验组时，控制组更好的平均比例)
     */
    private double calculateExpectedLoss(double[] treatment, double[] control) {
        double loss = 0;
        for (int i = 0; i < treatment.length; i++) {
            if (control[i] > treatment[i]) {
                loss += control[i] - treatment[i];
            }
        }
        return loss / treatment.length;
    }

    /**
     * 计算平均Lift
     */
    private double calculateLift(double[] treatment, double[] control) {
        double sumLift = 0;
        int count = 0;
        for (int i = 0; i < treatment.length; i++) {
            if (control[i] > 0) {
                sumLift += (treatment[i] - control[i]) / control[i];
                count++;
            }
        }
        return count > 0 ? sumLift / count : 0;
    }

    /**
     * 计算Lift的可信区间
     */
    private double[] calculateCredibleInterval(double[] treatment, double[] control) {
        double[] lifts = new double[treatment.length];
        int validCount = 0;
        for (int i = 0; i < treatment.length; i++) {
            if (control[i] > 0) {
                lifts[validCount++] = (treatment[i] - control[i]) / control[i];
            }
        }
        // 排序并取2.5%和97.5%分位数
        java.util.Arrays.sort(lifts, 0, validCount);
        double lower = validCount > 0 ? lifts[(int) (validCount * 0.025)] : 0;
        double upper = validCount > 0 ? lifts[(int) (validCount * 0.975)] : 0;
        return new double[]{lower, upper};
    }

    /**
     * 获取建议
     */
    private String getRecommendation(double probBetter, double expectedLoss, double threshold) {
        if (probBetter > 0.95 && expectedLoss < threshold) {
            return "STOP_BEST"; // 实验组显著更好，停止并采纳
        }
        if (probBetter < 0.05 && expectedLoss < threshold) {
            return "STOP_REJECT"; // 实验组显著更差，停止并拒绝
        }
        return "CONTINUE"; // 继续实验
    }

    /**
     * 估算需要的样本量
     */
    private int estimateRequiredSamples(BayesianAnalysisRequest request) {
        // 简化估算: 基于当前转化率差异
        double currentDiff = Math.abs(
                (double) request.getTreatmentConversions() / request.getTreatmentSamples() -
                        (double) request.getControlConversions() / request.getControlSamples()
        );
        if (currentDiff < 0.001) {
            return request.getTreatmentSamples() * 3; // 差异太小，需要更多样本
        }
        // 假设需要每组约1000个转化
        return Math.max(request.getTreatmentSamples(), request.getControlSamples()) * 2;
    }

    /**
     * 生成分析摘要
     */
    private String generateSummary(BayesianAnalysisResponse response, BayesianAnalysisRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("基于%d次蒙特卡洛模拟，", 100000));
        sb.append(String.format("实验组优于控制组的概率为%.1f%%。", response.getProbabilityBetter() * 100));
        if (response.isCanStop()) {
            sb.append("已满足停止条件，可以做出决策。");
        } else {
            sb.append("建议继续收集数据以获得更可靠结论。");
        }
        return sb.toString();
    }
}