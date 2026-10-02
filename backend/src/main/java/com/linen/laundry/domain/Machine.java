package com.linen.laundry.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lombok.Data;

/**
 * 水洗机 / 烘干机 / 烫平机设备台账。
 */
@Data
public class Machine {

    private Long id;
    private String code;
    private String name;
    private String model;
    /** WASHER 水洗机 / DRYER 烘干机 / IRONER 烫平机 */
    private String machineType;
    /** 容量 kg */
    private BigDecimal capacityKg;
    /** RUNNING 运行 / IDLE 待机 / FAULT 故障 */
    private String status;
    /** 累计运行时长（小时） */
    private BigDecimal runHours;
    /** 车间位置 */
    private String location;
    private LocalDate lastMaintainAt;

    // ------- 非表字段 -------
    /** 未完工维修单数 */
    private Integer openRepairCount;
    /** 维修记录（嵌套 @Many 带出） */
    private List<RepairOrder> repairs;

    // ------- 污染追溯（非表字段，查询拼装） -------
    /** 该设备未放行的已确认污染事件数（灯墙显示污染来源） */
    private Integer activeContaminationCount;
    /** 最近一条未放行污染事件号 */
    private String activeEventNo;
    /** 最近一条未放行污染类型中文名 */
    private String activeContamTypeName;
    /** 最近一条未放行污染事件影响窗口文案 */
    private String activeAffectRange;
    /** 该设备上待处置（隔离中）批次数 */
    private Integer quarantinedBatchCount;
}
