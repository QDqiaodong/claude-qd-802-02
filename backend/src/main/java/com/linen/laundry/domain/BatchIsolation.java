package com.linen.laundry.domain;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

/**
 * 批次隔离记录：一个污染事件命中一个批次仅一条（数据库 UNIQUE(event_id, batch_id) 兜底）。
 * hitReason 解释该批次“为什么被命中”。
 */
@Data
public class BatchIsolation {

    private Long id;
    private Long eventId;
    private Long batchId;
    /** 命中时批次停留工序 */
    private String hitStepCode;
    private String hitStepName;
    private Long hitMachineId;
    /** 与影响窗口重叠时长文案 */
    private String hitOverlap;
    /** 命中原因解释 */
    private String hitReason;
    /** ISOLATED 隔离中 / RELEASED 已放行 */
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime releasedAt;

    // ------- 非表字段（查询拼装） -------
    private String batchNo;
    private String categoryName;
    private java.math.BigDecimal weightKg;
    private String currentStep;
    private String currentStepName;
    private Integer currentStepOrder;
    /** 批次当前状态：RUNNING / DONE / QUARANTINED 等 */
    private String batchStatus;
    private String machineName;
    private String machineCode;
    private String statusName;
    /** 该隔离批次的复洗单（嵌套 @Many 带出，通常 0 或 1 条有效，历史可有 FAILED / DELETED） */
    private List<RewashOrder> rewashOrders;
}
