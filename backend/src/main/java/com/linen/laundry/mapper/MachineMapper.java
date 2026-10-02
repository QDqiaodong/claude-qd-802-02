package com.linen.laundry.mapper;

import java.util.List;
import java.util.Map;

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

import com.linen.laundry.domain.Machine;
import com.linen.laundry.domain.RepairOrder;

/**
 * 水洗机 / 烘干机设备台账：列表带出维修记录（嵌套 @Many）与未完工维修单数。
 */
@Mapper
public interface MachineMapper {

    @Select("""
            SELECT m.*,
                   (SELECT COUNT(*) FROM repair_order r
                     WHERE r.machine_id = m.id AND r.status = 'OPEN') AS open_repair_count,
                   (SELECT COUNT(*) FROM contamination_event e
                     WHERE e.machine_id = m.id AND e.status = 'CONFIRMED') AS active_contamination_count,
                   (SELECT e.event_no FROM contamination_event e
                     WHERE e.machine_id = m.id AND e.status = 'CONFIRMED'
                     ORDER BY e.found_time DESC, e.id DESC LIMIT 1) AS active_event_no,
                   (SELECT e.contam_type FROM contamination_event e
                     WHERE e.machine_id = m.id AND e.status = 'CONFIRMED'
                     ORDER BY e.found_time DESC, e.id DESC LIMIT 1) AS active_contam_type,
                   (SELECT CONCAT(DATE_FORMAT(e.affect_start, '%H:%i'), '-', DATE_FORMAT(e.affect_end, '%H:%i'))
                      FROM contamination_event e
                     WHERE e.machine_id = m.id AND e.status = 'CONFIRMED'
                     ORDER BY e.found_time DESC, e.id DESC LIMIT 1) AS active_affect_range,
                   (SELECT COUNT(DISTINCT i.batch_id) FROM batch_isolation i
                     JOIN contamination_event e2 ON e2.id = i.event_id
                    WHERE e2.machine_id = m.id AND i.status = 'ISOLATED') AS quarantined_batch_count
              FROM machine m
             ORDER BY FIELD(m.status, 'FAULT', 'RUNNING', 'IDLE'),
                      active_contamination_count DESC, m.code
            """)
    @Results(id = "machineMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "code", column = "code"),
            @Result(property = "name", column = "name"),
            @Result(property = "machineType", column = "machine_type"),
            @Result(property = "capacityKg", column = "capacity_kg"),
            @Result(property = "runHours", column = "run_hours"),
            @Result(property = "lastMaintainAt", column = "last_maintain_at"),
            @Result(property = "openRepairCount", column = "open_repair_count"),
            @Result(property = "activeContaminationCount", column = "active_contamination_count"),
            @Result(property = "activeEventNo", column = "active_event_no"),
            @Result(property = "activeContamTypeName", column = "active_contam_type"),
            @Result(property = "activeAffectRange", column = "active_affect_range"),
            @Result(property = "quarantinedBatchCount", column = "quarantined_batch_count"),
            @Result(property = "repairs", column = "id",
                    many = @Many(select = "com.linen.laundry.mapper.RepairOrderMapper.selectByMachineId"))
    })
    List<Machine> selectAll();

    @Select("SELECT * FROM machine WHERE id = #{id}")
    @ResultMap("machineMap")
    Machine selectById(@Param("id") Long id);

    @Select("SELECT * FROM machine WHERE status = #{status} ORDER BY code")
    @ResultMap("machineMap")
    List<Machine> selectByStatus(@Param("status") String status);

    @Select("SELECT * FROM machine WHERE machine_type = #{machineType} AND status <> 'FAULT' ORDER BY capacity_kg DESC")
    @ResultMap("machineMap")
    List<Machine> selectAvailable(@Param("machineType") String machineType);

    @Insert("""
            INSERT INTO machine (code, name, model, machine_type, capacity_kg,
                                 status, run_hours, location, last_maintain_at)
            VALUES (#{code}, #{name}, #{model}, #{machineType}, #{capacityKg},
                    #{status}, #{runHours}, #{location}, #{lastMaintainAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(Machine machine);

    @Update("""
            UPDATE machine
               SET name = #{name}, model = #{model}, machine_type = #{machineType},
                   capacity_kg = #{capacityKg}, location = #{location},
                   last_maintain_at = #{lastMaintainAt}, run_hours = #{runHours}
             WHERE id = #{id}
            """)
    int update(Machine machine);

    /** 灯墙点灯轮转状态 */
    @Update("UPDATE machine SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /** 完工累计运行时长 */
    @Update("UPDATE machine SET run_hours = run_hours + #{hours} WHERE id = #{id}")
    int addRunHours(@Param("id") Long id, @Param("hours") java.math.BigDecimal hours);

    @Delete("DELETE FROM machine WHERE id = #{id}")
    int delete(@Param("id") Long id);

    @Select("""
            SELECT COUNT(*)                                                    AS machine_total,
                   SUM(CASE WHEN status = 'RUNNING' THEN 1 ELSE 0 END)         AS running_count,
                   SUM(CASE WHEN status = 'IDLE' THEN 1 ELSE 0 END)            AS idle_count,
                   SUM(CASE WHEN status = 'FAULT' THEN 1 ELSE 0 END)           AS fault_count,
                   IFNULL(ROUND(SUM(run_hours), 1), 0)                         AS run_hours_total
              FROM machine
            """)
    Map<String, Object> summary();
}
