package com.linen.laundry.domain;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 收送单明细行：按布草品类登记收发件数与短少。
 */
@Data
public class PickupOrderLine {

    private Long id;
    private Long orderId;
    private Long categoryId;
    private Integer dirtyQty;
    private Integer cleanQty;
    private Integer shortageQty;
    /** 短少赔付金额 */
    private BigDecimal lossAmount;

    // ------- 非表字段 -------
    private String categoryName;
    private String spec;
    private BigDecimal unitPrice;
}
