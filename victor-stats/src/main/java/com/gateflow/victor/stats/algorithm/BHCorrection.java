package com.gateflow.victor.stats.algorithm;

import com.gateflow.victor.stats.model.TestResult;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class BHCorrection {
    private static final double ALPHA = 0.05;

    public List<TestResult> correct(List<TestResult> testResults) {
        int m = testResults.size();
        
        List<TestResult> sorted = testResults.stream()
            .sorted(Comparator.comparingDouble(TestResult::getPValue))
            .collect(Collectors.toList());
        
        int k = -1;
        for (int i = m - 1; i >= 0; i--) {
            double threshold = (i + 1) / (double) m * ALPHA;
            if (sorted.get(i).getPValue() <= threshold) {
                k = i;
                break;
            }
        }
        
        List<TestResult> corrected = new ArrayList<>();
        for (int i = 0; i < m; i++) {
            TestResult original = sorted.get(i);
            double adjustedP = Math.min(original.getPValue() * m / (i + 1), 1.0);
            if (i > 0 && corrected.size() > 0) {
                adjustedP = Math.min(adjustedP, corrected.get(i-1).getAdjustedPValue());
            }
            
            corrected.add(TestResult.builder()
                .testName(original.getTestName())
                .pValue(original.getPValue())
                .adjustedPValue(adjustedP)
                .significant(i <= k)
                .lift(original.getLift())
                .confidenceInterval(original.getConfidenceInterval())
                .message(i <= k ? "BH校正后显著" : "BH校正后不显著")
                .build());
        }
        return corrected;
    }
}