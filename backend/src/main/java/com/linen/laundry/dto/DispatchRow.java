package com.linen.laundry.dto;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 配送看板行：指定日期内按司机 / 车牌聚合的收送量。
 * 口径与收送单日汇总一致：脏件按收脏时间、净件 / 短少 / 赔付按送净时间归属当日。
 */
@Data
public class DispatchRow {

    private String driver;
    private String plate;
    /** 当日涉及的收送单数（收脏或送净任一发生在当日，去重计 1 单） */
    private Integer orderCount;
    /** 当日收脏单数 */
    private Integer pickupOrderCount;
    /** 当日送净单数 */
    private Integer deliverOrderCount;
    /** 当日收脏件数（按 pickup_time） */
    private Integer dirtyQty;
    /** 当日送净件数（按 deliver_time） */
    private Integer cleanQty;
    /** 当日短少件数（按送净时间） */
    private Integer shortageQty;
    /** 当日赔付金额（按送净时间） */
    private BigDecimal compensation;
    private String districts;
}
