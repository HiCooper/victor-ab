package com.gateflow.victor.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gateflow.victor.domain.entity.Layer;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 层Mapper接口
 */
public interface LayerMapper extends BaseMapper<Layer> {

    /**
     * 查询指定域下的所有层
     */
    @Select("SELECT * FROM victor_layer WHERE domain_id = #{domainId} ORDER BY sort_order")
    List<Layer> selectByDomainId(@Param("domainId") Long domainId);

    /**
     * 根据业务ID查询层
     */
    @Select("SELECT * FROM victor_layer WHERE layer_id = #{layerId}")
    Layer selectByLayerId(@Param("layerId") String layerId);

    /**
     * 批量查询层（按ID列表）
     */
    @Select("<script>SELECT * FROM victor_layer WHERE id IN " +
            "<foreach item='id' collection='ids' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<Layer> selectByIds(@Param("ids") List<Long> ids);
}