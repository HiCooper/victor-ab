package com.gateflow.victor.stats.algorithm;

/**
 * A/A Validation Algorithm
 * <p>
 * Analyzes historical A/A tests to validate that the experiment platform
 * has no systematic bias. In a properly functioning platform, A/A tests
 * (where both buckets are identical) should show significant differences
 * only 5% of the time (at alpha=0.05).
 * <p>
 * If the false positive rate is significantly higher than 5%, it indicates
 * platform bias or bugs in traffic allocation.
 */
public class AaValidation {

    /**
     * Validate platform bias using historical A/A test results
     *
     * @param aaTestPValues P-values from historical A/A tests
     * @param alpha         Significance level (default 0.05)
     * @return Validation result
     */
    public static AaResult validate(double[] aaTestPValues, double alpha) {
        int totalTests = aaTestPValues.length;
        int falsePositives = 0;

        // Count false positives (A/A tests that showed significant difference)
        for (double pValue : aaTestPValues) {
            if (pValue < alpha) {
                falsePositives++;
            }
        }

        double falsePositiveRate = totalTests > 0
                ? (double) falsePositives / totalTests
                : 0;

        // Acceptable if FPR is within 2x the expected rate
        boolean isAcceptable = falsePositiveRate <= (alpha * 2);

        String recommendation;
        if (totalTests == 0) {
            recommendation = "需要更多 A/A 测试数据来验证平台可靠性";
        } else if (isAcceptable) {
            recommendation = String.format(
                    "平台表现正常，假阳性率 %.1f%% 在可接受范围内",
                    falsePositiveRate * 100
            );
        } else {
            recommendation = String.format(
                    "警告：假阳性率 %.1f%% 过高，可能存在平台偏差或分流问题",
                    falsePositiveRate * 100
            );
        }

        return new AaResult(totalTests, falsePositives, falsePositiveRate,
                isAcceptable, recommendation);
    }

    /**
     * Simplified validation with default alpha=0.05
     */
    public static AaResult validate(double[] aaTestPValues) {
        return validate(aaTestPValues, 0.05);
    }

    /**
     * Result of A/A validation
     */
    public static class AaResult {
        private int totalTests;
        private int falsePositives;
        private double falsePositiveRate;
        private boolean isAcceptable;
        private String recommendation;

        public AaResult(int totalTests, int falsePositives, double falsePositiveRate,
                        boolean isAcceptable, String recommendation) {
            this.totalTests = totalTests;
            this.falsePositives = falsePositives;
            this.falsePositiveRate = falsePositiveRate;
            this.isAcceptable = isAcceptable;
            this.recommendation = recommendation;
        }

        public int getTotalTests() {
            return totalTests;
        }

        public int getFalsePositives() {
            return falsePositives;
        }

        public double getFalsePositiveRate() {
            return falsePositiveRate;
        }

        public boolean isAcceptable() {
            return isAcceptable;
        }

        public String getRecommendation() {
            return recommendation;
        }
    }
}
