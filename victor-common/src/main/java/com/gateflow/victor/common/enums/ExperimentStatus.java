package com.gateflow.victor.common.enums;

import lombok.Getter;

/**
 * 实验状态枚举
 */
@Getter
public enum ExperimentStatus {

    /**
     * 草稿 - 实验创建初期，可编辑
     */
    DRAFT("draft", "草稿"),

    /**
     * 待审核 - 等待审批
     */
    REVIEW("review", "待审核"),

    /**
     * 渐进上线 - 逐步放量
     */
    RAMP("ramp", "渐进上线"),

    /**
     * 运行中 - 实验正在进行
     */
    RUNNING("running", "运行中"),

    /**
     * 已暂停 - 实验暂停
     */
    PAUSED("paused", "已暂停"),

    /**
     * 分析中 - 实验结束，数据分析阶段
     */
    ANALYZING("analyzing", "分析中"),

    /**
     * 决策阶段 - 分析完成，等待决策
     */
    DECISION("decision", "决策阶段"),

    /**
     * 已归档 - 实验结束并归档
     */
    ARCHIVE("archive", "已归档");

    private final String code;
    private final String description;

    ExperimentStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据code获取枚举
     */
    public static ExperimentStatus fromCode(String code) {
        for (ExperimentStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        return null;
    }

    /**
     * 判断是否可以分桶 (运行中或渐进上线状态)
     */
    public boolean isBucketable() {
        return this == RUNNING || this == RAMP;
    }

    /**
     * 判断是否可以编辑
     */
    public boolean isEditable() {
        return this == DRAFT || this == REVIEW || this == PAUSED;
    }
}