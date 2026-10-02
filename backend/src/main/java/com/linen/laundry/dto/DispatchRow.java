package com.linen.laundry.dto;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 配送看板行：按司机 / 车牌聚合当日收送量。
 */
@Data
public class DispatchRow {

    private String driver;
    private String plate;
    private Integer orderCount;
    private Integer dirtyQty;
    private Integer cleanQty;
    private Integer shortageQty;
    private BigDecimal compensation;
    private String districts;
}
