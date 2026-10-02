package com.linen.laundry.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.Data;

/**
 * 盘点保存结果：差异行数、差异件数合计、损益金额。
 */
@Data
public class CountResult {

    private Integer changedCount;
    private Integer diffQty;
    private BigDecimal gainLoss;
    private List<String> warnings;
}
