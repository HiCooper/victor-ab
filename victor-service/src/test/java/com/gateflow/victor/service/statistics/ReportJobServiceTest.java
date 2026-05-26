package com.gateflow.victor.service.statistics;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gateflow.victor.domain.entity.ReportJob;
import com.gateflow.victor.infra.mapper.ReportJobMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportJobServiceTest {

    @Mock
    private ReportJobMapper reportJobMapper;

    private ReportJobService service;

    private static final String JOB_ID = "job-uuid-123";
    private static final String EXP_ID = "exp_test_001";

    @BeforeEach
    void setUp() {
        service = new ReportJobService(reportJobMapper);
    }

    @Test
    @DisplayName("createJob — 插入新任务记录")
    void shouldInsertNewJob() {
        service.createJob(JOB_ID, "report_generation", EXP_ID);

        ArgumentCaptor<ReportJob> captor = ArgumentCaptor.forClass(ReportJob.class);
        verify(reportJobMapper).insert(captor.capture());

        ReportJob job = captor.getValue();
        assertEquals(JOB_ID, job.getId());
        assertEquals("report_generation", job.getType());
        assertEquals(EXP_ID, job.getExperimentId());
        assertEquals("pending", job.getStatus());
        assertEquals(0, job.getProgress());
        assertNotNull(job.getStartTime());
    }

    @Test
    @DisplayName("updateJobStatus — running → completed 时设置 endTime")
    void shouldSetEndTimeWhenCompleted() {
        ReportJob existing = buildJob("running", 50);
        when(reportJobMapper.selectById(JOB_ID)).thenReturn(existing);

        service.updateJobStatus(JOB_ID, "completed", 100);

        ArgumentCaptor<ReportJob> captor = ArgumentCaptor.forClass(ReportJob.class);
        verify(reportJobMapper).updateById(captor.capture());

        ReportJob updated = captor.getValue();
        assertEquals("completed", updated.getStatus());
        assertEquals(100, updated.getProgress());
        assertNotNull(updated.getEndTime());
    }

    @Test
    @DisplayName("updateJobStatus — running → failed 时设置 endTime")
    void shouldSetEndTimeWhenFailed() {
        ReportJob existing = buildJob("running", 30);
        when(reportJobMapper.selectById(JOB_ID)).thenReturn(existing);

        service.updateJobStatus(JOB_ID, "failed", 30);

        ArgumentCaptor<ReportJob> captor = ArgumentCaptor.forClass(ReportJob.class);
        verify(reportJobMapper).updateById(captor.capture());

        ReportJob updated = captor.getValue();
        assertEquals("failed", updated.getStatus());
        assertNotNull(updated.getEndTime());
    }

    @Test
    @DisplayName("updateJobStatus — job 不存在时不抛异常")
    void shouldNotThrowWhenJobNotFound() {
        when(reportJobMapper.selectById(JOB_ID)).thenReturn(null);

        assertDoesNotThrow(() -> service.updateJobStatus(JOB_ID, "running", 50));
        verify(reportJobMapper, never()).updateById(any(ReportJob.class));
    }

    @Test
    @DisplayName("getJob — 返回 job 的 Map 表示")
    void shouldReturnJobAsMap() {
        ReportJob job = buildJob("completed", 100);
        when(reportJobMapper.selectById(JOB_ID)).thenReturn(job);

        Map<String, Object> result = service.getJob(JOB_ID);

        assertEquals(JOB_ID, result.get("id"));
        assertEquals("report_generation", result.get("type"));
        assertEquals("completed", result.get("status"));
        assertEquals(EXP_ID, result.get("experimentId"));
        assertEquals(100, result.get("progress"));
    }

    @Test
    @DisplayName("getJob — 未找到时抛出 RuntimeException")
    void shouldThrowWhenJobNotFound() {
        when(reportJobMapper.selectById(JOB_ID)).thenReturn(null);

        assertThrows(RuntimeException.class, () -> service.getJob(JOB_ID));
    }

    @Test
    @DisplayName("getJobs — 按 status 过滤")
    void shouldFilterByStatus() {
        ReportJob running = buildJob("running", 30);
        running.setId("job-1");
        ReportJob completed = buildJob("completed", 100);
        completed.setId("job-2");

        when(reportJobMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(running));

        List<Map<String, Object>> results = service.getJobs("running", null);

        assertEquals(1, results.size());
        assertEquals("job-1", results.get(0).get("id"));
        assertEquals("running", results.get(0).get("status"));
    }

    @Test
    @DisplayName("getJobs — 无过滤条件时返回全部")
    void shouldReturnAllJobsWhenNoFilter() {
        ReportJob job1 = buildJob("completed", 100);
        job1.setId("job-1");
        ReportJob job2 = buildJob("pending", 0);
        job2.setId("job-2");

        when(reportJobMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(job1, job2));

        List<Map<String, Object>> results = service.getJobs(null, null);

        assertEquals(2, results.size());
    }

    // ========== helpers ==========

    private ReportJob buildJob(String status, int progress) {
        ReportJob job = new ReportJob();
        job.setId(JOB_ID);
        job.setType("report_generation");
        job.setExperimentId(EXP_ID);
        job.setStatus(status);
        job.setProgress(progress);
        job.setStartTime(LocalDateTime.of(2026, 5, 26, 10, 0));
        return job;
    }
}
