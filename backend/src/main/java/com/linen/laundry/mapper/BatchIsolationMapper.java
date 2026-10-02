package com.linen.laundry.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.linen.laundry.domain.BatchIsolation;

/**
 * 批次隔离记录：事件详情逐条展示命中原因，并嵌套带出复洗单。
 */
@Mapper
public interface BatchIsolationMapper {

    @Select("""
            SELECT i.*,
                   b.batch_no           AS batch_no,
                   c.name               AS category_name,
                   b.weight_kg          AS weight_kg,
                   b.current_step       AS current_step,
                   b.current_step_order AS current_step_order,
                   b.status             AS batch_status,
                   m.name               AS machine_name,
                   m.code               AS machine_code
              FROM batch_isolation i
              JOIN wash_batch b        ON b.id = i.batch_id
              LEFT JOIN linen_category c ON c.id = b.category_id
              LEFT JOIN machine m      ON m.id = i.hit_machine_id
             WHERE i.event_id = #{eventId}
             ORDER BY i.id
            """)
    @Results(id = "isolationMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "eventId", column = "event_id"),
            @Result(property = "batchId", column = "batch_id"),
            @Result(property = "hitStepCode", column = "hit_step_code"),
            @Result(property = "hitStepName", column = "hit_step_name"),
            @Result(property = "hitMachineId", column = "hit_machine_id"),
            @Result(property = "hitOverlap", column = "hit_overlap"),
            @Result(property = "hitReason", column = "hit_reason"),
            @Result(property = "status", column = "status"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "releasedAt", column = "released_at"),
            @Result(property = "batchNo", column = "batch_no"),
            @Result(property = "categoryName", column = "category_name"),
            @Result(property = "weightKg", column = "weight_kg"),
            @Result(property = "currentStep", column = "current_step"),
            @Result(property = "currentStepOrder", column = "current_step_order"),
            @Result(property = "batchStatus", column = "batch_status"),
            @Result(property = "machineName", column = "machine_name"),
            @Result(property = "machineCode", column = "machine_code"),
            @Result(property = "rewashOrders", column = "id",
                    many = @org.apache.ibatis.annotations.Many(
                            select = "com.linen.laundry.mapper.RewashOrderMapper.selectByIsolationId"))
    })
    List<BatchIsolation> selectByEventId(@Param("eventId") Long eventId);

    /** 悲观行锁：发起复洗 / 放行按隔离记录串行 */
    @Select("SELECT * FROM batch_isolation WHERE id = #{id} FOR UPDATE")
    BatchIsolation selectByIdForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM batch_isolation WHERE event_id = #{eventId} AND batch_id = #{batchId}")
    BatchIsolation find(@Param("eventId") Long eventId, @Param("batchId") Long batchId);

    /** 当前仍处于隔离中的记录（任意事件），用于批次推进闸门 */
    @Select("SELECT * FROM batch_isolation WHERE batch_id = #{batchId} AND status = 'ISOLATED' LIMIT 1")
    BatchIsolation findActiveByBatchId(@Param("batchId") Long batchId);

    @Insert("""
            INSERT INTO batch_isolation (event_id, batch_id, hit_step_code, hit_step_name, hit_machine_id,
                                         hit_overlap, hit_reason, status, created_at)
            VALUES (#{eventId}, #{batchId}, #{hitStepCode}, #{hitStepName}, #{hitMachineId},
                    #{hitOverlap}, #{hitReason}, #{status}, #{createdAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(BatchIsolation isolation);

    @Update("UPDATE batch_isolation SET status = 'RELEASED', released_at = #{releasedAt} WHERE id = #{id}")
    int markReleased(@Param("id") Long id, @Param("releasedAt") java.time.LocalDateTime releasedAt);
}
