package com.gateflow.victor.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("victor_report_job")
public class ReportJob {

    @TableId(type = IdType.INPUT)
    private String id;

    @TableField("type")
    private String type;

    @TableField("experiment_id")
    private String experimentId;

    @TableField("status")
    private String status;

    @TableField("progress")
    private Integer progress;

    @TableField("message")
    private String message;

    @TableField("start_time")
    private LocalDateTime startTime;

    @TableField("end_time")
    private LocalDateTime endTime;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
