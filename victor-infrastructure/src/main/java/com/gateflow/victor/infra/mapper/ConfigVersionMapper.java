package com.gateflow.victor.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gateflow.victor.domain.entity.ConfigVersion;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 配置版本Mapper接口
 */
public interface ConfigVersionMapper extends BaseMapper<ConfigVersion> {

    /**
     * 查询最新配置版本
     */
    ConfigVersion selectLatestVersion();

    /**
     * 查询指定版本之后的变更
     */
    List<ConfigVersion> selectChangesAfterVersion(@Param("fromVersion") String fromVersion);
}