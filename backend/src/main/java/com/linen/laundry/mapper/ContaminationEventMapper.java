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

import com.linen.laundry.domain.ContaminationEvent;

/**
 * 医院布草污染事件：列表 / 详情带出隔离记录（嵌套 @Many）与设备名称。
 */
@Mapper
public interface ContaminationEventMapper {

    @Select("""
            SELECT e.*,
                   m.name AS machine_name,
                   m.code AS machine_code,
                   (SELECT COUNT(*) FROM batch_isolation i WHERE i.event_id = e.id) AS hit_count,
                   (SELECT COUNT(*) FROM batch_isolation i
                     WHERE i.event_id = e.id AND i.status = 'ISOLATED') AS pending_count,
                   (SELECT COUNT(*) FROM batch_isolation i
                     WHERE i.event_id = e.id AND i.status = 'RELEASED') AS released_count,
                   (SELECT COUNT(*) FROM batch_isolation i
                     WHERE i.event_id = e.id
                       AND EXISTS (SELECT 1 FROM rewash_order r
                                    WHERE r.isolation_id = i.id AND r.status = 'DONE')) AS rewashed_count
              FROM contamination_event e
              LEFT JOIN machine m ON m.id = e.machine_id
             ORDER BY FIELD(e.status, 'CONFIRMED', 'DRAFT', 'RELEASED'), e.found_time DESC, e.id DESC
            """)
    @Results(id = "eventMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "eventNo", column = "event_no"),
            @Result(property = "machineId", column = "machine_id"),
            @Result(property = "contamType", column = "contam_type"),
            @Result(property = "foundTime", column = "found_time"),
            @Result(property = "affectStart", column = "affect_start"),
            @Result(property = "affectEnd", column = "affect_end"),
            @Result(property = "description", column = "description"),
            @Result(property = "reporter", column = "reporter"),
            @Result(property = "status", column = "status"),
            @Result(property = "confirmedBy", column = "confirmed_by"),
            @Result(property = "confirmedAt", column = "confirmed_at"),
            @Result(property = "releasedBy", column = "released_by"),
            @Result(property = "releasedAt", column = "released_at"),
            @Result(property = "releaseNote", column = "release_note"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "machineName", column = "machine_name"),
            @Result(property = "machineCode", column = "machine_code"),
            @Result(property = "hitCount", column = "hit_count"),
            @Result(property = "pendingCount", column = "pending_count"),
            @Result(property = "releasedCount", column = "released_count"),
            @Result(property = "rewashedCount", column = "rewashed_count"),
            @Result(property = "isolations", column = "id",
                    many = @org.apache.ibatis.annotations.Many(
                            select = "com.linen.laundry.mapper.BatchIsolationMapper.selectByEventId"))
    })
    List<ContaminationEvent> selectAll();

    @Select("""
            SELECT e.*,
                   m.name AS machine_name,
                   m.code AS machine_code,
                   (SELECT COUNT(*) FROM batch_isolation i WHERE i.event_id = e.id) AS hit_count,
                   (SELECT COUNT(*) FROM batch_isolation i
                     WHERE i.event_id = e.id AND i.status = 'ISOLATED') AS pending_count,
                   (SELECT COUNT(*) FROM batch_isolation i
                     WHERE i.event_id = e.id AND i.status = 'RELEASED') AS released_count,
                   (SELECT COUNT(*) FROM batch_isolation i
                     WHERE i.event_id = e.id
                       AND EXISTS (SELECT 1 FROM rewash_order r
                                    WHERE r.isolation_id = i.id AND r.status = 'DONE')) AS rewashed_count
              FROM contamination_event e
              LEFT JOIN machine m ON m.id = e.machine_id
             WHERE e.id = #{id}
            """)
    @ResultMap("eventMap")
    ContaminationEvent selectById(@Param("id") Long id);

    /** 悲观行锁：确认 / 放行等状态流转必须先锁事件行，串行化同一事件的并发操作 */
    @Select("SELECT * FROM contamination_event WHERE id = #{id} FOR UPDATE")
    @ResultMap("eventMap")
    ContaminationEvent selectByIdForUpdate(@Param("id") Long id);

    @Insert("""
            INSERT INTO contamination_event (event_no, machine_id, contam_type, found_time, affect_start, affect_end,
                                             description, reporter, status, created_at)
            VALUES (#{eventNo}, #{machineId}, #{contamType}, #{foundTime}, #{affectStart}, #{affectEnd},
                    #{description}, #{reporter}, #{status}, #{createdAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(ContaminationEvent event);

    /** 用自增主键回填规范事件号（保证并发下也不撞号） */
    @Update("UPDATE contamination_event SET event_no = #{eventNo} WHERE id = #{id}")
    int updateEventNo(@Param("id") Long id, @Param("eventNo") String eventNo);

    @Update("""
            UPDATE contamination_event
               SET status = #{status},
                   confirmed_by = #{confirmedBy},
                   confirmed_at = #{confirmedAt},
                   released_by = #{releasedBy},
                   released_at = #{releasedAt},
                   release_note = #{releaseNote}
             WHERE id = #{id}
            """)
    int updateStatus(ContaminationEvent event);

    /** CAS 守卫：仅当事件仍处于期望状态时才流转，配合行锁双保险，防止并发提前放行 */
    @Update("""
            UPDATE contamination_event
               SET status = #{toStatus},
                   confirmed_by = #{confirmedBy},
                   confirmed_at = #{confirmedAt},
                   released_by = #{releasedBy},
                   released_at = #{releasedAt},
                   release_note = #{releaseNote}
             WHERE id = #{id} AND status = #{expectStatus}
            """)
    int compareAndSetStatus(@Param("id") Long id,
                            @Param("expectStatus") String expectStatus,
                            @Param("toStatus") String toStatus,
                            @Param("confirmedBy") String confirmedBy,
                            @Param("confirmedAt") java.time.LocalDateTime confirmedAt,
                            @Param("releasedBy") String releasedBy,
                            @Param("releasedAt") java.time.LocalDateTime releasedAt,
                            @Param("releaseNote") String releaseNote);
}
