package com.linen.laundry.mapper;

import java.util.Map;

/**
 * 网点动态检索 SQL：按关键字 / 类型 / 结算方式 / 预警拼条件。
 */
public class OutletSqlProvider {

    public String search(Map<String, Object> params) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT o.*, ")
                .append(" (SELECT IFNULL(SUM(p.dirty_qty),0) FROM pickup_order p WHERE p.outlet_id = o.id AND DATE(p.pickup_time) = CURDATE()) AS today_dirty, ")
                .append(" (SELECT IFNULL(SUM(p.clean_qty),0) FROM pickup_order p WHERE p.outlet_id = o.id AND DATE(p.deliver_time) = CURDATE()) AS today_clean ")
                .append(" FROM outlet o WHERE 1 = 1 ");

        Object keyword = params.get("keyword");
        if (keyword != null && !keyword.toString().isBlank()) {
            sql.append(" AND (o.name LIKE CONCAT('%', #{keyword}, '%') ")
                    .append(" OR o.code LIKE CONCAT('%', #{keyword}, '%') ")
                    .append(" OR o.contact LIKE CONCAT('%', #{keyword}, '%') ")
                    .append(" OR o.district LIKE CONCAT('%', #{keyword}, '%')) ");
        }
        Object outletType = params.get("outletType");
        if (outletType != null && !outletType.toString().isBlank()) {
            sql.append(" AND o.outlet_type = #{outletType} ");
        }
        Object settleType = params.get("settleType");
        if (settleType != null && !settleType.toString().isBlank()) {
            sql.append(" AND o.settle_type = #{settleType} ");
        }
        Object warnOnly = params.get("warnOnly");
        if (warnOnly != null && Boolean.parseBoolean(warnOnly.toString())) {
            sql.append(" AND o.warn_threshold > 0 AND o.dirty_pending >= o.warn_threshold ");
        }
        sql.append(" ORDER BY o.dirty_pending DESC, o.id ");
        return sql.toString();
    }
}
