package com.linen.laundry.dto;

import java.util.List;

import lombok.Data;

/**
 * 配方草稿保存请求：草稿可反复整体覆盖保存；发布校验在 publish 时统一进行。
 */
@Data
public class RecipeDraftSaveRequest {

    /** 可承接设备类型（WASHER/DRYER/IRONER） */
    private List<String> machineTypes;
    /** 最大装载比例 % */
    private Integer maxLoadRatio;
    private String remark;
    /** 八道工序参数（草稿期允许不完整，发布时校验必须齐八道） */
    private List<StepPayload> steps;

    @Data
    public static class StepPayload {
        private String stepCode;
        private Integer targetTemp;
        private Integer tempTolerance;
        private Integer targetDetergentMl;
        private Integer detergentTolerance;
        /** 本工序适用设备类型 */
        private List<String> machineTypes;
    }
}
