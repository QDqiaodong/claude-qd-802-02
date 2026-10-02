package com.linen.laundry.dto;

import lombok.Data;

/**
 * 推进工序请求：目标工序必须正好是当前工序的下一道，不允许跳步。
 * actualWaterTemp / actualDetergentMl 为「当前将收口工序」的实际录入值，
 * 超出批次配方快照容差时批次转待复核，不直接推进。
 */
@Data
public class BatchAdvanceRequest {

    /** 目标工序编码；留空表示推进到下一道 */
    private String targetStep;
    private String operator;
    /** 下一工序计划水温（留空按配方标准带入） */
    private Integer waterTemp;
    /** 下一工序计划洗涤剂（留空按配方标准带入） */
    private Integer detergentMl;
    /** 当前收口工序实际录入水温 */
    private Integer actualWaterTemp;
    /** 当前收口工序实际录入洗涤剂 */
    private Integer actualDetergentMl;
}
