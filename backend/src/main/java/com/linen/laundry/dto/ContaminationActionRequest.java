package com.linen.laundry.dto;

import lombok.Data;

/**
 * 确认污染事件 / 发起复洗 / 复核放行时携带的操作人与说明。
 */
@Data
public class ContaminationActionRequest {

    /** 操作人（确认人 / 复洗操作员 / 放行人） */
    private String operator;
    /** 复核放行备注；复洗时作为处置说明 */
    private String note;
    /** 发起复洗时可指定水洗机，不传则自动选择可用非故障设备 */
    private Long machineId;
}
