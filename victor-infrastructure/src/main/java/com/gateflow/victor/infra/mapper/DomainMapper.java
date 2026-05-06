package com.gateflow.victor.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gateflow.victor.domain.entity.Domain;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 域Mapper接口
 */
public interface DomainMapper extends BaseMapper<Domain> {

    /**
     * 查询所有域
     */
    @Select("SELECT * FROM victor_domain ORDER BY id")
    List<Domain> selectAllDomains();

    /**
     * 根据业务ID查询域
     */
    @Select("SELECT * FROM victor_domain WHERE domain_id = #{domainId}")
    Domain selectByDomainId(@Param("domainId") String domainId);
}