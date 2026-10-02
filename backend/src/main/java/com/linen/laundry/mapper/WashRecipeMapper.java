package com.linen.laundry.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Many;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.linen.laundry.domain.WashRecipeStep;
import com.linen.laundry.domain.WashRecipeVersion;

/**
 * 洗涤配方版本：列表/详情用嵌套 @Many 带出八道工序参数。
 * 发布 / 停用 / 接替全部走 CAS 更新，唯一索引 uk_recipe_current(category_id, current_flag) 兜底并发。
 */
@Mapper
public interface WashRecipeMapper {

    @Select("""
            SELECT v.*, c.name AS category_name
              FROM wash_recipe_version v
              LEFT JOIN linen_category c ON c.id = v.category_id
             WHERE (#{categoryId} IS NULL OR v.category_id = #{categoryId})
             ORDER BY v.category_id, v.version_no DESC
            """)
    @Results(id = "recipeMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "categoryId", column = "category_id"),
            @Result(property = "versionNo", column = "version_no"),
            @Result(property = "status", column = "status"),
            @Result(property = "machineTypes", column = "machine_types"),
            @Result(property = "maxLoadRatio", column = "max_load_ratio"),
            @Result(property = "baseVersionId", column = "base_version_id"),
            @Result(property = "remark", column = "remark"),
            @Result(property = "createdBy", column = "created_by"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "publishedBy", column = "published_by"),
            @Result(property = "publishedAt", column = "published_at"),
            @Result(property = "disabledBy", column = "disabled_by"),
            @Result(property = "disabledAt", column = "disabled_at"),
            @Result(property = "categoryName", column = "category_name"),
            @Result(property = "steps", column = "id",
                    many = @Many(select = "com.linen.laundry.mapper.WashRecipeMapper.selectSteps"))
    })
    List<WashRecipeVersion> selectAll(@Param("categoryId") Long categoryId);

    @Select("""
            SELECT v.*, c.name AS category_name
              FROM wash_recipe_version v
              LEFT JOIN linen_category c ON c.id = v.category_id
             WHERE v.id = #{id}
            """)
    @ResultMap("recipeMap")
    WashRecipeVersion selectById(@Param("id") Long id);

    /** 品类当前生效版本（排产取配方用） */
    @Select("""
            SELECT v.*, c.name AS category_name
              FROM wash_recipe_version v
              LEFT JOIN linen_category c ON c.id = v.category_id
             WHERE v.category_id = #{categoryId} AND v.status = 'PUBLISHED'
             LIMIT 1
            """)
    @ResultMap("recipeMap")
    WashRecipeVersion findPublished(@Param("categoryId") Long categoryId);

    /** 悲观行锁：发布 / 停用 / 编辑草稿前锁定版本行 */
    @Select("SELECT * FROM wash_recipe_version WHERE id = #{id} FOR UPDATE")
    WashRecipeVersion selectByIdForUpdate(@Param("id") Long id);

    /** 嵌套 @Many：版本八道工序参数 */
    @Select("SELECT * FROM wash_recipe_step WHERE version_id = #{versionId} ORDER BY step_order")
    List<WashRecipeStep> selectSteps(@Param("versionId") Long versionId);

    @Insert("""
            INSERT INTO wash_recipe_version (category_id, version_no, status, machine_types, max_load_ratio,
                                             base_version_id, remark, created_by, created_at)
            VALUES (#{categoryId}, #{versionNo}, #{status}, #{machineTypes}, #{maxLoadRatio},
                    #{baseVersionId}, #{remark}, #{createdBy}, #{createdAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertVersion(WashRecipeVersion version);

    @Insert("""
            INSERT INTO wash_recipe_step (version_id, step_code, step_name, step_order, target_temp,
                                          temp_tolerance, target_detergent_ml, detergent_tolerance, machine_types)
            VALUES (#{versionId}, #{stepCode}, #{stepName}, #{stepOrder}, #{targetTemp},
                    #{tempTolerance}, #{targetDetergentMl}, #{detergentTolerance}, #{machineTypes})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertStep(WashRecipeStep step);

    /** 草稿整体覆盖保存（CAS：仅草稿可改写，发布后不可原地改） */
    @Update("""
            UPDATE wash_recipe_version
               SET machine_types = #{machineTypes}, max_load_ratio = #{maxLoadRatio}, remark = #{remark}
             WHERE id = #{id} AND status = 'DRAFT'
            """)
    int updateDraft(WashRecipeVersion version);

    @Delete("DELETE FROM wash_recipe_step WHERE version_id = #{versionId}")
    int deleteSteps(@Param("versionId") Long versionId);

    /** 仅草稿可删除（CAS） */
    @Delete("DELETE FROM wash_recipe_version WHERE id = #{id} AND status = 'DRAFT'")
    int deleteDraft(@Param("id") Long id);

    /** 品类内下一个版本号（调用方须先锁品类行串行化） */
    @Select("SELECT COALESCE(MAX(version_no), 0) + 1 FROM wash_recipe_version WHERE category_id = #{categoryId}")
    int nextVersionNo(@Param("categoryId") Long categoryId);

    /** 当前版本被接替 / 停用（CAS：仅当前生效可转停用） */
    @Update("""
            UPDATE wash_recipe_version
               SET status = 'DISABLED', disabled_by = #{operator}, disabled_at = NOW()
             WHERE id = #{id} AND status = 'PUBLISHED'
            """)
    int supersede(@Param("id") Long id, @Param("operator") String operator);

    /** 草稿发布为当前版本（CAS：仅草稿可发布） */
    @Update("""
            UPDATE wash_recipe_version
               SET status = 'PUBLISHED', published_by = #{operator}, published_at = NOW()
             WHERE id = #{id} AND status = 'DRAFT'
            """)
    int publishDraft(@Param("id") Long id, @Param("operator") String operator);

    /** 接替激活：草稿或已停用版本 → 当前生效（CAS） */
    @Update("""
            UPDATE wash_recipe_version
               SET status = 'PUBLISHED', published_by = #{operator}, published_at = NOW()
             WHERE id = #{id} AND status IN ('DRAFT', 'DISABLED')
            """)
    int activate(@Param("id") Long id, @Param("operator") String operator);
}
