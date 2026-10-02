package com.linen.laundry.domain;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 工序流转记录：预洗→主洗→漂洗→中和→脱水→烘干→烫平→折叠。
 */
@Data
public class BatchStep {

    private Long id;
    private Long batchId;
    private String stepCode;
    private String stepName;
    /** 工序号 1~8 */
    private Integer stepOrder;
    private Integer waterTemp;
    private Integer detergentMl;
    /** 本工序实际占用设备 id（污染命中依据） */
    private Long machineId;
    private String operator;
    /** DONE 已完成 / CURRENT 进行中 / PENDING 未开始 */
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
