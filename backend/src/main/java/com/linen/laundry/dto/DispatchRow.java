package com.linen.laundry.dto;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 配送看板行：按司机 / 车牌聚合指定自然日（默认当天）的收送量。
 * 口径：收脏单量 / 脏件按收脏时间，送净单量 / 净件 / 短少 / 赔付按送净时间。
 */
@Data
public class DispatchRow {

    private String driver;
    private String plate;
    /** 当日收脏单量（当日收当日送只算一单，与送净单量互不为倍数） */
    private Integer pickupOrderCount;
    /** 当日送净单量 */
    private Integer deliverOrderCount;
    /** 当日有过收脏或送净动作的单量（当日收当日送不重复计） */
    private Integer orderCount;
    private Integer dirtyQty;
    private Integer cleanQty;
    private Integer shortageQty;
    private BigDecimal compensation;
    private String districts;
}
