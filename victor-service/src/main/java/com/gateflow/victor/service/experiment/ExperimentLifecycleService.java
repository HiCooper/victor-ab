package com.gateflow.victor.service.experiment;

import com.gateflow.victor.common.enums.ExperimentStatus;
import com.gateflow.victor.common.constant.ErrorCode;
import com.gateflow.victor.common.exception.VictorException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 实验生命周期状态机服务
 * 负责状态转换验证、审计日志记录
 */
@Slf4j
@Service
public class ExperimentLifecycleService {

    // 状态转换规则: key=当前状态, value=允许转换的目标状态集合
    private static final Map<ExperimentStatus, Set<ExperimentStatus>> ALLOWED_TRANSITIONS = new HashMap<>();

    static {
        // 草稿 -> 待审批/删除
        ALLOWED_TRANSITIONS.put(ExperimentStatus.DRAFT,
            Set.of(ExperimentStatus.PENDING_APPROVAL));

        // 待审批 -> 已审批(通过)/已拒绝(驳回)
        ALLOWED_TRANSITIONS.put(ExperimentStatus.PENDING_APPROVAL,
            Set.of(ExperimentStatus.APPROVED, ExperimentStatus.REJECTED));

        // 已审批 -> 渐进上线
        ALLOWED_TRANSITIONS.put(ExperimentStatus.APPROVED,
            Set.of(ExperimentStatus.RAMP));

        // 已拒绝 -> 草稿(重新修改)
        ALLOWED_TRANSITIONS.put(ExperimentStatus.REJECTED,
            Set.of(ExperimentStatus.DRAFT));

        // 待审核(兼容旧代码) -> 草稿(驳回)/渐进上线(通过)
        ALLOWED_TRANSITIONS.put(ExperimentStatus.REVIEW,
            Set.of(ExperimentStatus.DRAFT, ExperimentStatus.RAMP));

        // 渐进上线 -> 运行中/已暂停
        ALLOWED_TRANSITIONS.put(ExperimentStatus.RAMP,
            Set.of(ExperimentStatus.RUNNING, ExperimentStatus.PAUSED));

        // 运行中 -> 已暂停/分析中
        ALLOWED_TRANSITIONS.put(ExperimentStatus.RUNNING,
            Set.of(ExperimentStatus.PAUSED, ExperimentStatus.ANALYZING));

        // 已暂停 -> 运行中/分析中
        ALLOWED_TRANSITIONS.put(ExperimentStatus.PAUSED,
            Set.of(ExperimentStatus.RUNNING, ExperimentStatus.ANALYZING));

        // 分析中 -> 决策阶段
        ALLOWED_TRANSITIONS.put(ExperimentStatus.ANALYZING,
            Set.of(ExperimentStatus.DECISION));

        // 决策阶段 -> 渐进上线(迭代)/已归档(全量/回滚)
        ALLOWED_TRANSITIONS.put(ExperimentStatus.DECISION,
            Set.of(ExperimentStatus.RAMP, ExperimentStatus.ARCHIVE));

        // 已归档/已停止 -> 不可转换
        ALLOWED_TRANSITIONS.put(ExperimentStatus.ARCHIVE, Set.of());
        ALLOWED_TRANSITIONS.put(ExperimentStatus.STOPPED, Set.of());
    }

    /**
     * 验证状态转换是否合法
     */
    public void validateTransition(ExperimentStatus from, ExperimentStatus to) {
        Set<ExperimentStatus> allowedTargets = ALLOWED_TRANSITIONS.get(from);
        
        if (allowedTargets == null || !allowedTargets.contains(to)) {
            throw new VictorException(ErrorCode.LFC_INVALID_TRANSITION, String.format(
                "Invalid state transition: %s -> %s. Allowed transitions: %s",
                from.getDescription(),
                to.getDescription(),
                allowedTargets == null ? "none" : allowedTargets.stream()
                    .map(ExperimentStatus::getDescription)
                    .toList()
            ));
        }
    }

    /**
     * 记录状态转换日志
     */
    public void logTransition(Long experimentId, String expId, 
                             ExperimentStatus from, ExperimentStatus to,
                             String operator, String reason) {
        log.info("Experiment [{}] {} transitioned: {} -> {} by {} (reason: {})",
            experimentId, expId, 
            from.getDescription(), to.getDescription(), 
            operator, reason);
        
        // TODO: 持久化到审计日志表
        // auditLogMapper.insert(AuditLog.builder()
        //     .experimentId(experimentId)
        //     .fromStatus(from.getCode())
        //     .toStatus(to.getCode())
        //     .operator(operator)
        //     .reason(reason)
        //     .createdAt(LocalDateTime.now())
        //     .build());
    }

    /**
     * 获取当前状态允许的所有操作
     */
    public List<String> getAvailableActions(ExperimentStatus status) {
        return switch (status) {
            case DRAFT -> List.of("提交审批", "删除");
            case PENDING_APPROVAL -> List.of("审批通过", "驳回");
            case APPROVED -> List.of("启动", "删除");
            case REJECTED -> List.of("重新编辑");
            case RUNNING -> List.of("暂停", "停止");
            case PAUSED -> List.of("恢复", "停止");
            case STOPPED -> List.of("查看");
            case REVIEW -> List.of("审批通过", "驳回");
            case RAMP -> List.of("全量运行", "暂停");
            case ANALYZING -> List.of("生成决策");
            case DECISION -> List.of("全量发布", "回滚归档");
            case ARCHIVE -> List.of("查看");
        };
    }

    /**
     * 检查实验是否可以执行某个操作
     */
    public boolean canPerformAction(ExperimentStatus status, String action) {
        return getAvailableActions(status).contains(action);
    }
}
