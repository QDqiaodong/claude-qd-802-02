package com.linen.laundry.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

/**
 * 收送单：一次上门收脏 / 送净的完整记录。
 */
@Data
public class PickupOrder {

    private Long id;
    private String orderNo;
    private Long outletId;
    /** 收脏时间 */
    private LocalDateTime pickupTime;
    /** 送净时间 */
    private LocalDateTime deliverTime;
    /** 脏件数 */
    private Integer dirtyQty;
    /** 净件数 */
    private Integer cleanQty;
    private String driver;
    private String plate;
    /** 短少件数 = 脏件数 - 净件数 */
    private Integer shortageQty;
    /** 赔付金额 */
    private BigDecimal compensation;
    /** PICKED 已收待送 / DELIVERED 已送达 / CLOSED 已结算 */
    private String status;
    private String remark;

    // ------- 非表字段 -------
    private String outletName;
    private String district;
    /** 收送单明细行（嵌套 @Many 带出） */
    private List<PickupOrderLine> lines;
    /** 所属网点（嵌套 @One 带出） */
    private Outlet outlet;
}
