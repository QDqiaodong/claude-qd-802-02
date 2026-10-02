package com.linen.laundry.mapper;

import java.util.Map;

/**
 * 收送单动态检索 SQL。
 */
public class PickupOrderSqlProvider {

    public String search(Map<String, Object> params) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT p.*, o.name AS outlet_name, o.district AS district ")
                .append(" FROM pickup_order p LEFT JOIN outlet o ON o.id = p.outlet_id ")
                .append(" WHERE 1 = 1 ");

        if (notBlank(params.get("outletId"))) {
            sql.append(" AND p.outlet_id = #{outletId} ");
        }
        if (notBlank(params.get("status"))) {
            sql.append(" AND p.status = #{status} ");
        }
        if (notBlank(params.get("driver"))) {
            sql.append(" AND p.driver LIKE CONCAT('%', #{driver}, '%') ");
        }
        if (notBlank(params.get("from"))) {
            sql.append(" AND DATE(p.pickup_time) >= #{from} ");
        }
        if (notBlank(params.get("to"))) {
            sql.append(" AND DATE(p.pickup_time) <= #{to} ");
        }
        if (params.get("shortageOnly") != null && Boolean.parseBoolean(params.get("shortageOnly").toString())) {
            sql.append(" AND p.shortage_qty > 0 ");
        }
        sql.append(" ORDER BY p.pickup_time DESC, p.id DESC LIMIT 200 ");
        return sql.toString();
    }

    private boolean notBlank(Object value) {
        return value != null && !value.toString().isBlank();
    }
}
