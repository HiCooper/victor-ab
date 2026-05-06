package com.gateflow.victor.service.experiment;

import com.gateflow.victor.common.enums.ExperimentStatus;
import com.gateflow.victor.common.exception.VictorException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 实验生命周期状态机测试
 */
class ExperimentLifecycleServiceTest {

    private ExperimentLifecycleService lifecycleService;

    @BeforeEach
    void setUp() {
        lifecycleService = new ExperimentLifecycleService();
    }

    @Test
    void shouldAllowDraftToReview() {
        // 草稿 -> 待审核 应该成功
        assertDoesNotThrow(() -> 
            lifecycleService.validateTransition(ExperimentStatus.DRAFT, ExperimentStatus.REVIEW));
    }

    @Test
    void shouldAllowReviewToRamp() {
        // 待审核 -> 渐进上线 应该成功
        assertDoesNotThrow(() -> 
            lifecycleService.validateTransition(ExperimentStatus.REVIEW, ExperimentStatus.RAMP));
    }

    @Test
    void shouldAllowReviewToDraft() {
        // 待审核 -> 草稿(驳回) 应该成功
        assertDoesNotThrow(() -> 
            lifecycleService.validateTransition(ExperimentStatus.REVIEW, ExperimentStatus.DRAFT));
    }

    @Test
    void shouldAllowRampToRunning() {
        // 渐进上线 -> 运行中 应该成功
        assertDoesNotThrow(() -> 
            lifecycleService.validateTransition(ExperimentStatus.RAMP, ExperimentStatus.RUNNING));
    }

    @Test
    void shouldAllowRampToPaused() {
        // 渐进上线 -> 已暂停 应该成功
        assertDoesNotThrow(() -> 
            lifecycleService.validateTransition(ExperimentStatus.RAMP, ExperimentStatus.PAUSED));
    }

    @Test
    void shouldAllowRunningToPaused() {
        // 运行中 -> 已暂停 应该成功
        assertDoesNotThrow(() -> 
            lifecycleService.validateTransition(ExperimentStatus.RUNNING, ExperimentStatus.PAUSED));
    }

    @Test
    void shouldAllowRunningToAnalyzing() {
        // 运行中 -> 分析中 应该成功
        assertDoesNotThrow(() -> 
            lifecycleService.validateTransition(ExperimentStatus.RUNNING, ExperimentStatus.ANALYZING));
    }

    @Test
    void shouldAllowPausedToRunning() {
        // 已暂停 -> 运行中 应该成功
        assertDoesNotThrow(() -> 
            lifecycleService.validateTransition(ExperimentStatus.PAUSED, ExperimentStatus.RUNNING));
    }

    @Test
    void shouldAllowPausedToAnalyzing() {
        // 已暂停 -> 分析中 应该成功
        assertDoesNotThrow(() -> 
            lifecycleService.validateTransition(ExperimentStatus.PAUSED, ExperimentStatus.ANALYZING));
    }

    @Test
    void shouldAllowAnalyzingToDecision() {
        // 分析中 -> 决策阶段 应该成功
        assertDoesNotThrow(() -> 
            lifecycleService.validateTransition(ExperimentStatus.ANALYZING, ExperimentStatus.DECISION));
    }

    @Test
    void shouldAllowDecisionToArchive() {
        // 决策阶段 -> 已归档 应该成功
        assertDoesNotThrow(() -> 
            lifecycleService.validateTransition(ExperimentStatus.DECISION, ExperimentStatus.ARCHIVE));
    }

    @Test
    void shouldAllowDecisionToRamp() {
        // 决策阶段 -> 渐进上线(迭代) 应该成功
        assertDoesNotThrow(() -> 
            lifecycleService.validateTransition(ExperimentStatus.DECISION, ExperimentStatus.RAMP));
    }

    @Test
    void shouldRejectDraftToRunning() {
        // 草稿 -> 运行中 应该失败（必须先经过审核）
        VictorException exception = assertThrows(VictorException.class, () -> 
            lifecycleService.validateTransition(ExperimentStatus.DRAFT, ExperimentStatus.RUNNING));
        
        assertTrue(exception.getMessage().contains("Invalid state transition"));
    }

    @Test
    void shouldRejectRunningToDraft() {
        // 运行中 -> 草稿 应该失败
        VictorException exception = assertThrows(VictorException.class, () -> 
            lifecycleService.validateTransition(ExperimentStatus.RUNNING, ExperimentStatus.DRAFT));
        
        assertTrue(exception.getMessage().contains("Invalid state transition"));
    }

    @Test
    void shouldRejectArchiveToAny() {
        // 已归档 -> 任何状态 应该失败
        for (ExperimentStatus status : ExperimentStatus.values()) {
            if (status != ExperimentStatus.ARCHIVE) {
                VictorException exception = assertThrows(VictorException.class, () -> 
                    lifecycleService.validateTransition(ExperimentStatus.ARCHIVE, status));
                assertTrue(exception.getMessage().contains("Invalid state transition"));
            }
        }
    }

    @Test
    void shouldReturnCorrectActionsForDraft() {
        List<String> actions = lifecycleService.getAvailableActions(ExperimentStatus.DRAFT);
        assertTrue(actions.contains("提交审核"));
        assertTrue(actions.contains("删除"));
        assertEquals(2, actions.size());
    }

    @Test
    void shouldReturnCorrectActionsForReview() {
        List<String> actions = lifecycleService.getAvailableActions(ExperimentStatus.REVIEW);
        assertTrue(actions.contains("审批通过"));
        assertTrue(actions.contains("驳回"));
        assertEquals(2, actions.size());
    }

    @Test
    void shouldReturnCorrectActionsForRunning() {
        List<String> actions = lifecycleService.getAvailableActions(ExperimentStatus.RUNNING);
        assertTrue(actions.contains("暂停"));
        assertTrue(actions.contains("结束分析"));
        assertEquals(2, actions.size());
    }

    @Test
    void shouldReturnCorrectActionsForAnalyzing() {
        List<String> actions = lifecycleService.getAvailableActions(ExperimentStatus.ANALYZING);
        assertTrue(actions.contains("生成决策"));
        assertEquals(1, actions.size());
    }

    @Test
    void shouldReturnCorrectActionsForDecision() {
        List<String> actions = lifecycleService.getAvailableActions(ExperimentStatus.DECISION);
        assertTrue(actions.contains("全量发布"));
        assertTrue(actions.contains("回滚归档"));
        assertTrue(actions.contains("迭代优化"));
        assertEquals(3, actions.size());
    }

    @Test
    void shouldReturnCorrectActionsForArchive() {
        List<String> actions = lifecycleService.getAvailableActions(ExperimentStatus.ARCHIVE);
        assertTrue(actions.contains("查看"));
        assertEquals(1, actions.size());
    }

    @Test
    void shouldCheckActionAvailability() {
        assertTrue(lifecycleService.canPerformAction(ExperimentStatus.DRAFT, "提交审核"));
        assertTrue(lifecycleService.canPerformAction(ExperimentStatus.REVIEW, "审批通过"));
        assertTrue(lifecycleService.canPerformAction(ExperimentStatus.RUNNING, "暂停"));
        assertFalse(lifecycleService.canPerformAction(ExperimentStatus.DRAFT, "审批通过"));
        assertFalse(lifecycleService.canPerformAction(ExperimentStatus.ARCHIVE, "删除"));
    }

    @Test
    void shouldLogTransition() {
        // 验证日志记录不抛异常
        assertDoesNotThrow(() -> 
            lifecycleService.logTransition(1L, "exp_test_001",
                ExperimentStatus.DRAFT, ExperimentStatus.REVIEW,
                "test_user", "测试提交审核"));
    }
}
