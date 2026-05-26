package com.gateflow.victor.service.statistics;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gateflow.victor.domain.entity.ReportJob;
import com.gateflow.victor.infra.mapper.ReportJobMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportJobService {

    private final ReportJobMapper reportJobMapper;

    public void createJob(String jobId, String type, String experimentId) {
        ReportJob job = new ReportJob();
        job.setId(jobId);
        job.setType(type);
        job.setExperimentId(experimentId);
        job.setStatus("pending");
        job.setProgress(0);
        job.setStartTime(LocalDateTime.now());
        reportJobMapper.insert(job);
        log.info("Created job: {}", jobId);
    }

    public void updateJobStatus(String jobId, String status, int progress) {
        ReportJob job = reportJobMapper.selectById(jobId);
        if (job != null) {
            job.setStatus(status);
            job.setProgress(progress);
            if ("completed".equals(status) || "failed".equals(status)) {
                job.setEndTime(LocalDateTime.now());
            }
            reportJobMapper.updateById(job);
            log.info("Updated job {} status to: {}, progress: {}", jobId, status, progress);
        }
    }

    public Map<String, Object> getJob(String jobId) {
        ReportJob job = reportJobMapper.selectById(jobId);
        if (job == null) {
            throw new RuntimeException("Job not found: " + jobId);
        }
        return toMap(job);
    }

    public List<Map<String, Object>> getJobs(String status, String type) {
        LambdaQueryWrapper<ReportJob> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(ReportJob::getStatus, status);
        }
        if (type != null) {
            wrapper.eq(ReportJob::getType, type);
        }
        wrapper.orderByDesc(ReportJob::getCreatedAt);
        return reportJobMapper.selectList(wrapper).stream()
                .map(ReportJobService::toMap)
                .toList();
    }

    private static Map<String, Object> toMap(ReportJob job) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", job.getId());
        map.put("type", job.getType());
        map.put("status", job.getStatus());
        map.put("experimentId", job.getExperimentId());
        map.put("startTime", job.getStartTime() != null ? job.getStartTime().toString() : null);
        map.put("endTime", job.getEndTime() != null ? job.getEndTime().toString() : null);
        map.put("progress", job.getProgress());
        map.put("message", job.getMessage());
        return map;
    }
}
