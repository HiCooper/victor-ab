package com.gateflow.victor.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gateflow.victor.domain.entity.ExperimentApproval;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 实验审批Mapper
 */
@Mapper
public interface ExperimentApprovalMapper extends BaseMapper<ExperimentApproval> {

    /**
     * 根据expId查询最新的审批记录
     */
    @Select("SELECT * FROM victor_experiment_approval WHERE exp_id = #{expId} ORDER BY created_at DESC LIMIT 1")
    ExperimentApproval selectLatestByExpId(@Param("expId") String expId);

    /**
     * 查询待审批列表
     */
    @Select("SELECT * FROM victor_experiment_approval WHERE status = 'pending' ORDER BY created_at ASC")
    List<ExperimentApproval> selectPending();

    /**
     * 查询expId的所有审批记录
     */
    @Select("SELECT * FROM victor_experiment_approval WHERE exp_id = #{expId} ORDER BY created_at DESC")
    List<ExperimentApproval> selectByExpId(@Param("expId") String expId);
}