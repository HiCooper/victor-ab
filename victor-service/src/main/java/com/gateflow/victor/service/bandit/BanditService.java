package com.gateflow.victor.service.bandit;

import com.gateflow.victor.domain.dto.BanditRequest;
import com.gateflow.victor.domain.dto.BanditResponse;
import com.gateflow.victor.domain.entity.Bucket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 多臂老虎机服务
 * 实现自动流量分配优化算法
 */
@Slf4j
@Service
public class BanditService {

    /**
     * Thompson Sampling 算法
     * 使用Beta-Bernoulli共轭进行在线学习
     */
    public BanditResponse thompsonSampling(List<Bucket> variants, Map<Long, int[]> data) {
        Map<String, Double> allocationProbabilities = new HashMap<>();
        Map<String, Double> estimatedRates = new HashMap<>();

        // 对每个variant计算后验Beta分布
        Map<Long, double[]> posteriors = new HashMap<>();
        for (Variant v : variants) {
            int[] stats = data.getOrDefault(v.getId(), new int[]{0, 0});
            int samples = stats[0];
            int conversions = stats[1];

            // Beta(alpha + conversions, beta + (samples - conversions))
            double alpha = 1.0 + conversions;
            double beta = 1.0 + (samples - conversions);
            posteriors.put(v.getId(), new double[]{alpha, beta});

            // 估计转化率
            estimatedRates.put(v.getName(), samples > 0 ? (double) conversions / samples : 0.0);
        }

        // 采样并计算选择概率
        int simulations = 10000;
        Map<Long, Integer> wins = new HashMap<>();
        for (Variant v : variants) {
            wins.put(v.getId(), 0);
        }

        Random random = new Random();
        for (int i = 0; i < simulations; i++) {
            double bestRate = -1;
            Long bestVariant = null;

            for (Variant v : variants) {
                double[] post = posteriors.get(v.getId());
                double sample = sampleBeta(post[0], post[1], random);
                if (sample > bestRate) {
                    bestRate = sample;
                    bestVariant = v.getId();
                }
            }
            if (bestVariant != null) {
                wins.put(bestVariant, wins.get(bestVariant) + 1);
            }
        }

        // 计算分配概率
        for (Variant v : variants) {
            double prob = (double) wins.get(v.getId()) / simulations;
            allocationProbabilities.put(v.getName(), prob);
        }

        // 找到最佳variant
        String bestName = estimatedRates.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(variants.get(0).getName());

        Long bestId = variants.stream()
                .filter(v -> v.getName().equals(bestName))
                .findFirst()
                .map(Variant::getId)
                .orElse(null);

        BanditResponse response = new BanditResponse();
        response.setRecommendedVariantId(bestId);
        response.setRecommendedVariantName(bestName);
        response.setAllocationProbabilities((HashMap<String, Double>) allocationProbabilities);
        response.setEstimatedRates((HashMap<String, Double>) estimatedRates);
        response.setExpectedLift(calculateExpectedLift(estimatedRates));
        response.setAlgorithmInfo("Thompson Sampling: 每次决策从Beta后验分布中采样，选择预期转化率最高的variant");
        response.setReasoning("基于历史数据，使用贝叶斯方法计算各variant的转化率分布，直接给出最优选择");

        return response;
    }

    /**
     * Epsilon-Greedy 算法
     */
    public BanditResponse epsilonGreedy(List<Bucket> variants, Map<Long, int[]> data, double epsilon) {
        Map<String, Double> estimatedRates = new HashMap<>();

        // 计算每个variant的转化率
        for (Variant v : variants) {
            int[] stats = data.getOrDefault(v.getId(), new int[]{0, 0});
            int samples = stats[0];
            int conversions = stats[1];
            estimatedRates.put(v.getName(), samples > 0 ? (double) conversions / samples : 0.0);
        }

        // 找到最佳variant
        String bestName = estimatedRates.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(variants.get(0).getName());

        Long bestId = variants.stream()
                .filter(v -> v.getName().equals(bestName))
                .findFirst()
                .map(Variant::getId)
                .orElse(null);

        // 分配概率: epsilon用于探索，1-epsilon用于利用
        Map<String, Double> allocationProbabilities = new HashMap<>();
        double exploreProb = epsilon / variants.size();
        double exploitProb = 1 - epsilon;

        for (Variant v : variants) {
            if (v.getName().equals(bestName)) {
                allocationProbabilities.put(v.getName(), exploitProb + exploreProb);
            } else {
                allocationProbabilities.put(v.getName(), exploreProb);
            }
        }

        BanditResponse response = new BanditResponse();
        response.setRecommendedVariantId(bestId);
        response.setRecommendedVariantName(bestName);
        response.setAllocationProbabilities((HashMap<String, Double>) allocationProbabilities);
        response.setEstimatedRates((HashMap<String, Double>) estimatedRates);
        response.setExpectedLift(calculateExpectedLift(estimatedRates));
        response.setAlgorithmInfo(String.format("Epsilon-Greedy: %.0f%%时间探索新方案，%.0f%%时间利用已知最佳方案", epsilon * 100, (1 - epsilon) * 100));
        response.setReasoning(String.format("以%.0f%%的概率选择最佳方案，其余概率随机分配给其他方案", exploitProb * 100));

        return response;
    }

    /**
     * UCB (Upper Confidence Bound) 算法
     */
    public BanditResponse ucb(List<Bucket> variants, Map<Long, int[]> data) {
        Map<String, Double> estimatedRates = new HashMap<>();
        Map<String, Double> ucbScores = new HashMap<>();

        int totalSamples = data.values().stream()
                .mapToInt(arr -> arr[0])
                .sum();

        for (Variant v : variants) {
            int[] stats = data.getOrDefault(v.getId(), new int[]{0, 0});
            int samples = stats[0];
            int conversions = stats[1];

            double rate = samples > 0 ? (double) conversions / samples : 0.0;
            estimatedRates.put(v.getName(), rate);

            // UCB公式: rate + sqrt(2*ln(total)/samples)
            double ucb = rate;
            if (samples > 0 && totalSamples > 0) {
                ucb += Math.sqrt(2.0 * Math.log(totalSamples) / samples);
            }
            ucbScores.put(v.getName(), ucb);
        }

        // 选择UCB分数最高的
        String bestName = ucbScores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(variants.get(0).getName());

        Long bestId = variants.stream()
                .filter(v -> v.getName().equals(bestName))
                .findFirst()
                .map(Variant::getId)
                .orElse(null);

        Map<String, Double> allocationProbabilities = new HashMap<>();
        for (Variant v : variants) {
            if (v.getName().equals(bestName)) {
                allocationProbabilities.put(v.getName(), 1.0);
            } else {
                allocationProbabilities.put(v.getName(), 0.0);
            }
        }

        BanditResponse response = new BanditResponse();
        response.setRecommendedVariantId(bestId);
        response.setRecommendedVariantName(bestName);
        response.setAllocationProbabilities((HashMap<String, Double>) allocationProbabilities);
        response.setEstimatedRates((HashMap<String, Double>) estimatedRates);
        response.setExpectedLift(calculateExpectedLift(estimatedRates));
        response.setAlgorithmInfo("UCB: 平衡探索与利用，选择置信上界最高的方案");
        response.setReasoning("UCB算法自动平衡探索新方案和利用已知最佳方案");

        return response;
    }

    /**
     * 根据请求执行多臂老虎机算法
     */
    public BanditResponse optimize(BanditRequest request, List<Bucket> variants, Map<Long, int[]> data) {
        String algorithm = request.getAlgorithm();

        return switch (algorithm) {
            case "THOMPSON_SAMPLING" -> thompsonSampling(variants, data);
            case "UCB" -> ucb(variants, data);
            default -> epsilonGreedy(variants, data, request.getEpsilon());
        };
    }

    /**
     * 从Beta分布采样
     */
    private double sampleBeta(double alpha, double beta, Random random) {
        double x = gammaSample(alpha, 1.0, random);
        double y = gammaSample(beta, 1.0, random);
        return x / (x + y);
    }

    /**
     * Gamma分布采样
     */
    private double gammaSample(double shape, double scale, Random random) {
        if (shape < 1) {
            return gammaSample(1 + shape, scale, random) * Math.pow(random.nextDouble(), 1.0 / shape);
        }
        double d = shape - 1.0 / 3.0;
        double c = 1.0 / Math.sqrt(9.0 * d);
        while (true) {
            double x, v;
            do {
                x = random.nextGaussian();
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
     * 计算预期提升
     */
    private double calculateExpectedLift(Map<String, Double> rates) {
        OptionalDouble max = rates.values().stream().mapToDouble(Double::doubleValue).max();
        OptionalDouble min = rates.values().stream().mapToDouble(Double::doubleValue).min();
        if (max.isPresent() && min.isPresent() && min.getAsDouble() > 0) {
            return (max.getAsDouble() - min.getAsDouble()) / min.getAsDouble();
        }
        return 0;
    }
}