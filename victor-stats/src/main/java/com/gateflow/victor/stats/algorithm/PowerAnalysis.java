package com.gateflow.victor.stats.algorithm;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Power Analysis - 功效分析算法
 *
 * 用于计算A/B实验所需的样本量和统计功效
 *
 * 使用两比例z检验的公式：
 * n = (Zα/2 + Zβ)² * (p1(1-p1) + p2(1-p2)) / (p1-p2)²
 */
@Slf4j
public class PowerAnalysis {

    /**
     * 默认显著性水平
     */
    public static final double DEFAULT_ALPHA = 0.05;

    /**
     * 默认统计功效
     */
    public static final double DEFAULT_POWER = 0.80;

    /**
     * 标准正态分布临界值 (单侧)
     */
    private static final double Z_ALPHA_005 = 1.645;   // α=0.05 单侧
    private static final double Z_ALPHA_001 = 2.326;  // α=0.01 单侧
    private static final double Z_BETA_080 = 0.842;   // 80% 功效
    private static final double Z_BETA_090 = 1.282;   // 90% 功效
    private static final double Z_BETA_095 = 1.645;  // 95% 功效

    /**
     * 计算两比例z检验所需的样本量
     *
     * @param p1 基线转化率
     * @param mde 最小可检测效应（相对值，如0.10表示10%相对提升）
     * @param power 统计功效 (默认0.80)
     * @param alpha 显著性水平 (默认0.05)
     * @return 每组所需样本量
     */
    public static long calculateSampleSize(double p1, double mde, double power, double alpha) {
        if (p1 <= 0 || p1 >= 1) {
            throw new IllegalArgumentException("基线转化率必须在(0,1)范围内");
        }
        if (mde <= 0 || mde > 1) {
            throw new IllegalArgumentException("MDE必须在(0,1]范围内");
        }
        if (power <= 0 || power >= 1) {
            throw new IllegalArgumentException("功效必须在(0,1)范围内");
        }
        if (alpha <= 0 || alpha >= 1) {
            throw new IllegalArgumentException("显著性水平必须在(0,1)范围内");
        }

        // 计算治疗组期望转化率
        double p2 = p1 * (1 + mde);

        // 确保p2在有效范围内
        if (p2 >= 1) {
            p2 = 0.999;
        }

        // 获取Z值
        double zAlpha = getZAlpha(alpha);
        double zBeta = getZBeta(power);

        // 计算合并方差
        double pBar = (p1 + p2) / 2;
        double varSum = p1 * (1 - p1) + p2 * (1 - p2);

        // 样本量公式
        double diff = p2 - p1;
        double n = Math.pow(zAlpha + zBeta, 2) * varSum / (diff * diff);

        return (long) Math.ceil(n);
    }

    /**
     * 计算给定样本量下的统计功效
     *
     * @param p1 基线转化率
     * @param p2 期望转化率
     * @param n 每组样本量
     * @param alpha 显著性水平
     * @return 统计功效 (0-1)
     */
    public static double calculatePower(double p1, double p2, long n, double alpha) {
        if (p1 <= 0 || p1 >= 1 || p2 <= 0 || p2 >= 1) {
            throw new IllegalArgumentException("转化率必须在(0,1)范围内");
        }
        if (n <= 0) {
            throw new IllegalArgumentException("样本量必须大于0");
        }

        double zAlpha = getZAlpha(alpha);
        double diff = p2 - p1;
        double varSum = p1 * (1 - p1) + p2 * (1 - p2);

        // 计算z值
        double z = Math.abs(diff) * Math.sqrt(n) / Math.sqrt(varSum);

        // 转换为标准正态分布
        double zBeta = z - zAlpha;

        // 计算功效 (标准正态分布的累积分布)
        return normalCDF(zBeta);
    }

    /**
     * 计算功效分析结果
     */
    public static PowerAnalysisResult analyze(
            double baseConversionRate,
            double mde,
            double power,
            double alpha,
            long dailyTraffic
    ) {
        long sampleSize = calculateSampleSize(baseConversionRate, mde, power, alpha);
        long totalSample = sampleSize * 2; // 两组

        // 计算预计实验天数（假设50%流量到实验组）
        long effectiveDaily = dailyTraffic / 2;
        long estimatedDays = effectiveDaily > 0
            ? (long) Math.ceil((double) sampleSize / effectiveDaily)
            : 0;

        // 计算MDE敏感度（不同样本量下的可检测效应）
        List<SensitivityPoint> sensitivity = calculateSensitivity(
            baseConversionRate, sampleSize, alpha
        );

        PowerAnalysisResult result = new PowerAnalysisResult();
        result.setRequiredSampleSize(sampleSize);
        result.setTotalSampleSize(totalSample);
        result.setEstimatedDays(estimatedDays);
        result.setDailyTraffic(dailyTraffic);
        result.setBaseConversionRate(baseConversionRate);
        result.setMde(mde);
        result.setPower(power);
        result.setAlpha(alpha);
        result.setConfidence(power);
        result.setSensitivity(sensitivity);

        // 置信区间宽度(相对)
        double ciWidth = 1.96 * Math.sqrt(
            baseConversionRate * (1 - baseConversionRate) / sampleSize
        ) / baseConversionRate;
        result.setConfidenceIntervalWidth(ciWidth * 2);

        return result;
    }

    /**
     * 计算敏感度分析数据点
     */
    public static List<SensitivityPoint> calculateSensitivity(
            double baseRate,
            long sampleSize,
            double alpha
    ) {
        List<SensitivityPoint> points = new ArrayList<>();

        // 测试不同的MDE值
        double[] mdes = {0.02, 0.05, 0.08, 0.10, 0.15, 0.20, 0.25, 0.30};

        for (double mde : mdes) {
            double p2 = baseRate * (1 + mde);
            if (p2 >= 1) p2 = 0.999;

            double power = calculatePower(baseRate, p2, sampleSize, alpha);

            SensitivityPoint point = new SensitivityPoint();
            point.setMde(mde);
            point.setPower(power);
            point.setDetectable(power >= 0.80);
            points.add(point);
        }

        return points;
    }

    /**
     * 获取ZAlpha值
     */
    private static double getZAlpha(double alpha) {
        if (alpha <= 0.10) return Z_ALPHA_005;
        return 1.96; // 默认
    }

    /**
     * 获取ZBeta值
     */
    private static double getZBeta(double power) {
        if (power >= 0.95) return Z_BETA_095;
        if (power >= 0.90) return Z_BETA_090;
        return Z_BETA_080;
    }

    /**
     * 标准正态分布累积分布函数
     */
    private static double normalCDF(double z) {
        // 使用近似公式
        return 1.0 / (1.0 + Math.exp(-1.702 * z));
    }

    @Data
    public static class PowerAnalysisResult {
        private long requiredSampleSize;       // 每组所需样本量
        private long totalSampleSize;        // 总样本量
        private long estimatedDays;          // 预计天数
        private long dailyTraffic;          // 每日流量
        private double baseConversionRate; // 基线转化率
        private double mde;               // MDE
        private double power;              // 功效
        private double alpha;            // 显著性
        private double confidence;        // 置信度
        private double confidenceIntervalWidth;
        private List<SensitivityPoint> sensitivity;
    }

    @Data
    public static class SensitivityPoint {
        private double mde;
        private double power;
        private boolean detectable;
    }
}