package com.linen.laundry.common;

import java.util.List;

/**
 * 洗涤工序链：预洗→主洗→漂洗→中和→脱水→烘干→烫平→折叠。
 * 工序必须按顺序推进，不允许跳步。
 */
public final class WashProcess {

    private WashProcess() {
    }

    public static final List<String> STEP_CODES = List.of(
            "PRE_WASH", "MAIN_WASH", "RINSE", "NEUTRALIZE",
            "EXTRACT", "DRY", "IRON", "FOLD");

    public static final List<String> STEP_NAMES = List.of(
            "预洗", "主洗", "漂洗", "中和", "脱水", "烘干", "烫平", "折叠");

    public static final List<Integer> DEFAULT_TEMP = List.of(35, 70, 45, 40, 0, 75, 160, 0);

    public static int size() {
        return STEP_CODES.size();
    }

    public static String nameOf(String code) {
        int i = STEP_CODES.indexOf(code);
        return i < 0 ? code : STEP_NAMES.get(i);
    }

    /** 工序号从 1 开始；非法编码返回 -1 */
    public static int orderOf(String code) {
        return STEP_CODES.indexOf(code) + 1;
    }

    public static String codeAt(int order) {
        return order >= 1 && order <= STEP_CODES.size() ? STEP_CODES.get(order - 1) : null;
    }

    /** 下一道工序编码；已是最后一道返回 null */
    public static String nextOf(String code) {
        return codeAt(orderOf(code) + 1);
    }

    public static int defaultTemp(String code) {
        int i = STEP_CODES.indexOf(code);
        return i < 0 ? 0 : DEFAULT_TEMP.get(i);
    }
}
