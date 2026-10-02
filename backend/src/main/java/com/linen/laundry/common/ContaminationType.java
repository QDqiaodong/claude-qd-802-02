package com.linen.laundry.common;

import java.util.List;

/**
 * 医院布草污染类型词典与状态机口径。
 */
public final class ContaminationType {

    private ContaminationType() {
    }

    public static final List<String> TYPE_CODES = List.of(
            "INFECTIOUS", "BLOOD", "CHEMICAL", "RADIOACTIVE", "OTHER");

    public static final List<String> TYPE_NAMES = List.of(
            "感染性污染", "血渍 / 体液污染", "化学污染", "放射性污染", "其他污染");

    public static String nameOf(String code) {
        int i = TYPE_CODES.indexOf(code);
        return i < 0 ? code : TYPE_NAMES.get(i);
    }

    public static boolean isValid(String code) {
        return code != null && TYPE_CODES.contains(code);
    }

    /** 事件状态中文名 */
    public static String eventStatusName(String status) {
        return switch (status == null ? "" : status) {
            case "DRAFT" -> "待确认";
            case "CONFIRMED" -> "已确认·隔离中";
            case "RELEASED" -> "已复核放行";
            default -> status;
        };
    }

    /** 隔离记录状态中文名 */
    public static String isolationStatusName(String status) {
        return "RELEASED".equals(status) ? "已放行" : "隔离中";
    }

    /** 复洗单状态中文名 */
    public static String rewashStatusName(String status) {
        return switch (status == null ? "" : status) {
            case "ACTIVE" -> "复洗在产";
            case "DONE" -> "复洗完成";
            case "FAILED" -> "复洗失败";
            case "DELETED" -> "已删除";
            default -> status;
        };
    }
}
