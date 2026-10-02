package com.linen.laundry.dto;

import lombok.Data;

/**
 * 配方版本操作（发布 / 复制等）请求：仅需操作人。
 */
@Data
public class RecipeActionRequest {

    private String operator;
}
