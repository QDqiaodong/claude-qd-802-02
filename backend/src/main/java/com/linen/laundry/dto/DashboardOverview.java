package com.linen.laundry.dto;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 车间总览看板 KPI。
 */
@Data
public class DashboardOverview {

    private Integer outletCount;
    private Integer warningOutletCount;
    /** 今日收脏件数 */
    private Integer todayDirty;
    /** 今日送净件数 */
    private Integer todayClean;
    /** 待收脏布草总量 */
    private Integer pendingDirty;
    /** 在产批次 */
    private Integer runningBatch;
    /** 今日完工批次 */
    private Integer doneBatch;
    /** 污染隔离中批次 */
    private Integer quarantinedBatch;
    /** 未放行污染事件数 */
    private Integer activeContaminationCount;
    private Integer machineCount;
    private Integer faultMachineCount;
    /** 周转缺口品类数 */
    private Integer shortageCategoryCount;
    /** 今日短少赔付金额（按送净时间口径） */
    private BigDecimal compensationTotal;
}
