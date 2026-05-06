package com.gateflow.victor.stats.algorithm;

import com.gateflow.victor.stats.model.SampleStatistics;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CUPEDTest {
    @Test
    void shouldReduceVariance() {
        CUPED cuped = new CUPED();
        List<Double> y = Arrays.asList(10.0, 11.0, 12.0, 9.0, 8.0);
        List<Double> x = Arrays.asList(9.5, 10.5, 11.5, 8.5, 7.5);
        double meanX = 9.5;
        
        SampleStatistics result = cuped.adjust(y, x, meanX);
        assertTrue(result.getVariance() < 2.5);
        assertEquals(5, result.getN());
    }
}