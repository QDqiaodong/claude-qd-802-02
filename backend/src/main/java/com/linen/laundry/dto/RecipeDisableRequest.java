package com.linen.laundry.dto;

import lombok.Data;

/**
 * 停用当前配方版本：必须先选定接替版本，否则该品类新批次无法排产。
 */
@Data
public class RecipeDisableRequest {

    /** 接替版本 id（同品类的草稿或已停用版本；草稿须通过发布校验） */
    private Long successorVersionId;
    private String operator;
}
