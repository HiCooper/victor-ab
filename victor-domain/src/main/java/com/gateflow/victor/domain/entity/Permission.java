package com.gateflow.victor.domain.entity;

/**
 * 权限枚举
 */
public enum Permission {

    // 实验相关
    CREATE_EXPERIMENT("创建实验"),
    EDIT_EXPERIMENT("编辑实验"),
    DELETE_EXPERIMENT("删除实验"),
    VIEW_EXPERIMENT("查看实验"),

    // 审批相关
    APPROVE_EXPERIMENT("审批实验"),
    SUBMIT_APPROVAL("提交审批"),

    // 流量相关
    VIEW_TRAFFIC("查看流量"),

    // 分析相关
    VIEW_ANALYSIS("查看分析"),
    POWER_ANALYSIS("功效分析"),

    // 用户管理
    MANAGE_USERS("用户管理");

    private final String description;

    Permission(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}