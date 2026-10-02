package com.linen.laundry.dto;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 新建洗涤批次（排产）。
 */
@Data
public class BatchCreateRequest {

    private Long categoryId;
    private BigDecimal weightKg;
    private Long machineId;
    private Integer detergentMl;
    private Integer waterTemp;
    private String operator;
    private String remark;
}
