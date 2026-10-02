package com.linen.laundry.dto;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 网点抽屉登记收发送件：脏件数 / 净件数 / 司机 / 车牌 / 短少赔付。
 */
@Data
public class PickupRegisterRequest {

    private Long outletId;
    private Integer dirtyQty;
    private Integer cleanQty;
    private String driver;
    private String plate;
    /** 短少赔付金额，短少时必须 > 0 */
    private BigDecimal compensation;
    private String remark;
}
