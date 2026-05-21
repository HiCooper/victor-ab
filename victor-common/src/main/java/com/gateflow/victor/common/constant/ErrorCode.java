package com.gateflow.victor.common.constant;

/**
 * 错误码枚举
 * 格式: {模块}_{序号}，模块缩写 + 3位数字
 */
public enum ErrorCode {

    // ========== 实验管理 (EXP) ==========
    EXP_NOT_FOUND("EXP_001", "实验不存在"),
    EXP_CANNOT_DELETE_RUNNING("EXP_002", "运行中的实验不能删除"),
    EXP_ONLY_DRAFT_CAN_MODIFY("EXP_003", "只有草稿状态的实验可以修改核心配置"),
    EXP_ONLY_DRAFT_CAN_START("EXP_004", "只有草稿或暂停状态的实验可以启动"),
    EXP_NO_ACTIVE_VARIANT("EXP_005", "实验必须至少有一个活跃版本"),
    EXP_DECISION_MUST_ANALYZING("EXP_006", "只有分析中的实验可以生成决策"),
    EXP_ALREADY_EXISTS("EXP_007", "实验ID已存在"),

    // ========== 层管理 (LYR) ==========
    LAYER_NOT_FOUND("LYR_001", "层不存在"),
    LAYER_HAS_EXPERIMENTS("LYR_002", "层下存在实验，无法删除"),

    // ========== 变体管理 (VAR) ==========
    VARIANT_NOT_FOUND("VAR_001", "变体不存在"),
    VARIANT_EMPTY_LIST("VAR_002", "变体列表不能为空"),
    VARIANT_BUCKET_OUT_OF_RANGE("VAR_003", "变体桶范围超出实验桶范围"),
    VARIANT_BUCKET_INVALID("VAR_004", "变体桶起始值必须小于结束值"),
    VARIANT_BUCKET_MUST_COVER("VAR_005", "变体桶范围必须覆盖整个实验桶范围"),
    VARIANT_ONLY_DRAFT_ADD("VAR_006", "只有草稿状态的实验可以添加变体"),
    VARIANT_ONLY_DRAFT_MODIFY("VAR_007", "只有草稿状态的实验可以修改变体"),
    VARIANT_ONLY_DRAFT_DELETE("VAR_008", "只有草稿状态的实验可以删除变体"),
    VARIANT_DUPLICATE_KEY("VAR_009", "变体标识重复"),

    // ========== 分桶 (BKT) ==========
    BKT_OUT_OF_RANGE("BKT_001", "桶范围必须在 [0, 9999] 范围内"),
    BKT_TRAFFIC_PERCENTAGE("BKT_002", "流量比例总和必须为100%"),
    BKT_OVERLAP("BKT_003", "桶范围与同层其他实验重叠"),
    BKT_LAST_END_MUST_9999("BKT_004", "最后一个变体的桶结束值必须为9999"),
    BKT_INVALID_END("BKT_005", "无效的桶结束值"),

    // ========== 版本管理 (VER) ==========
    VER_NOT_FOUND("VER_001", "版本不存在"),
    VER_DUPLICATE("VER_002", "版本标识重复"),
    VER_ACTIVATE_FAILED("VER_003", "激活版本失败"),

    // ========== 生命周期 (LFC) ==========
    LFC_INVALID_TRANSITION("LFC_001", "无效的状态转换"),

    // ========== 分域 (DOM) ==========
    DOMAIN_NOT_FOUND("DOM_001", "分域不存在"),
    DOMAIN_HAS_LAYERS("DOM_002", "域下存在层，无法删除"),

    // ========== 通用 (SYS) ==========
    SYS_INTERNAL_ERROR("SYS_001", "系统内部错误"),
    SYS_PARAM_INVALID("SYS_002", "参数校验失败");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
