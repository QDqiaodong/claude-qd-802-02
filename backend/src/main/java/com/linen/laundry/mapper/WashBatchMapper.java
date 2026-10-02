package com.linen.laundry.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Many;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.One;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.Update;

import com.linen.laundry.domain.BatchStep;
import com.linen.laundry.domain.LinenCategory;
import com.linen.laundry.domain.Machine;
import com.linen.laundry.domain.WashBatch;
import com.linen.laundry.dto.StepStat;

/**
 * 洗涤批次：一条查询带出工序流转记录（@Many）、品类（@One）与设备（@One）。
 * 批次冗余冻结所用配方版本号与完整快照（recipe_snapshot）：发布后内容不可原地改写，
 * 在产批次按建批时快照继续执行，不受后来发布 / 停用影响。
 */
@Mapper
public interface WashBatchMapper {

    @Select("""
            SELECT b.*,
                   c.name       AS category_name,
                   m.name       AS machine_name,
                   m.code       AS machine_code,
                   ROUND(b.current_step_order * 100.0 / 8, 0) AS progress,
                   (SELECT i.id FROM batch_isolation i
                     WHERE i.batch_id = b.id AND i.status = 'ISOLATED' LIMIT 1) AS isolation_id,
                   (SELECT e.id FROM batch_isolation i
                     JOIN contamination_event e ON e.id = i.event_id
                    WHERE i.batch_id = b.id AND i.status = 'ISOLATED' LIMIT 1) AS quarantine_event_id,
                   (SELECT e.event_no FROM batch_isolation i
                     JOIN contamination_event e ON e.id = i.event_id
                    WHERE i.batch_id = b.id AND i.status = 'ISOLATED' LIMIT 1) AS quarantine_event_no,
                   (SELECT i.hit_reason FROM batch_isolation i
                     WHERE i.batch_id = b.id AND i.status = 'ISOLATED' LIMIT 1) AS quarantine_reason,
                   (SELECT r.status FROM rewash_order r
                     WHERE r.rewash_batch_id = b.id LIMIT 1) AS rewash_status,
                   (SELECT sb.batch_no FROM rewash_order r
                     JOIN wash_batch sb ON sb.id = r.source_batch_id
                    WHERE r.rewash_batch_id = b.id LIMIT 1) AS source_batch_no,
                   (SELECT wb.batch_no FROM rewash_order r
                     JOIN wash_batch wb ON wb.id = r.rewash_batch_id
                     JOIN batch_isolation i ON i.id = r.isolation_id
                    WHERE r.source_batch_id = b.id AND r.status IN ('ACTIVE','DONE') LIMIT 1) AS rewash_batch_no
              FROM wash_batch b
              LEFT JOIN linen_category c ON c.id = b.category_id
              LEFT JOIN machine m        ON m.id = b.machine_id
             ORDER BY FIELD(b.status, 'QUARANTINED', 'REVIEW', 'RUNNING', 'HOLD', 'DONE'), b.start_time DESC
            """)
    @Results(id = "batchMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "batchNo", column = "batch_no"),
            @Result(property = "categoryId", column = "category_id"),
            @Result(property = "weightKg", column = "weight_kg"),
            @Result(property = "currentStep", column = "current_step"),
            @Result(property = "currentStepOrder", column = "current_step_order"),
            @Result(property = "detergentMl", column = "detergent_ml"),
            @Result(property = "waterTemp", column = "water_temp"),
            @Result(property = "machineId", column = "machine_id"),
            @Result(property = "startTime", column = "start_time"),
            @Result(property = "finishTime", column = "finish_time"),
            @Result(property = "rewashOrderId", column = "rewash_order_id"),
            @Result(property = "quarantineWasDone", column = "quarantine_was_done"),
            @Result(property = "recipeVersionId", column = "recipe_version_id"),
            @Result(property = "recipeVersionNo", column = "recipe_version_no"),
            @Result(property = "recipeSnapshot", column = "recipe_snapshot"),
            @Result(property = "categoryName", column = "category_name"),
            @Result(property = "machineName", column = "machine_name"),
            @Result(property = "machineCode", column = "machine_code"),
            @Result(property = "progress", column = "progress"),
            @Result(property = "isolationId", column = "isolation_id"),
            @Result(property = "quarantineEventId", column = "quarantine_event_id"),
            @Result(property = "quarantineEventNo", column = "quarantine_event_no"),
            @Result(property = "quarantineReason", column = "quarantine_reason"),
            @Result(property = "rewashStatus", column = "rewash_status"),
            @Result(property = "sourceBatchNo", column = "source_batch_no"),
            @Result(property = "rewashBatchNo", column = "rewash_batch_no"),
            @Result(property = "steps", column = "id",
                    many = @Many(select = "com.linen.laundry.mapper.WashBatchMapper.selectSteps")),
            @Result(property = "category", column = "category_id",
                    one = @One(select = "com.linen.laundry.mapper.LinenCategoryMapper.selectById")),
            @Result(property = "machine", column = "machine_id",
                    one = @One(select = "com.linen.laundry.mapper.MachineMapper.selectById"))
    })
    List<WashBatch> selectAll();

    /** 嵌套 @Many：批次工序流转记录 */
    @Select("SELECT * FROM batch_step WHERE batch_id = #{batchId} ORDER BY step_order")
    List<BatchStep> selectSteps(@Param("batchId") Long batchId);

    @Select("""
            SELECT b.*,
                   c.name AS category_name,
                   m.name AS machine_name,
                   m.code AS machine_code,
                   ROUND(b.current_step_order * 100.0 / 8, 0) AS progress,
                   (SELECT i.id FROM batch_isolation i
                     WHERE i.batch_id = b.id AND i.status = 'ISOLATED' LIMIT 1) AS isolation_id,
                   (SELECT e.id FROM batch_isolation i
                     JOIN contamination_event e ON e.id = i.event_id
                    WHERE i.batch_id = b.id AND i.status = 'ISOLATED' LIMIT 1) AS quarantine_event_id,
                   (SELECT e.event_no FROM batch_isolation i
                     JOIN contamination_event e ON e.id = i.event_id
                    WHERE i.batch_id = b.id AND i.status = 'ISOLATED' LIMIT 1) AS quarantine_event_no,
                   (SELECT i.hit_reason FROM batch_isolation i
                     WHERE i.batch_id = b.id AND i.status = 'ISOLATED' LIMIT 1) AS quarantine_reason,
                   (SELECT r.status FROM rewash_order r
                     WHERE r.rewash_batch_id = b.id LIMIT 1) AS rewash_status,
                   (SELECT sb.batch_no FROM rewash_order r
                     JOIN wash_batch sb ON sb.id = r.source_batch_id
                    WHERE r.rewash_batch_id = b.id LIMIT 1) AS source_batch_no,
                   (SELECT wb.batch_no FROM rewash_order r
                     JOIN wash_batch wb ON wb.id = r.rewash_batch_id
                     JOIN batch_isolation i ON i.id = r.isolation_id
                    WHERE r.source_batch_id = b.id AND r.status IN ('ACTIVE','DONE') LIMIT 1) AS rewash_batch_no
              FROM wash_batch b
              LEFT JOIN linen_category c ON c.id = b.category_id
              LEFT JOIN machine m        ON m.id = b.machine_id
             WHERE b.id = #{id}
            """)
    @ResultMap("batchMap")
    WashBatch selectById(@Param("id") Long id);

    /** 供品类 @Many 嵌套调用 */
    @Select("""
            SELECT b.*,
                   c.name AS category_name,
                   m.name AS machine_name,
                   m.code AS machine_code,
                   ROUND(b.current_step_order * 100.0 / 8, 0) AS progress,
                   (SELECT i.id FROM batch_isolation i
                     WHERE i.batch_id = b.id AND i.status = 'ISOLATED' LIMIT 1) AS isolation_id,
                   (SELECT e.event_no FROM batch_isolation i
                     JOIN contamination_event e ON e.id = i.event_id
                    WHERE i.batch_id = b.id AND i.status = 'ISOLATED' LIMIT 1) AS quarantine_event_no
              FROM wash_batch b
              LEFT JOIN linen_category c ON c.id = b.category_id
              LEFT JOIN machine m        ON m.id = b.machine_id
             WHERE b.category_id = #{categoryId}
             ORDER BY b.start_time DESC
             LIMIT 4
            """)
    @ResultMap("batchMap")
    List<WashBatch> selectByCategoryId(@Param("categoryId") Long categoryId);

    /** 隧道视图：动态条件（状态 / 停留工序 / 品类）走 @SelectProvider */
    @SelectProvider(type = WashBatchSqlProvider.class, method = "tunnel")
    @ResultMap("batchMap")
    List<WashBatch> tunnel(@Param("status") String status,
                           @Param("stepCode") String stepCode,
                           @Param("categoryId") Long categoryId);

    @Insert("""
            INSERT INTO wash_batch (batch_no, category_id, weight_kg, current_step, current_step_order,
                                    detergent_ml, water_temp, machine_id, operator, status,
                                    start_time, finish_time, rewash_order_id,
                                    recipe_version_id, recipe_version_no, recipe_snapshot, remark)
            VALUES (#{batchNo}, #{categoryId}, #{weightKg}, #{currentStep}, #{currentStepOrder},
                    #{detergentMl}, #{waterTemp}, #{machineId}, #{operator}, #{status},
                    #{startTime}, #{finishTime}, #{rewashOrderId},
                    #{recipeVersionId}, #{recipeVersionNo}, #{recipeSnapshot}, #{remark})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(WashBatch batch);

    /** 推进工序：更新当前工序与状态 */
    @Update("""
            UPDATE wash_batch
               SET current_step = #{currentStep}, current_step_order = #{currentStepOrder},
                   water_temp = IFNULL(#{waterTemp}, water_temp),
                   detergent_ml = IFNULL(#{detergentMl}, detergent_ml),
                   operator = IFNULL(#{operator}, operator),
                   status = #{status},
                   finish_time = #{finishTime}
             WHERE id = #{id}
            """)
    int advance(WashBatch batch);

    @Insert("""
            INSERT INTO batch_step (batch_id, step_code, step_name, step_order, water_temp,
                                    detergent_ml, machine_id, operator, status, start_time, end_time)
            VALUES (#{batchId}, #{stepCode}, #{stepName}, #{stepOrder}, #{waterTemp},
                    #{detergentMl}, #{machineId}, #{operator}, #{status}, #{startTime}, #{endTime})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertStep(BatchStep step);

    /** 把上一道工序收口为 DONE，并回填本工序实际录入值 */
    @Update("""
            UPDATE batch_step
               SET status = 'DONE', end_time = NOW(),
                   water_temp = IFNULL(#{waterTemp}, water_temp),
                   detergent_ml = IFNULL(#{detergentMl}, detergent_ml)
             WHERE batch_id = #{batchId} AND step_code = #{stepCode}
            """)
    int finishStep(@Param("batchId") Long batchId, @Param("stepCode") String stepCode,
                   @Param("waterTemp") Integer waterTemp, @Param("detergentMl") Integer detergentMl);

    /** 超差进入待复核：把实际录入值记到当前工序行（工序保持 CURRENT，等主管结论） */
    @Update("""
            UPDATE batch_step
               SET water_temp = IFNULL(#{waterTemp}, water_temp),
                   detergent_ml = IFNULL(#{detergentMl}, detergent_ml)
             WHERE batch_id = #{batchId} AND step_code = #{stepCode} AND status = 'CURRENT'
            """)
    int recordStepActuals(@Param("batchId") Long batchId, @Param("stepCode") String stepCode,
                          @Param("waterTemp") Integer waterTemp, @Param("detergentMl") Integer detergentMl);

    /** 批次状态直改（待复核 / 复核退回等在产状态间切换） */
    @Update("UPDATE wash_batch SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /** 超差待复核：冻结批次当前实际值并转 REVIEW */
    @Update("""
            UPDATE wash_batch
               SET water_temp = IFNULL(#{waterTemp}, water_temp),
                   detergent_ml = IFNULL(#{detergentMl}, detergent_ml),
                   status = #{status}
             WHERE id = #{id}
            """)
    int freezeForReview(WashBatch batch);

    @Delete("DELETE FROM batch_step WHERE batch_id = #{batchId}")
    int deleteSteps(@Param("batchId") Long batchId);

    @Delete("DELETE FROM wash_batch WHERE id = #{id}")
    int delete(@Param("id") Long id);

    /** 工序站位统计：当前停在各工序的在产（含待复核，非隔离）批次与重量 */
    @Select("""
            SELECT b.current_step                        AS step_code,
                   MIN(b.current_step_order)             AS step_order,
                   COUNT(*)                              AS batch_count,
                   IFNULL(ROUND(SUM(b.weight_kg), 0), 0) AS total_weight
              FROM wash_batch b
             WHERE b.status IN ('RUNNING', 'REVIEW')
             GROUP BY b.current_step
             ORDER BY step_order
            """)
    List<StepStat> stepStats();

    @Select("""
            SELECT COUNT(*)                                                   AS batch_total,
                   SUM(CASE WHEN status = 'RUNNING' THEN 1 ELSE 0 END)        AS running_batch,
                   SUM(CASE WHEN status = 'DONE' THEN 1 ELSE 0 END)           AS done_batch,
                   SUM(CASE WHEN status = 'QUARANTINED' THEN 1 ELSE 0 END)    AS quarantined_batch,
                   SUM(CASE WHEN status = 'REVIEW' THEN 1 ELSE 0 END)         AS review_batch,
                   IFNULL(ROUND(SUM(CASE WHEN status = 'RUNNING' THEN weight_kg ELSE 0 END), 0), 0) AS running_weight
              FROM wash_batch
            """)
    Map<String, Object> summary();

    /** 悲观行锁：推进 / 发起复洗等动作先锁批次，防止与隔离确认并发交叉 */
    @Select("SELECT * FROM wash_batch WHERE id = #{id} FOR UPDATE")
    WashBatch selectByIdForUpdate(@Param("id") Long id);

    /** 事件确认：批次转入污染隔离（不改变当前工序），并记录隔离前是否已完工 */
    @Update("UPDATE wash_batch SET status = 'QUARANTINED', quarantine_was_done = #{wasDone} "
            + "WHERE id = #{id} AND status <> 'QUARANTINED'")
    int markQuarantined(@Param("id") Long id, @Param("wasDone") Integer wasDone);

    /** 事件放行：隔离前尚未完工的批次解除隔离、恢复在产（停留工序不变，可继续推进）；
     *  隔离前已完工（hit_was_done=1）的批次保持 DONE，不回退到在产。 */
    @Update("UPDATE wash_batch SET status = IF(quarantine_was_done = 1, 'DONE', 'RUNNING'), "
            + "finish_time = IF(quarantine_was_done = 1, finish_time, NULL) "
            + "WHERE id = #{id} AND status = 'QUARANTINED'")
    int releaseQuarantine(@Param("id") Long id);

    /** 复洗失败 / 删除复洗：复洗批次留在污染隔离态，等待重新处置 */
    @Update("""
            UPDATE wash_batch SET status = 'QUARANTINED', finish_time = NOW()
             WHERE id = #{id} AND rewash_order_id IS NOT NULL
            """)
    int markRewashHeld(@Param("id") Long id);

    /** 是否存在由本批次派生的复洗记录（用于放行批次折叠完工时避免重复入库） */
    @Select("""
            SELECT COUNT(*) FROM rewash_order r
             WHERE r.source_batch_id = #{batchId} AND r.status = 'DONE'
            """)
    int countDoneRewashFromSource(@Param("batchId") Long batchId);

    /** 隔离中批次（用于隧道顶部待处置提示等） */
    @Select("SELECT COUNT(*) FROM wash_batch WHERE status = 'QUARANTINED'")
    int countQuarantined();

    /** 未放行（隔离中）的污染事件数，用于顶部 KPI */
    @Select("SELECT COUNT(*) FROM contamination_event WHERE status = 'CONFIRMED'")
    int countActiveContaminationEvents();
}
