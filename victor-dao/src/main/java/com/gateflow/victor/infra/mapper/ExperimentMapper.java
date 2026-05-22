package com.gateflow.victor.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gateflow.victor.domain.entity.Experiment;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 实验Mapper接口
 */
public interface ExperimentMapper extends BaseMapper<Experiment> {

    /**
     * 查询运行中的实验列表
     */
    List<Experiment> selectRunningExperiments();

    /**
     * 查询指定层下的所有实验
     */
    List<Experiment> selectByLayerId(@Param("layerId") Long layerId);

    /**
     * 根据业务ID查询实验
     */
    Experiment selectByExpId(@Param("expId") String expId);

    /**
     * 查询实验及其版本 (需要在Service层处理关联)
     */
    Experiment selectById(@Param("id") Long id);
}