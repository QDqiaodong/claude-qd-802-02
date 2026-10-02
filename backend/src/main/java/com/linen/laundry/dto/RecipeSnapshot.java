package com.linen.laundry.dto;

import java.util.List;

import com.linen.laundry.domain.WashRecipeStep;
import com.linen.laundry.domain.WashRecipeVersion;

import lombok.Data;

/**
 * 批次配方快照：建批时从当前配方版本冻结，序列化为 JSON 存 wash_batch.recipe_snapshot。
 * 之后配方发布 / 停用 / 复制新版本都不再影响已建批次——批次永远按本快照执行与复核。
 */
@Data
public class RecipeSnapshot {

    private Long versionId;
    private Integer versionNo;
    private Long categoryId;
    /** 可承接设备类型，逗号分隔 */
    private String machineTypes;
    /** 最大装载比例 % */
    private Integer maxLoadRatio;
    /** 八道工序标准参数 */
    private List<StepStd> steps;

    /** 按工序编码取标准参数 */
    public StepStd stdOf(String stepCode) {
        if (steps == null || stepCode == null) {
            return null;
        }
        return steps.stream().filter(s -> stepCode.equals(s.getStepCode())).findFirst().orElse(null);
    }

    /** 由配方版本 + 工序参数构建快照 */
    public static RecipeSnapshot of(WashRecipeVersion version, List<WashRecipeStep> steps) {
        RecipeSnapshot snapshot = new RecipeSnapshot();
        snapshot.setVersionId(version.getId());
        snapshot.setVersionNo(version.getVersionNo());
        snapshot.setCategoryId(version.getCategoryId());
        snapshot.setMachineTypes(version.getMachineTypes());
        snapshot.setMaxLoadRatio(version.getMaxLoadRatio());
        snapshot.setSteps(steps.stream().map(StepStd::of).toList());
        return snapshot;
    }

    /**
     * 单道工序的标准参数（目标值 + 容差 + 适用设备类型）。
     */
    @Data
    public static class StepStd {

        private String stepCode;
        private String stepName;
        private Integer stepOrder;
        private Integer targetTemp;
        private Integer tempTolerance;
        private Integer targetDetergentMl;
        private Integer detergentTolerance;
        private String machineTypes;

        public static StepStd of(WashRecipeStep step) {
            StepStd std = new StepStd();
            std.setStepCode(step.getStepCode());
            std.setStepName(step.getStepName());
            std.setStepOrder(step.getStepOrder());
            std.setTargetTemp(step.getTargetTemp());
            std.setTempTolerance(step.getTempTolerance());
            std.setTargetDetergentMl(step.getTargetDetergentMl());
            std.setDetergentTolerance(step.getDetergentTolerance());
            std.setMachineTypes(step.getMachineTypes());
            return std;
        }
    }
}
