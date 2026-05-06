package com.gateflow.victor.common.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 实验ID生成器测试
 */
class ExperimentIdGeneratorTest {

    @Test
    void testGenerate_DefaultLength() {
        String id = ExperimentIdGenerator.generate();
        System.out.println(id);
        assertNotNull(id);
        assertEquals(7, id.length(), "ID长度必须为7位");
        assertTrue(id.matches("\\d{7}"), "ID必须全部为数字");
    }

    @Test
    void testGenerate_WithSpecificDate() {
        // 测试特定日期：2026年5月6日
        LocalDate testDate = LocalDate.of(2026, 5, 6);
        String id = ExperimentIdGenerator.generate(testDate);
        
        assertNotNull(id);
        assertEquals(7, id.length(), "ID长度必须为7位");
        assertTrue(id.matches("\\d{7}"), "ID必须全部为数字");
        
        // 验证日期部分：年份最后一位(6) + 月份(05) = "605"
        String datePart = id.substring(0, 3);
        assertEquals("605", datePart, "日期部分应为605");
    }

    @Test
    void testGenerate_DifferentDates() {
        // 测试不同日期的ID生成
        LocalDate date1 = LocalDate.of(2026, 1, 1);
        LocalDate date2 = LocalDate.of(2026, 12, 31);
        LocalDate date3 = LocalDate.of(2025, 6, 15);
        
        String id1 = ExperimentIdGenerator.generate(date1);
        String id2 = ExperimentIdGenerator.generate(date2);
        String id3 = ExperimentIdGenerator.generate(date3);
        
        // 验证每个ID的长度和格式
        assertEquals(7, id1.length());
        assertEquals(7, id2.length());
        assertEquals(7, id3.length());
        
        assertTrue(id1.matches("\\d{7}"));
        assertTrue(id2.matches("\\d{7}"));
        assertTrue(id3.matches("\\d{7}"));
        
        // 验证日期部分
        assertEquals("601", id1.substring(0, 3), "2026-01-01的日期部分应为601");
        assertEquals("612", id2.substring(0, 3), "2026-12-31的日期部分应为612");
        assertEquals("506", id3.substring(0, 3), "2025-06-15的日期部分应为506");
    }

    @Test
    void testGenerate_Uniqueness() {
        // 测试生成ID的唯一性（在同一毫秒内生成多个ID）
        Set<String> ids = new HashSet<>();
        int generationCount = 50;
        
        for (int i = 0; i < generationCount; i++) {
            String id = ExperimentIdGenerator.generate();
            System.out.println(id);
            ids.add(id);
        }
        
        // 使用ThreadLocalRandom生成随机数，大部分ID应该是唯一的
        // 允许极少量重复（生日悖论）
        assertTrue(ids.size() >= generationCount * 0.999,
            String.format("生成的%d个ID中至少有90%%应该是唯一的，实际唯一ID数: %d", 
                generationCount, ids.size()));
    }

    @Test
    void testGenerate_FormatValidation() {
        // 验证ID格式：前3位是日期部分，后4位是随机数
        String id = ExperimentIdGenerator.generate();
        
        // 提取日期部分和随机部分
        String datePart = id.substring(0, 3);
        String randomPart = id.substring(3);
        
        // 验证日期部分是数字
        assertTrue(datePart.matches("\\d{3}"), "日期部分必须是3位数字");
        
        // 验证随机部分是4位数字
        assertTrue(randomPart.matches("\\d{4}"), "随机部分必须是4位数字");
        
        // 验证整个ID是7位数字
        assertTrue(id.matches("\\d{7}"), "整个ID必须是7位数字");
    }

    @Test
    void testGenerate_MonthBoundary() {
        // 测试月份边界情况
        LocalDate janFirst = LocalDate.of(2026, 1, 1);
        LocalDate decLast = LocalDate.of(2026, 12, 31);
        
        String janId = ExperimentIdGenerator.generate(janFirst);
        String decId = ExperimentIdGenerator.generate(decLast);
        
        // 1月的ID应以"601"开头
        assertEquals("601", janId.substring(0, 3));
        
        // 12月的ID应以"612"开头
        assertEquals("612", decId.substring(0, 3));
    }

    @Test
    void testGenerate_YearChange() {
        // 测试年份变化的情况
        LocalDate year2025 = LocalDate.of(2025, 6, 15);
        LocalDate year2026 = LocalDate.of(2026, 6, 15);
        
        String id2025 = ExperimentIdGenerator.generate(year2025);
        String id2026 = ExperimentIdGenerator.generate(year2026);
        
        // 2025年的ID应以"506"开头（年份最后一位5 + 月份06）
        assertEquals("506", id2025.substring(0, 3));
        
        // 2026年的ID应以"606"开头（年份最后一位6 + 月份06）
        assertEquals("606", id2026.substring(0, 3));
    }

    @RepeatedTest(10)
    void testGenerate_MultipleGenerations() {
        // 重复测试多次生成的一致性
        String id = ExperimentIdGenerator.generate();
        
        assertNotNull(id);
        assertEquals(7, id.length());
        assertTrue(id.matches("\\d{7}"));
    }
}
