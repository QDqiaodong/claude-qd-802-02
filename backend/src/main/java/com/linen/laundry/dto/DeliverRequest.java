package com.linen.laundry.dto;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 送净登记：净件数不得大于脏件数，短少须登记赔付金额。
 */
@Data
public class DeliverRequest {

    private Integer cleanQty;
    private BigDecimal compensation;
    private String driver;
    private String plate;
    private String remark;
}
