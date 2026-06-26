package com.gateflow.victor.common.bucketing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 层内流量分配器 —— 实现「层内互斥」的桶段分配与校验。
 * <p>
 * 模型：每层桶空间为 [0, 9999]（共 10000 桶）。同一层内的多个运行中实验各自占用一段
 * 互不重叠的子区间，从而保证一个用户在一层内最多命中一个实验（层内互斥）；不同层使用
 * 不同 salt 做独立随机化，实现层间正交。
 * <p>
 * 纯函数实现，无框架依赖，便于单元测试。
 */
public final class LayerTrafficAllocator {

    /** 层桶空间下界 */
    public static final int LAYER_MIN = 0;
    /** 层桶空间上界 */
    public static final int LAYER_MAX = 9999;
    /** 层桶总数 */
    public static final int LAYER_SIZE = 10000;

    private LayerTrafficAllocator() {
        // 工具类，禁止实例化
    }

    /**
     * 桶区间（闭区间 [start, end]）。
     */
    public record Range(int start, int end) {
        public Range {
            if (start > end) {
                throw new IllegalArgumentException("range start must be <= end: [" + start + "," + end + "]");
            }
        }

        public int width() {
            return end - start + 1;
        }
    }

    /**
     * 两个闭区间是否重叠。
     */
    public static boolean overlaps(Range a, Range b) {
        return a.start() <= b.end() && b.start() <= a.end();
    }

    /**
     * 区间是否落在合法层空间 [0, 9999] 内。
     */
    public static boolean withinLayer(Range r) {
        return r.start() >= LAYER_MIN && r.end() <= LAYER_MAX;
    }

    /**
     * 校验候选区间在层内合法且不与任何已占用区间重叠。
     *
     * @throws IllegalArgumentException 区间越界
     * @throws IllegalStateException    与已占用区间重叠
     */
    public static void validateNoOverlap(Range candidate, List<Range> occupied) {
        if (!withinLayer(candidate)) {
            throw new IllegalArgumentException(
                    "experiment range out of layer space [0,9999]: [" + candidate.start() + "," + candidate.end() + "]");
        }
        for (Range r : occupied) {
            if (overlaps(candidate, r)) {
                throw new IllegalStateException(
                        "experiment range [" + candidate.start() + "," + candidate.end()
                                + "] overlaps occupied [" + r.start() + "," + r.end() + "]");
            }
        }
    }

    /**
     * 在层空间中为给定宽度寻找第一个空闲桶段（按 start 升序的首个可用间隙）。
     *
     * @param width    需要的桶宽 (1..10000)
     * @param occupied 已被占用的区间
     * @return 分配到的区间
     * @throws IllegalArgumentException width 非法
     * @throws IllegalStateException    层内剩余空间不足
     */
    public static Range findFreeGap(int width, List<Range> occupied) {
        if (width <= 0 || width > LAYER_SIZE) {
            throw new IllegalArgumentException("width must be within [1," + LAYER_SIZE + "]: " + width);
        }
        List<Range> sorted = new ArrayList<>(occupied);
        sorted.sort(Comparator.comparingInt(Range::start));

        int cursor = LAYER_MIN;
        for (Range r : sorted) {
            if (r.start() - cursor >= width) {
                return new Range(cursor, cursor + width - 1);
            }
            cursor = Math.max(cursor, r.end() + 1);
        }
        if (LAYER_MAX - cursor + 1 >= width) {
            return new Range(cursor, cursor + width - 1);
        }
        throw new IllegalStateException("no free gap of width " + width + " in layer space");
    }

    /**
     * 将实验桶段按各变体的流量百分比（合计必须为 100）切分为连续、无缝隙、完全覆盖
     * 该桶段的变体子区间。最后一个变体吃掉舍入余数以保证完全覆盖。
     *
     * @param experimentRange 实验在层内占用的桶段
     * @param percentages     各变体流量百分比，顺序与返回结果一一对应
     * @return 每个变体的桶段
     */
    public static List<Range> splitByPercentage(Range experimentRange, List<Integer> percentages) {
        if (percentages == null || percentages.isEmpty()) {
            throw new IllegalArgumentException("percentages must not be empty");
        }
        int total = 0;
        for (Integer p : percentages) {
            if (p == null || p <= 0) {
                throw new IllegalArgumentException("each traffic percentage must be > 0");
            }
            total += p;
        }
        if (total != 100) {
            throw new IllegalArgumentException("traffic percentages must sum to 100, got " + total);
        }

        int width = experimentRange.width();
        int n = percentages.size();
        List<Range> result = new ArrayList<>(n);
        int cursor = experimentRange.start();
        for (int i = 0; i < n; i++) {
            int span;
            if (i == n - 1) {
                // 最后一个变体吃掉余数，确保完全覆盖、无缝隙
                span = experimentRange.end() - cursor + 1;
            } else {
                span = (int) Math.round(percentages.get(i) / 100.0 * width);
                // 防御：至少 1 桶，且为后续变体各保留至少 1 桶
                int maxSpan = experimentRange.end() - cursor + 1 - (n - 1 - i);
                span = Math.max(1, Math.min(span, maxSpan));
            }
            result.add(new Range(cursor, cursor + span - 1));
            cursor += span;
        }
        return result;
    }

    /**
     * 校验变体子区间连续、无缝隙、无重叠，且完全覆盖实验桶段。
     *
     * @throws IllegalArgumentException 不满足覆盖约束
     */
    public static void validateVariantsCoverExperiment(Range experimentRange, List<Range> variants) {
        if (variants == null || variants.isEmpty()) {
            throw new IllegalArgumentException("variants must not be empty");
        }
        List<Range> sorted = new ArrayList<>(variants);
        sorted.sort(Comparator.comparingInt(Range::start));
        int cursor = experimentRange.start();
        for (Range v : sorted) {
            if (!withinLayer(v)) {
                throw new IllegalArgumentException("variant range out of layer space: [" + v.start() + "," + v.end() + "]");
            }
            if (v.start() != cursor) {
                throw new IllegalArgumentException(
                        "variant ranges must be contiguous within experiment range; expected start "
                                + cursor + " but got " + v.start());
            }
            cursor = v.end() + 1;
        }
        if (cursor - 1 != experimentRange.end()) {
            throw new IllegalArgumentException(
                    "variant ranges must fully cover experiment range [" + experimentRange.start()
                            + "," + experimentRange.end() + "], but covered up to " + (cursor - 1));
        }
    }
}
