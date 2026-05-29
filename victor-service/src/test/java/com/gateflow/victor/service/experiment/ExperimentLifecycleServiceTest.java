package com.gateflow.victor.service.experiment;

import com.gateflow.victor.common.enums.ExperimentStatus;
import com.gateflow.victor.common.exception.VictorException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExperimentLifecycleServiceTest {

    private ExperimentLifecycleService lifecycleService;

    @BeforeEach
    void setUp() {
        lifecycleService = new ExperimentLifecycleService();
    }

    @Test
    void shouldAllowDraftToPendingApproval() {
        assertDoesNotThrow(() ->
            lifecycleService.validateTransition(ExperimentStatus.DRAFT, ExperimentStatus.PENDING_APPROVAL));
    }

    @Test
    void shouldAllowDraftToRunning() {
        assertDoesNotThrow(() ->
            lifecycleService.validateTransition(ExperimentStatus.DRAFT, ExperimentStatus.RUNNING));
    }

    @Test
    void shouldAllowPendingApprovalToRunning() {
        assertDoesNotThrow(() ->
            lifecycleService.validateTransition(ExperimentStatus.PENDING_APPROVAL, ExperimentStatus.RUNNING));
    }

    @Test
    void shouldAllowPendingApprovalToDraft() {
        assertDoesNotThrow(() ->
            lifecycleService.validateTransition(ExperimentStatus.PENDING_APPROVAL, ExperimentStatus.DRAFT));
    }

    @Test
    void shouldAllowRunningToStopped() {
        assertDoesNotThrow(() ->
            lifecycleService.validateTransition(ExperimentStatus.RUNNING, ExperimentStatus.STOPPED));
    }

    @Test
    void shouldAllowStoppedToArchive() {
        assertDoesNotThrow(() ->
            lifecycleService.validateTransition(ExperimentStatus.STOPPED, ExperimentStatus.ARCHIVE));
    }

    @Test
    void shouldRejectRunningToArchive() {
        VictorException exception = assertThrows(VictorException.class, () ->
            lifecycleService.validateTransition(ExperimentStatus.RUNNING, ExperimentStatus.ARCHIVE));
        assertTrue(exception.getMessage().contains("无效的状态转换"));
    }

    @Test
    void shouldRejectDraftToStopped() {
        VictorException exception = assertThrows(VictorException.class, () ->
            lifecycleService.validateTransition(ExperimentStatus.DRAFT, ExperimentStatus.STOPPED));
        assertTrue(exception.getMessage().contains("无效的状态转换"));
    }

    @Test
    void shouldRejectArchiveToAny() {
        for (ExperimentStatus status : ExperimentStatus.values()) {
            if (status != ExperimentStatus.ARCHIVE) {
                VictorException exception = assertThrows(VictorException.class, () ->
                    lifecycleService.validateTransition(ExperimentStatus.ARCHIVE, status));
                assertTrue(exception.getMessage().contains("无效的状态转换"));
            }
        }
    }

    @Test
    void shouldReturnCorrectActionsForDraft() {
        List<String> actions = lifecycleService.getAvailableActions(ExperimentStatus.DRAFT);
        assertTrue(actions.contains("编辑"));
        assertTrue(actions.contains("提交审批"));
        assertTrue(actions.contains("启动"));
        assertTrue(actions.contains("删除"));
    }

    @Test
    void shouldReturnCorrectActionsForRunning() {
        List<String> actions = lifecycleService.getAvailableActions(ExperimentStatus.RUNNING);
        assertTrue(actions.contains("停止"));
    }

    @Test
    void shouldReturnCorrectActionsForArchive() {
        List<String> actions = lifecycleService.getAvailableActions(ExperimentStatus.ARCHIVE);
        assertTrue(actions.contains("查看"));
        assertEquals(1, actions.size());
    }

    @Test
    void shouldCheckActionAvailability() {
        assertTrue(lifecycleService.canPerformAction(ExperimentStatus.DRAFT, "提交审批"));
        assertTrue(lifecycleService.canPerformAction(ExperimentStatus.PENDING_APPROVAL, "审批通过"));
        assertTrue(lifecycleService.canPerformAction(ExperimentStatus.RUNNING, "停止"));
        assertFalse(lifecycleService.canPerformAction(ExperimentStatus.DRAFT, "停止"));
        assertFalse(lifecycleService.canPerformAction(ExperimentStatus.ARCHIVE, "删除"));
    }

    @Test
    void shouldLogTransition() {
        assertDoesNotThrow(() ->
            lifecycleService.logTransition(1L, "exp_test_001",
                ExperimentStatus.DRAFT, ExperimentStatus.PENDING_APPROVAL,
                "test_user", "测试提交审批"));
    }
}
