package com.linen.laundry.dto;

import lombok.Data;

/**
 * 新建配方草稿：可从既有版本复制（baseVersionId），也可从默认工艺参数空白起步。
 */
@Data
public class RecipeDraftCreateRequest {

    private Long categoryId;
    /** 复制来源版本 id；为空则按默认参数生成八道工序 */
    private Long baseVersionId;
    private String createdBy;
}
