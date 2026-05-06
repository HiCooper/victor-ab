package com.gateflow.victor.stats.algorithm;

import com.gateflow.victor.stats.model.TestResult;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ZTestTest {
    @Test
    void shouldDetectSignificantDifference() {
        ZTest zTest = new ZTest();
        TestResult result = zTest.executeProportion(500, 10000, 600, 10000);
        assertTrue(result.isSignificant());
        assertTrue(result.getPValue() < 0.05);
        assertNotNull(result.getLift());
    }
    
    @Test
    void shouldNotDetectInsignificantDifference() {
        ZTest zTest = new ZTest();
        TestResult result = zTest.executeProportion(500, 10000, 503, 10000);
        assertFalse(result.isSignificant());
        assertTrue(result.getPValue() > 0.05);
    }
}