package com.linen.laundry.domain;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

/**
 * 医院布草污染事件：登记设备、发现时间、影响起止时间与污染类型；
 * 确认后按“设备占用时段”命中批次并隔离，全部复洗完成后复核放行。
 */
@Data
public class ContaminationEvent {

    private Long id;
    private String eventNo;
    private Long machineId;
    /** INFECTIOUS / BLOOD / CHEMICAL / RADIOACTIVE / OTHER */
    private String contamType;
    private LocalDateTime foundTime;
    private LocalDateTime affectStart;
    private LocalDateTime affectEnd;
    private String description;
    private String reporter;
    /** DRAFT 待确认 / CONFIRMED 已确认隔离中 / RELEASED 已复核放行 */
    private String status;
    private String confirmedBy;
    private LocalDateTime confirmedAt;
    private String releasedBy;
    private LocalDateTime releasedAt;
    private String releaseNote;
    private LocalDateTime createdAt;

    // ------- 非表字段（查询拼装） -------
    private String machineName;
    private String machineCode;
    private String contamTypeName;
    private String statusName;
    /** 命中隔离记录（嵌套 @Many 带出，含复洗单） */
    private List<BatchIsolation> isolations;
    /** 命中批次数 */
    private Integer hitCount;
    /** 待处置（隔离中）批次数 */
    private Integer pendingCount;
    /** 已放行批次数 */
    private Integer releasedCount;
    /** 复洗完成批次数 */
    private Integer rewashedCount;
}
