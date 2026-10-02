package com.linen.laundry.dto;

import lombok.Data;

/**
 * 工序站位统计：当前停在各工序的批次数与重量。
 */
@Data
public class StepStat {

    private String stepCode;
    private String stepName;
    private Integer stepOrder;
    private Integer batchCount;
    private Integer totalWeight;
}
