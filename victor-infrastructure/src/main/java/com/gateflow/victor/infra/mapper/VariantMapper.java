package com.gateflow.victor.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gateflow.victor.domain.entity.Variant;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 版本Mapper接口
 */
public interface VariantMapper extends BaseMapper<Variant> {

    /**
     * 查询实验的所有版本（所有历史版本）
     */
    @Select("SELECT * FROM victor_variant WHERE exp_id = #{expId} ORDER BY version DESC, variant_key ASC")
    List<Variant> selectByExpId(@Param("expId") Long expId);

    /**
     * 查询实验的当前活跃版本
     */
    @Select("SELECT * FROM victor_variant WHERE exp_id = #{expId} AND is_active = TRUE ORDER BY variant_key ASC")
    List<Variant> selectActiveVariants(@Param("expId") Long expId);

    /**
     * 查询实验的指定版本
     */
    @Select("SELECT * FROM victor_variant WHERE exp_id = #{expId} AND version = #{version} ORDER BY variant_key ASC")
    List<Variant> selectByExpIdAndVersion(@Param("expId") Long expId, @Param("version") String version);

    /**
     * 查询实验的所有版本号列表
     */
    @Select("SELECT DISTINCT version FROM victor_variant WHERE exp_id = #{expId} ORDER BY version DESC")
    List<String> selectVersionsByExpId(@Param("expId") Long expId);

    /**
     * 将实验的所有版本标记为非活跃
     */
    @Update("UPDATE victor_variant SET is_active = FALSE WHERE exp_id = #{expId}")
    int deactivateAllVariants(@Param("expId") Long expId);

    /**
     * 激活实验的指定版本
     */
    @Update("UPDATE victor_variant SET is_active = TRUE WHERE exp_id = #{expId} AND version = #{version}")
    int activateVersion(@Param("expId") Long expId, @Param("version") String version);

    /**
     * 删除实验的所有版本
     */
    @Delete("DELETE FROM victor_variant WHERE exp_id = #{expId}")
    int deleteByExpId(@Param("expId") Long expId);

    /**
     * 删除实验的指定版本
     */
    @Delete("DELETE FROM victor_variant WHERE exp_id = #{expId} AND version = #{version}")
    int deleteByVersion(@Param("expId") Long expId, @Param("version") String version);
}