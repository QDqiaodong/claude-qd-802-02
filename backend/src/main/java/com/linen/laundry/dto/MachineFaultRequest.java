package com.linen.laundry.dto;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 故障报修：故障描述、维修人、费用。
 */
@Data
public class MachineFaultRequest {

    private String faultDesc;
    private String repairMan;
    private BigDecimal cost;
}
