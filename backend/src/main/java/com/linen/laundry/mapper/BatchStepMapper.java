package com.linen.laundry.mapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 工序占用设备查询：污染事件确认时，按“设备 + 影响时段”反查窗口内实际经过该设备的批次。
 * 每条结果是一次“批次 × 工序”的设备占用重叠；同一批次多道工序命中时由 service 聚合解释。
 */
@Mapper
public interface BatchStepMapper {

    /**
     * 路径 A：批次在某道工序实际占用了污染设备，且该工序的时间区间与影响窗口严格重叠
     * （s.start_time &lt; end AND s.end_time &gt; start；进行中工序 end_time 为空视为持续到影响窗口结束）。
     */
    @Select("""
            SELECT s.batch_id                AS batch_id,
                   s.step_code               AS step_code,
                   s.step_name               AS step_name,
                   s.step_order              AS step_order,
                   CAST(s.start_time AS DATETIME)                       AS step_start,
                   CAST(COALESCE(s.end_time, NOW()) AS DATETIME)        AS step_end,
                   'STEP'                    AS match_source
              FROM batch_step s
             WHERE s.machine_id = #{machineId}
               AND s.start_time < #{end}
               AND COALESCE(s.end_time, NOW()) > #{start}
            """)
    List<Map<String, Object>> findOverlappingSteps(@Param("machineId") Long machineId,
                                                   @Param("start") LocalDateTime start,
                                                   @Param("end") LocalDateTime end);

    /**
     * 路径 B（兜底）：批次当时绑定在污染设备上、但尚无带设备的工序与窗口重叠
     * （例如刚排产、首道工序未回填设备），同样视为命中，避免漏隔离。
     */
    @Select("""
            SELECT b.id                       AS batch_id,
                   b.current_step             AS step_code,
                   NULL                       AS step_name,
                   b.current_step_order       AS step_order,
                   CAST(b.start_time AS DATETIME)  AS step_start,
                   CAST(COALESCE(b.finish_time, NOW()) AS DATETIME) AS step_end,
                   'MACHINE'                  AS match_source
              FROM wash_batch b
             WHERE b.machine_id = #{machineId}
               AND b.status <> 'QUARANTINED'
               AND b.start_time < #{end}
               AND COALESCE(b.finish_time, NOW()) > #{start}
               AND NOT EXISTS (
                   SELECT 1 FROM batch_step s
                    WHERE s.machine_id = #{machineId}
                      AND s.batch_id = b.id
                      AND s.start_time < #{end}
                      AND COALESCE(s.end_time, NOW()) > #{start})
            """)
    List<Map<String, Object>> findMachineBoundBatches(@Param("machineId") Long machineId,
                                                      @Param("start") LocalDateTime start,
                                                      @Param("end") LocalDateTime end);
}
