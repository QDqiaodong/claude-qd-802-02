package com.linen.laundry.dto;

import lombok.Data;

/**
 * 快速盘点行：品类 + 调整后在库数。
 */
@Data
public class CountItem {

    private Long categoryId;
    private Integer stockQty;
}
