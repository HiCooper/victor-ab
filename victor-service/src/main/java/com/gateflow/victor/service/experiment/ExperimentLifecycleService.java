package com.gateflow.victor.service.experiment;

import com.gateflow.victor.common.enums.ExperimentStatus;
import com.gateflow.victor.common.constant.ErrorCode;
import com.gateflow.victor.common.exception.VictorException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 实验生命周期状态机服务
 * 状态: draft → pending_approval → running → stopped → archive
 */
@Slf4j
@Service
public class ExperimentLifecycleService {

    private static final Map<ExperimentStatus, Set<ExperimentStatus>> ALLOWED_TRANSITIONS = new HashMap<>();

    static {
        ALLOWED_TRANSITIONS.put(ExperimentStatus.DRAFT,
            Set.of(ExperimentStatus.PENDING_APPROVAL, ExperimentStatus.RUNNING));

        ALLOWED_TRANSITIONS.put(ExperimentStatus.PENDING_APPROVAL,
            Set.of(ExperimentStatus.RUNNING, ExperimentStatus.DRAFT));

        ALLOWED_TRANSITIONS.put(ExperimentStatus.RUNNING,
            Set.of(ExperimentStatus.STOPPED));

        ALLOWED_TRANSITIONS.put(ExperimentStatus.STOPPED,
            Set.of(ExperimentStatus.ARCHIVE));

        ALLOWED_TRANSITIONS.put(ExperimentStatus.ARCHIVE, Set.of());
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
    }

    /**
     * 获取当前状态允许的所有操作
     */
    public List<String> getAvailableActions(ExperimentStatus status) {
        return switch (status) {
            case DRAFT -> List.of("编辑", "提交审批", "启动", "删除");
            case PENDING_APPROVAL -> List.of("审批通过", "驳回");
            case RUNNING -> List.of("停止");
            case STOPPED -> List.of("归档", "查看");
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
