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

import com.linen.laundry.domain.RewashOrder;

/**
 * 复洗单：隔离批次与复洗新批次的可追溯关联。
 */
@Mapper
public interface RewashOrderMapper {

    String COLS = """
            SELECT r.id, r.rewash_no, r.isolation_id, r.event_id, r.source_batch_id, r.rewash_batch_id,
                   r.machine_id, r.operator, r.reason, r.status, r.created_at, r.finished_at,
                   sb.batch_no          AS source_batch_no,
                   wb.batch_no          AS rewash_batch_no,
                   wb.current_step      AS rewash_current_step,
                   wb.current_step_order AS rewash_current_step_order,
                   wb.status            AS rewash_batch_status,
                   m.name               AS machine_name,
                   m.code               AS machine_code
              FROM rewash_order r
              LEFT JOIN wash_batch sb ON sb.id = r.source_batch_id
              LEFT JOIN wash_batch wb ON wb.id = r.rewash_batch_id
              LEFT JOIN machine m     ON m.id = r.machine_id
            """;

    @Results(id = "rewashMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "rewashNo", column = "rewash_no"),
            @Result(property = "isolationId", column = "isolation_id"),
            @Result(property = "eventId", column = "event_id"),
            @Result(property = "sourceBatchId", column = "source_batch_id"),
            @Result(property = "rewashBatchId", column = "rewash_batch_id"),
            @Result(property = "machineId", column = "machine_id"),
            @Result(property = "operator", column = "operator"),
            @Result(property = "reason", column = "reason"),
            @Result(property = "status", column = "status"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "finishedAt", column = "finished_at"),
            @Result(property = "sourceBatchNo", column = "source_batch_no"),
            @Result(property = "rewashBatchNo", column = "rewash_batch_no"),
            @Result(property = "rewashCurrentStepName", column = "rewash_current_step"),
            @Result(property = "rewashCurrentStepOrder", column = "rewash_current_step_order"),
            @Result(property = "rewashBatchStatus", column = "rewash_batch_status"),
            @Result(property = "machineName", column = "machine_name"),
            @Result(property = "machineCode", column = "machine_code")
    })
    @Select(COLS + " WHERE r.isolation_id = #{isolationId} ORDER BY r.id")
    List<RewashOrder> selectByIsolationId(@Param("isolationId") Long isolationId);

    @Select(COLS + " WHERE r.event_id = #{eventId} ORDER BY r.id")
    @ResultMap("rewashMap")
    List<RewashOrder> selectByEventId(@Param("eventId") Long eventId);

    @Select(COLS + " WHERE r.id = #{id}")
    @ResultMap("rewashMap")
    RewashOrder selectById(@Param("id") Long id);

    /** 复洗批次推进完工时回写复洗单 */
    @Select(COLS + " WHERE r.rewash_batch_id = #{batchId}")
    @ResultMap("rewashMap")
    RewashOrder findByRewashBatchId(@Param("batchId") Long batchId);

    /** 唯一一份有效复洗（ACTIVE / DONE）；active_flag 唯一索引保证至多一条 */
    @Select(COLS + " WHERE r.isolation_id = #{isolationId} AND r.status IN ('ACTIVE','DONE') LIMIT 1")
    @ResultMap("rewashMap")
    RewashOrder findActiveByIsolationId(@Param("isolationId") Long isolationId);

    @Insert("""
            INSERT INTO rewash_order (rewash_no, isolation_id, event_id, source_batch_id, rewash_batch_id,
                                      machine_id, operator, reason, status, created_at)
            VALUES (#{rewashNo}, #{isolationId}, #{eventId}, #{sourceBatchId}, #{rewashBatchId},
                    #{machineId}, #{operator}, #{reason}, #{status}, #{createdAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(RewashOrder rewash);

    /** 用自增主键回填规范复洗单号（保证并发下也不撞号） */
    @Update("UPDATE rewash_order SET rewash_no = #{rewashNo} WHERE id = #{id}")
    int updateRewashNo(@Param("id") Long id, @Param("rewashNo") String rewashNo);

    @Update("UPDATE rewash_order SET rewash_batch_id = #{rewashBatchId}, machine_id = #{machineId} WHERE id = #{id}")
    int bindBatch(@Param("id") Long id,
                  @Param("rewashBatchId") Long rewashBatchId,
                  @Param("machineId") Long machineId);

    /** 复洗批次完成：ACTIVE → DONE（CAS，仅在产可完成） */
    @Update("""
            UPDATE rewash_order SET status = 'DONE', finished_at = #{finishedAt}
             WHERE id = #{id} AND status = 'ACTIVE'
            """)
    int markDone(@Param("id") Long id, @Param("finishedAt") java.time.LocalDateTime finishedAt);

    /** 复洗失败：ACTIVE → FAILED（active_flag 自动置 NULL，允许重新发起） */
    @Update("""
            UPDATE rewash_order SET status = 'FAILED', finished_at = #{finishedAt}
             WHERE id = #{id} AND status = 'ACTIVE'
            """)
    int markFailed(@Param("id") Long id, @Param("finishedAt") java.time.LocalDateTime finishedAt);

    /** 删除复洗：ACTIVE / FAILED → DELETED（active_flag 自动置 NULL，允许重新发起；记录保留可追溯） */
    @Update("""
            UPDATE rewash_order SET status = 'DELETED', finished_at = #{finishedAt}
             WHERE id = #{id} AND status IN ('ACTIVE','FAILED')
            """)
    int markDeleted(@Param("id") Long id, @Param("finishedAt") java.time.LocalDateTime finishedAt);
}
