package com.gateflow.victor.common.util;

import java.time.LocalDate;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 实验ID生成器
 * <p>
 * 格式：年份最后1位 + 月份(2位)+ 随机数字(4位)
 * 总长度：7位
 * <p>
 * 示例：
 * - 2026年5月 → 605XXXX (XXX为4位随机数)
 * - 2026年12月 → 612XXXX (XXX为4位随机数)
 */
public class ExperimentIdGenerator {

    /**
     * ID总长度
     */
    private static final int TOTAL_LENGTH = 7;

    /**
     * 日期部分长度（年1位 + 月2位）
     */
    private static final int DATE_PART_LENGTH = 3;

    /**
     * 随机数部分长度
     */
    private static final int RANDOM_PART_LENGTH = TOTAL_LENGTH - DATE_PART_LENGTH;

    /**
     * 随机数最大值（4位随机数：0000-9999）
     */
    private static final int RANDOM_MAX = (int) Math.pow(10, RANDOM_PART_LENGTH);

    /**
     * 生成实验ID
     *
     * @return 7位实验ID字符串
     * @throws RuntimeException 如果生成的ID长度不符合要求
     */
    public static String generate() {
        return generate(LocalDate.now());
    }

    /**
     * 生成实验ID（指定日期）
     *
     * @param date 日期
     * @return 7位实验ID字符串
     */
    public static String generate(LocalDate date) {
        // 1. 生成日期部分（3位）
        String datePart = formatDatePart(date);

        // 2. 使用ThreadLocalRandom生成真正的随机数
        int randomNum = ThreadLocalRandom.current().nextInt(RANDOM_MAX);
        String randomPart = String.format("%0" + RANDOM_PART_LENGTH + "d", randomNum);

        // 3. 组合ID
        String experimentId = datePart + randomPart;

        // 4. 验证长度
        if (experimentId.length() != TOTAL_LENGTH) {
            throw new RuntimeException(
                    String.format("Generated experiment ID length %d does not match expected %d",
                            experimentId.length(), TOTAL_LENGTH)
            );
        }

        return experimentId;
    }

    /**
     * 生成日期部分（3位）
     *
     * @param date 年份最后一位 + 月份2位
     * @return str
     */
    private static String formatDatePart(LocalDate date) {
        int yearLastDigit = date.getYear() % 10;
        return yearLastDigit + String.format("%02d", date.getMonthValue());
    }
}
