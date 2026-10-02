package com.linen.laundry.domain;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 批次参数偏差复核：推进工序时实际水温 / 洗涤剂超出批次快照容差，
 * 批次转 REVIEW 待复核并落本表；主管填原因后放行本批次或退回重做，结论只影响该批次。
 */
@Data
public class BatchDeviationReview {

    private Long id;
    private Long batchId;
    private String stepCode;
    private String stepName;
    /** 工序号 1~8 */
    private Integer stepOrder;
    /** 快照标准水温 ℃ */
    private Integer stdWaterTemp;
    /** 快照标准洗涤剂 ml */
    private Integer stdDetergentMl;
    /** 实际录入水温 ℃ */
    private Integer actualWaterTemp;
    /** 实际录入洗涤剂 ml */
    private Integer actualDetergentMl;
    /** 被拦截推进的目标工序编码；NULL = 收口完工 */
    private String targetStep;
    /** 原计划下一工序水温（放行时按此继续） */
    private Integer nextWaterTemp;
    /** 原计划下一工序洗涤剂（放行时按此继续） */
    private Integer nextDetergentMl;
    private String operator;
    /** PENDING 待复核 / RELEASED 已放行 / REDONE 已退回重做 */
    private String status;
    /** 主管复核原因（必填） */
    private String reason;
    /** 复核人（主管） */
    private String reviewer;
    private LocalDateTime decidedAt;
    private LocalDateTime createdAt;

    // ------- 非表字段 -------
    private String batchNo;
    private String statusName;
}
