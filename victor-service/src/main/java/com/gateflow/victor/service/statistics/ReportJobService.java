package com.gateflow.victor.service.statistics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing report generation jobs
 */
@Slf4j
@Service
public class ReportJobService {

    // In-memory job storage (in production, use Redis or database)
    private final Map<String, JobRecord> jobs = new ConcurrentHashMap<>();

    /**
     * Create a new job
     */
    public void createJob(String jobId, String type, String experimentId) {
        JobRecord job = new JobRecord();
        job.setId(jobId);
        job.setType(type);
        job.setExperimentId(experimentId);
        job.setStatus("pending");
        job.setStartTime(LocalDateTime.now().toString());
        jobs.put(jobId, job);
        log.info("Created job: {}", jobId);
    }

    /**
     * Update job status
     */
    public void updateJobStatus(String jobId, String status, int progress) {
        JobRecord job = jobs.get(jobId);
        if (job != null) {
            job.setStatus(status);
            job.setProgress(progress);
            if ("completed".equals(status) || "failed".equals(status)) {
                job.setEndTime(LocalDateTime.now().toString());
            }
            log.info("Updated job {} status to: {}, progress: {}", jobId, status, progress);
        }
    }

    /**
     * Get job by ID
     */
    public Map<String, Object> getJob(String jobId) {
        JobRecord job = jobs.get(jobId);
        if (job == null) {
            throw new RuntimeException("Job not found: " + jobId);
        }
        return job.toMap();
    }

    /**
     * Get all jobs with optional filters
     */
    public List<Map<String, Object>> getJobs(String status, String type) {
        return jobs.values().stream()
                .filter(job -> status == null || status.equals(job.getStatus()))
                .filter(job -> type == null || type.equals(job.getType()))
                .map(JobRecord::toMap)
                .toList();
    }

    /**
     * Job record class
     */
    public static class JobRecord {
        private String id;
        private String type;
        private String status;
        private String experimentId;
        private String startTime;
        private String endTime;
        private int progress;
        private String message;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getExperimentId() { return experimentId; }
        public void setExperimentId(String experimentId) { this.experimentId = experimentId; }
        public String getStartTime() { return startTime; }
        public void setStartTime(String startTime) { this.startTime = startTime; }
        public String getEndTime() { return endTime; }
        public void setEndTime(String endTime) { this.endTime = endTime; }
        public int getProgress() { return progress; }
        public void setProgress(int progress) { this.progress = progress; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", id);
            map.put("type", type);
            map.put("status", status);
            map.put("experimentId", experimentId);
            map.put("startTime", startTime);
            map.put("endTime", endTime);
            map.put("progress", progress);
            map.put("message", message);
            return map;
        }
    }
}
