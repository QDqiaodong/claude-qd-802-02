package com.linen.laundry.domain;

import java.math.BigDecimal;
import java.util.List;

import lombok.Data;

/**
 * 布草品类台账：床单 / 被套 / 枕套 / 浴巾 / 台布 / 工服。
 */
@Data
public class LinenCategory {

    private Long id;
    private String code;
    private String name;
    /** 规格，如 1.5m×2.0m */
    private String spec;
    /** 洗涤单价（元/件） */
    private BigDecimal unitPrice;
    /** 在库数（净布草） */
    private Integer stockQty;
    /** 报废率 % */
    private BigDecimal scrapRate;
    /** 最低周转量 */
    private Integer minTurnover;
    /** 归属：HOTEL / HOSPITAL / COMMON */
    private String ownerType;
    private String remark;

    // ------- 非表字段 -------
    /** 周转缺口 = max(0, 最低周转量 - 在库数) */
    private Integer gap;
    /** 在库金额 */
    private BigDecimal stockAmount;
    /** 近期洗涤批次（嵌套 @Many 带出） */
    private List<WashBatch> recentBatches;
}
