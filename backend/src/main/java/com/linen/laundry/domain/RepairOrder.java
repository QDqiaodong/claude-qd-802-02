package com.linen.laundry.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 设备维修单。
 */
@Data
public class RepairOrder {

    private Long id;
    private Long machineId;
    private String faultDesc;
    private String repairMan;
    private BigDecimal cost;
    private LocalDateTime reportTime;
    private LocalDateTime finishTime;
    /** OPEN 待修 / DONE 已完工 */
    private String status;

    // ------- 非表字段 -------
    private String machineName;
    private String machineCode;
}
