package com.linen.laundry.dto;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 车间人员登记 / 编辑医院布草污染事件。
 */
@Data
public class ContaminationEventRequest {

    private Long machineId;
    /** INFECTIOUS / BLOOD / CHEMICAL / RADIOACTIVE / OTHER */
    private String contamType;
    private LocalDateTime foundTime;
    private LocalDateTime affectStart;
    private LocalDateTime affectEnd;
    private String description;
    private String reporter;
}
