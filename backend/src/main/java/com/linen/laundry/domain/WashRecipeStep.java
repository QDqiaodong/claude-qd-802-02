package com.linen.laundry.domain;

import lombok.Data;

/**
 * 配方版本工序参数：每个版本固定八道工序一行。
 */
@Data
public class WashRecipeStep {

    private Long id;
    private Long versionId;
    private String stepCode;
    private String stepName;
    /** 工序号 1~8 */
    private Integer stepOrder;
    /** 目标水温 ℃ */
    private Integer targetTemp;
    /** 水温容差 ±℃ */
    private Integer tempTolerance;
    /** 目标洗涤剂用量 ml */
    private Integer targetDetergentMl;
    /** 洗涤剂容差 ±ml */
    private Integer detergentTolerance;
    /** 本工序适用设备类型，逗号分隔；空 = 不占设备 */
    private String machineTypes;
}
