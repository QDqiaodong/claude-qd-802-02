package com.linen.laundry.domain;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 复洗单：处置人员为隔离批次发起复洗，必须新建可追溯的复洗批次并关联原批次。
 * active_flag（数据库生成列）+ UNIQUE(isolation_id, active_flag) 保证同一隔离批次至多一份有效复洗。
 */
@Data
public class RewashOrder {

    private Long id;
    private String rewashNo;
    private Long isolationId;
    private Long eventId;
    private Long sourceBatchId;
    private Long rewashBatchId;
    private Long machineId;
    private String operator;
    private String reason;
    /** ACTIVE 在产 / DONE 完成 / FAILED 失败 / DELETED 已删除 */
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;

    // ------- 非表字段（查询拼装） -------
    private String sourceBatchNo;
    private String rewashBatchNo;
    private String machineName;
    private String machineCode;
    private String statusName;
    /** 复洗批次当前工序名（在产时展示进度） */
    private String rewashCurrentStepName;
    private Integer rewashCurrentStepOrder;
    private String rewashBatchStatus;
}
