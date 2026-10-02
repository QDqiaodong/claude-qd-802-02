package com.linen.laundry.mapper;

import java.util.Map;

/**
 * 批次隧道视图动态 SQL：按状态 / 停留工序 / 品类过滤，并按「在产优先」排序。
 */
public class WashBatchSqlProvider {

    public String tunnel(Map<String, Object> params) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT b.*, c.name AS category_name, m.name AS machine_name, m.code AS machine_code, ")
                .append(" ROUND(b.current_step_order * 100.0 / 8, 0) AS progress, ")
                .append(" (SELECT i.id FROM batch_isolation i ")
                .append("   WHERE i.batch_id = b.id AND i.status = 'ISOLATED' LIMIT 1) AS isolation_id, ")
                .append(" (SELECT e.id FROM batch_isolation i JOIN contamination_event e ON e.id = i.event_id ")
                .append("   WHERE i.batch_id = b.id AND i.status = 'ISOLATED' LIMIT 1) AS quarantine_event_id, ")
                .append(" (SELECT e.event_no FROM batch_isolation i JOIN contamination_event e ON e.id = i.event_id ")
                .append("   WHERE i.batch_id = b.id AND i.status = 'ISOLATED' LIMIT 1) AS quarantine_event_no, ")
                .append(" (SELECT i.hit_reason FROM batch_isolation i ")
                .append("   WHERE i.batch_id = b.id AND i.status = 'ISOLATED' LIMIT 1) AS quarantine_reason, ")
                .append(" (SELECT r.status FROM rewash_order r WHERE r.rewash_batch_id = b.id LIMIT 1) AS rewash_status, ")
                .append(" (SELECT sb.batch_no FROM rewash_order r JOIN wash_batch sb ON sb.id = r.source_batch_id ")
                .append("   WHERE r.rewash_batch_id = b.id LIMIT 1) AS source_batch_no, ")
                .append(" (SELECT wb.batch_no FROM rewash_order r JOIN wash_batch wb ON wb.id = r.rewash_batch_id ")
                .append("   JOIN batch_isolation i ON i.id = r.isolation_id ")
                .append("   WHERE r.source_batch_id = b.id AND r.status IN ('ACTIVE','DONE') LIMIT 1) AS rewash_batch_no ")
                .append(" FROM wash_batch b ")
                .append(" LEFT JOIN linen_category c ON c.id = b.category_id ")
                .append(" LEFT JOIN machine m ON m.id = b.machine_id ")
                .append(" WHERE 1 = 1 ");

        Object status = params.get("status");
        if (status != null && !status.toString().isBlank()) {
            sql.append(" AND b.status = #{status} ");
        }
        Object stepCode = params.get("stepCode");
        if (stepCode != null && !stepCode.toString().isBlank()) {
            sql.append(" AND b.current_step = #{stepCode} ");
        }
        Object categoryId = params.get("categoryId");
        if (categoryId != null && !categoryId.toString().isBlank()) {
            sql.append(" AND b.category_id = #{categoryId} ");
        }
        sql.append(" ORDER BY FIELD(b.status, 'QUARANTINED', 'REVIEW', 'RUNNING', 'HOLD', 'DONE'), b.current_step_order, b.start_time DESC ");
        sql.append(" LIMIT 100 ");
        return sql.toString();
    }
}
