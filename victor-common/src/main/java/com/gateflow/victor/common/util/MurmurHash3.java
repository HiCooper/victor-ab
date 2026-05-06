package com.gateflow.victor.common.util;

import java.nio.charset.StandardCharsets;

/**
 * MurmurHash3 实现 - 用于分桶计算
 * 
 * MurmurHash3 是一种非加密型哈希函数，具有以下特点：
 * - 高性能：适合大规模数据处理
 * - 低碰撞率：分布均匀，适合分桶场景
 * - 确定性：相同输入始终产生相同输出
 */
public final class MurmurHash3 {

    private static final int C1 = 0xcc9e2d51;
    private static final int C2 = 0x1b873593;

    private MurmurHash3() {
        // 工具类，禁止实例化
    }

    /**
     * 计算32位哈希值
     *
     * @param data 输入数据
     * @return 32位哈希值
     */
    public static int hash32(byte[] data) {
        return hash32(data, 0, data.length, 0);
    }

    /**
     * 计算32位哈希值（指定种子）
     *
     * @param data   输入数据
     * @param offset 偏移量
     * @param length 数据长度
     * @param seed   种子值
     * @return 32位哈希值
     */
    public static int hash32(byte[] data, int offset, int length, int seed) {
        int h = seed;
        int roundedEnd = offset + (length & 0xfffffffc); // 按4字节对齐

        // 处理4字节块
        for (int i = offset; i < roundedEnd; i += 4) {
            int k = (data[i] & 0xff)
                    | ((data[i + 1] & 0xff) << 8)
                    | ((data[i + 2] & 0xff) << 16)
                    | ((data[i + 3] & 0xff) << 24);

            k *= C1;
            k = Integer.rotateLeft(k, 15);
            k *= C2;

            h ^= k;
            h = Integer.rotateLeft(h, 13);
            h = h * 5 + 0xe6546b64;
        }

        // 处理剩余字节
        int k = 0;
        for (int i = roundedEnd; i < offset + length; i++) {
            k = (k << 8) | (data[i] & 0xff);
        }

        if (k != 0) {
            k *= C1;
            k = Integer.rotateLeft(k, 15);
            k *= C2;
            h ^= k;
        }

        // 最终混合
        h ^= length;
        h ^= h >>> 16;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        h *= 0xc2b2ae35;
        h ^= h >>> 16;

        return h;
    }

    /**
     * 计算字符串的32位哈希值
     *
     * @param str 输入字符串
     * @return 32位哈希值
     */
    public static int hash32(String str) {
        byte[] data = str.getBytes(StandardCharsets.UTF_8);
        return hash32(data);
    }

    /**
     * 计算字符串的32位哈希值（指定种子）
     *
     * @param str  输入字符串
     * @param seed 种子值
     * @return 32位哈希值
     */
    public static int hash32(String str, int seed) {
        byte[] data = str.getBytes(StandardCharsets.UTF_8);
        return hash32(data, 0, data.length, seed);
    }

    /**
     * 计算64位哈希值（返回高64位）
     *
     * @param data 输入数据
     * @return 64位哈希值
     */
    public static long hash64(byte[] data) {
        return hash64(data, 0, data.length, 0);
    }

    /**
     * 计算64位哈希值（指定种子）
     *
     * @param data   输入数据
     * @param offset 偏移量
     * @param length 数据长度
     * @param seed   种子值
     * @return 64位哈希值
     */
    public static long hash64(byte[] data, int offset, int length, int seed) {
        long h = seed;
        long c1 = 0x87c37b91114253d5L;
        long c2 = 0x4cf5ad432745937fL;

        int roundedEnd = offset + (length & 0xfffffff8); // 按8字节对齐

        for (int i = offset; i < roundedEnd; i += 8) {
            long k = ((long) data[i] & 0xff)
                    | (((long) data[i + 1] & 0xff) << 8)
                    | (((long) data[i + 2] & 0xff) << 16)
                    | (((long) data[i + 3] & 0xff) << 24)
                    | (((long) data[i + 4] & 0xff) << 32)
                    | (((long) data[i + 5] & 0xff) << 40)
                    | (((long) data[i + 6] & 0xff) << 48)
                    | (((long) data[i + 7] & 0xff) << 56);

            k *= c1;
            k = Long.rotateLeft(k, 31);
            k *= c2;

            h ^= k;
            h = Long.rotateLeft(h, 27);
            h = h * 5 + 0x52dce729;
        }

        // 处理剩余字节
        long k = 0;
        for (int i = roundedEnd; i < offset + length; i++) {
            k = (k << 8) | ((long) data[i] & 0xff);
        }

        if (k != 0) {
            k *= c1;
            k = Long.rotateLeft(k, 31);
            k *= c2;
            h ^= k;
        }

        // 最终混合
        h ^= length;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;

        return h;
    }

    /**
     * 计算字符串的64位哈希值
     *
     * @param str 输入字符串
     * @return 64位哈希值
     */
    public static long hash64(String str) {
        byte[] data = str.getBytes(StandardCharsets.UTF_8);
        return hash64(data);
    }
}