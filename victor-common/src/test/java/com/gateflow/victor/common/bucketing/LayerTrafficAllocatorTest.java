package com.gateflow.victor.common.bucketing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LayerTrafficAllocator 单元测试 —— 验证「层内互斥」的桶段分配与校验。
 */
class LayerTrafficAllocatorTest {

    private static LayerTrafficAllocator.Range r(int start, int end) {
        return new LayerTrafficAllocator.Range(start, end);
    }

    @Test
    @DisplayName("区间重叠判定")
    void overlaps() {
        assertTrue(LayerTrafficAllocator.overlaps(r(0, 4999), r(2000, 6999)));
        assertTrue(LayerTrafficAllocator.overlaps(r(0, 4999), r(4999, 5000))); // 边界相接算重叠
        assertFalse(LayerTrafficAllocator.overlaps(r(0, 4999), r(5000, 9999)));
    }

    @Test
    @DisplayName("非重叠校验：越界与重叠分别抛不同异常")
    void validateNoOverlap() {
        List<LayerTrafficAllocator.Range> occupied = List.of(r(0, 4999));
        assertDoesNotThrow(() -> LayerTrafficAllocator.validateNoOverlap(r(5000, 9999), occupied));
        assertThrows(IllegalStateException.class,
                () -> LayerTrafficAllocator.validateNoOverlap(r(3000, 6000), occupied));
        assertThrows(IllegalArgumentException.class,
                () -> LayerTrafficAllocator.validateNoOverlap(r(8000, 10000), List.of()));
    }

    @Test
    @DisplayName("寻找空闲桶段：空层 / 间隙 / 无空间")
    void findFreeGap() {
        // 空层从 0 开始
        assertEquals(r(0, 2999), LayerTrafficAllocator.findFreeGap(3000, List.of()));
        // 在已占用 [0,4999] 之后分配 3000 → [5000,7999]
        assertEquals(r(5000, 7999), LayerTrafficAllocator.findFreeGap(3000, List.of(r(0, 4999))));
        // 优先填中间足够大的间隙：占用 [2000,2999] 与 [6000,9999]，宽 2000 → [0,1999]
        assertEquals(r(0, 1999),
                LayerTrafficAllocator.findFreeGap(2000, List.of(r(2000, 2999), r(6000, 9999))));
        // 中间间隙 [3000,5999] 容纳 3000 宽
        assertEquals(r(3000, 5999),
                LayerTrafficAllocator.findFreeGap(3000, List.of(r(0, 2999), r(6000, 9999))));
        // 空间不足
        assertThrows(IllegalStateException.class,
                () -> LayerTrafficAllocator.findFreeGap(6000, List.of(r(0, 4999))));
    }

    @Test
    @DisplayName("按百分比切分实验桶段：整层与子段，末段吃余数完全覆盖")
    void splitByPercentage() {
        // 整层 50/50
        assertEquals(List.of(r(0, 4999), r(5000, 9999)),
                LayerTrafficAllocator.splitByPercentage(r(0, 9999), List.of(50, 50)));
        // 子段 [2000,2999] 50/50
        assertEquals(List.of(r(2000, 2499), r(2500, 2999)),
                LayerTrafficAllocator.splitByPercentage(r(2000, 2999), List.of(50, 50)));
        // 33/33/34 完全覆盖、无缝隙
        List<LayerTrafficAllocator.Range> three =
                LayerTrafficAllocator.splitByPercentage(r(0, 9999), List.of(33, 33, 34));
        assertEquals(0, three.get(0).start());
        assertEquals(9999, three.get(2).end());
        // 连续无缝隙
        for (int i = 1; i < three.size(); i++) {
            assertEquals(three.get(i - 1).end() + 1, three.get(i).start());
        }
        // 合计不为 100 抛异常
        assertThrows(IllegalArgumentException.class,
                () -> LayerTrafficAllocator.splitByPercentage(r(0, 9999), List.of(50, 40)));
    }

    @Test
    @DisplayName("变体覆盖校验：完全覆盖通过，缺口/越界失败")
    void validateVariantsCoverExperiment() {
        assertDoesNotThrow(() -> LayerTrafficAllocator.validateVariantsCoverExperiment(
                r(0, 9999), List.of(r(0, 4999), r(5000, 9999))));
        // 缺口
        assertThrows(IllegalArgumentException.class,
                () -> LayerTrafficAllocator.validateVariantsCoverExperiment(
                        r(0, 9999), List.of(r(0, 4998), r(5000, 9999))));
        // 未覆盖到末尾
        assertThrows(IllegalArgumentException.class,
                () -> LayerTrafficAllocator.validateVariantsCoverExperiment(
                        r(0, 9999), List.of(r(0, 4999), r(5000, 9000))));
    }

    @Test
    @DisplayName("两个实验在同层内的桶段互斥，但变体各自覆盖本实验桶段")
    void mutualExclusionScenario() {
        // 实验A占 [0,4999]，实验B占 [5000,9999]，互不重叠
        LayerTrafficAllocator.Range a = LayerTrafficAllocator.findFreeGap(5000, List.of());
        LayerTrafficAllocator.Range b = LayerTrafficAllocator.findFreeGap(5000, List.of(a));
        assertFalse(LayerTrafficAllocator.overlaps(a, b));
        // 各实验内部变体覆盖本桶段
        assertDoesNotThrow(() -> LayerTrafficAllocator.validateVariantsCoverExperiment(
                a, LayerTrafficAllocator.splitByPercentage(a, List.of(50, 50))));
        assertDoesNotThrow(() -> LayerTrafficAllocator.validateVariantsCoverExperiment(
                b, LayerTrafficAllocator.splitByPercentage(b, List.of(50, 50))));
    }
}
