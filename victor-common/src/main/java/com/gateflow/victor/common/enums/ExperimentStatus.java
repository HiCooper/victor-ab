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
     * 待审批 - 等待审批
     */
    PENDING_APPROVAL("pending_approval", "待审批"),

    /**
     * 运行中 - 实验正在进行，分桶生效
     */
    RUNNING("running", "运行中"),

    /**
     * 已停止 - 实验结束
     */
    STOPPED("stopped", "已停止"),

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
     * 判断是否可以分桶 (运行中状态)
     */
    public boolean isBucketable() {
        return this == RUNNING;
    }

    /**
     * 判断是否可以编辑
     */
    public boolean isEditable() {
        return this == DRAFT;
    }
}
