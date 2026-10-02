package com.linen.laundry.dto;

import lombok.Data;

/**
 * 参数偏差复核结论：主管填写原因后选择按本批次放行或退回重做，结论只影响该批次。
 */
@Data
public class ReviewResolveRequest {

    /** RELEASE 按本批次放行 / REDO 退回重做 */
    private String decision;
    /** 复核原因（必填） */
    private String reason;
    /** 复核人（主管） */
    private String reviewer;
}
