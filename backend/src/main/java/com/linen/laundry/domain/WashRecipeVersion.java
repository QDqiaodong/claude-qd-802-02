package com.linen.laundry.domain;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

/**
 * 洗涤配方版本：按布草品类维护预洗至折叠八道工序的标准参数。
 * DRAFT 草稿（可反复调整）→ PUBLISHED 当前生效（不可原地改写，只能复制生成新版本）→ DISABLED 已停用。
 * 每个品类同时最多一个 PUBLISHED（数据库生成列 + 唯一索引兜底）。
 */
@Data
public class WashRecipeVersion {

    private Long id;
    private Long categoryId;
    /** 品类内版本号，从 1 递增 */
    private Integer versionNo;
    /** DRAFT 草稿 / PUBLISHED 当前生效 / DISABLED 已停用 */
    private String status;
    /** 可承接设备类型，逗号分隔（WASHER/DRYER/IRONER） */
    private String machineTypes;
    /** 最大装载比例 %：批次重量 ≤ 设备容量 × 比例 */
    private Integer maxLoadRatio;
    /** 草稿基线版本 id（创建草稿时的当前生效版本，发布冲突检测依据） */
    private Long baseVersionId;
    private String remark;
    private String createdBy;
    private LocalDateTime createdAt;
    private String publishedBy;
    private LocalDateTime publishedAt;
    private String disabledBy;
    private LocalDateTime disabledAt;

    // ------- 非表字段 -------
    private String categoryName;
    private String statusName;
    /** 八道工序标准参数（嵌套 @Many 带出） */
    private List<WashRecipeStep> steps;
}
