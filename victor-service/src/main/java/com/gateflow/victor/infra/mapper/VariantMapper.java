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
    @Select("SELECT id, exp_id, version, bucket_id, name, bucket_start, bucket_end, params, is_active, created_at FROM victor_bucket WHERE exp_id = #{expId} ORDER BY version DESC, bucket_start ASC")
    List<Variant> selectByExpId(@Param("expId") String expId);

    /**
     * 查询实验的当前活跃版本
     */
    @Select("SELECT id, exp_id, version, bucket_id, name, bucket_start, bucket_end, params, is_active, created_at FROM victor_bucket WHERE exp_id = #{expId} AND is_active = TRUE ORDER BY bucket_start ASC")
    List<Variant> selectActiveVariants(@Param("expId") String expId);

    /**
     * 查询实验的指定版本
     */
    @Select("SELECT id, exp_id, version, bucket_id, name, bucket_start, bucket_end, params, is_active, created_at FROM victor_bucket WHERE exp_id = #{expId} AND version = #{version} ORDER BY bucket_start ASC")
    List<Variant> selectByExpIdAndVersion(@Param("expId") String expId, @Param("version") String version);

    /**
     * 查询实验的所有版本号列表
     */
    @Select("SELECT DISTINCT version FROM victor_bucket WHERE exp_id = #{expId} ORDER BY version DESC")
    List<String> selectVersionsByExpId(@Param("expId") String expId);

    /**
     * 将实验的所有版本标记为非活跃
     */
    @Update("UPDATE victor_bucket SET is_active = FALSE WHERE exp_id = #{expId}")
    int deactivateAllVariants(@Param("expId") String expId);

    /**
     * 激活实验的指定版本
     */
    @Update("UPDATE victor_bucket SET is_active = TRUE WHERE exp_id = #{expId} AND version = #{version}")
    int activateVersion(@Param("expId") String expId, @Param("version") String version);

    /**
     * 删除实验的所有版本
     */
    @Delete("DELETE FROM victor_bucket WHERE exp_id = #{expId}")
    int deleteByExpId(@Param("expId") String expId);

    /**
     * 删除实验的指定版本
     */
    @Delete("DELETE FROM victor_bucket WHERE exp_id = #{expId} AND version = #{version}")
    int deleteByVersion(@Param("expId") String expId, @Param("version") String version);

    /**
     * 批量查询多个实验的活跃变体
     */
    @Select("<script>SELECT id, exp_id, version, bucket_id, name, bucket_start, bucket_end, params, is_active, created_at FROM victor_bucket WHERE exp_id IN " +
        "<foreach item='id' collection='expIds' open='(' separator=',' close=')'>#{id}</foreach> " +
        "AND is_active = TRUE ORDER BY exp_id, bucket_start ASC</script>")
    List<Variant> selectActiveVariantsByExpIds(@Param("expIds") List<String> expIds);
}