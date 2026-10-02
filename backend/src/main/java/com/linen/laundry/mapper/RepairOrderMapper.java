package com.linen.laundry.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.linen.laundry.domain.RepairOrder;

/**
 * 设备维修单。
 */
@Mapper
public interface RepairOrderMapper {

    /** 供设备 @Many 嵌套调用 */
    @Select("SELECT * FROM repair_order WHERE machine_id = #{machineId} ORDER BY report_time DESC LIMIT 5")
    List<RepairOrder> selectByMachineId(@Param("machineId") Long machineId);

    @Select("""
            SELECT r.*, m.name AS machine_name, m.code AS machine_code
              FROM repair_order r
              LEFT JOIN machine m ON m.id = r.machine_id
             ORDER BY FIELD(r.status, 'OPEN', 'DONE'), r.report_time DESC
            """)
    List<RepairOrder> selectAll();

    @Select("SELECT * FROM repair_order WHERE id = #{id}")
    RepairOrder selectById(@Param("id") Long id);

    @Insert("""
            INSERT INTO repair_order (machine_id, fault_desc, repair_man, cost,
                                      report_time, finish_time, status)
            VALUES (#{machineId}, #{faultDesc}, #{repairMan}, #{cost},
                    #{reportTime}, #{finishTime}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(RepairOrder repair);

    @Update("UPDATE repair_order SET status = 'DONE', finish_time = NOW() WHERE id = #{id}")
    int finish(@Param("id") Long id);

    @Delete("DELETE FROM repair_order WHERE id = #{id}")
    int delete(@Param("id") Long id);

    @Select("""
            SELECT COUNT(*)                                              AS repair_total,
                   SUM(CASE WHEN status = 'OPEN' THEN 1 ELSE 0 END)      AS open_count,
                   IFNULL(SUM(cost), 0)                                  AS cost_total
              FROM repair_order
            """)
    Map<String, Object> summary();
}
