package com.gateflow.victor.common.util;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 分桶ID生成器
 *
 * 格式：5位随机数字 (00000-99999)
 *
 * 示例：
 * - 12345
 * - 98765
 * - 00001
 */
public final class BucketIdGenerator {

    /**
     * 分桶ID总长度
     */
    public static final int LENGTH = 5;

    /**
     * 随机数最大值（5位：00000-99999）
     */
    private static final int RANDOM_MAX = (int) Math.pow(10, LENGTH);

    private BucketIdGenerator() {
    }

    /**
     * 生成5位随机分桶ID
     *
     * @return 5位分桶ID字符串
     */
    public static String generate() {
        int randomNum = ThreadLocalRandom.current().nextInt(RANDOM_MAX);
        return String.format("%0" + LENGTH + "d", randomNum);
    }
}
