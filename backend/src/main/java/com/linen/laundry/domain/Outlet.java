package com.linen.laundry.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

/**
 * 客户网点（酒店 / 医院）：收送业务的起点。
 */
@Data
public class Outlet {

    private Long id;
    private String code;
    private String name;
    /** HOTEL 酒店 / HOSPITAL 医院 */
    private String outletType;
    private String district;
    private String contact;
    private String phone;
    /** MONTHLY 月结 / COUNT 计件 / CASH 现结 */
    private String settleType;
    /** 月结额度 */
    private BigDecimal creditLimit;
    /** 脏布草待收量 */
    private Integer dirtyPending;
    /** 待收预警阈值 */
    private Integer warnThreshold;
    /** 平面示意图坐标（百分比 0~100） */
    private Integer mapX;
    private Integer mapY;
    private String status;
    private LocalDateTime createdAt;

    // ------- 非表字段：统计 / 关联 -------
    /** 当日已收脏件 */
    private Integer todayDirty;
    /** 当日已送净件 */
    private Integer todayClean;
    /** 最近收送单（MyBatis 嵌套 @Many 带出） */
    private List<PickupOrder> recentOrders;
}
