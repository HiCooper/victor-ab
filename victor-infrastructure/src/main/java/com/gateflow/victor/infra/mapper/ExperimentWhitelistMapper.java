package com.gateflow.victor.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gateflow.victor.domain.entity.ExperimentWhitelist;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 实验白名单Mapper
 */
public interface ExperimentWhitelistMapper extends BaseMapper<ExperimentWhitelist> {

    /**
     * 根据实验业务ID查询白名单
     */
    @Select("SELECT * FROM victor_experiment_whitelist WHERE exp_id = #{expId}")
    List<ExperimentWhitelist> selectByExpId(@Param("expId") String expId);

    /**
     * 根据实验业务ID和分桶ID查询白名单
     */
    @Select("SELECT * FROM victor_experiment_whitelist WHERE exp_id = #{expId} AND bucket_id = #{bucketId}")
    List<ExperimentWhitelist> selectByExpIdAndBucketId(@Param("expId") String expId, @Param("bucketId") String bucketId);

    /**
     * 根据用户ID查询白名单记录
     */
    @Select("SELECT * FROM victor_experiment_whitelist WHERE user_ids LIKE CONCAT('%', #{userId}, '%')")
    List<ExperimentWhitelist> selectByUserId(@Param("userId") String userId);
}
