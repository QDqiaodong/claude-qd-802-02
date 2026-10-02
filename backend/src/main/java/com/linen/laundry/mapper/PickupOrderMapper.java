package com.linen.laundry.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Many;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.One;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.Update;

import com.linen.laundry.domain.Outlet;
import com.linen.laundry.domain.PickupOrder;
import com.linen.laundry.domain.PickupOrderLine;
import com.linen.laundry.dto.DispatchRow;

/**
 * 收送单：一次查询带出明细行（@Many）与所属网点（@One），动态条件走 @SelectProvider。
 */
@Mapper
public interface PickupOrderMapper {

    @Select("""
            SELECT p.*, o.name AS outlet_name, o.district AS district
              FROM pickup_order p
              LEFT JOIN outlet o ON o.id = p.outlet_id
             ORDER BY p.pickup_time DESC, p.id DESC
            """)
    @Results(id = "orderMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "orderNo", column = "order_no"),
            @Result(property = "outletId", column = "outlet_id"),
            @Result(property = "pickupTime", column = "pickup_time"),
            @Result(property = "deliverTime", column = "deliver_time"),
            @Result(property = "dirtyQty", column = "dirty_qty"),
            @Result(property = "cleanQty", column = "clean_qty"),
            @Result(property = "shortageQty", column = "shortage_qty"),
            @Result(property = "compensation", column = "compensation"),
            @Result(property = "outletName", column = "outlet_name"),
            @Result(property = "district", column = "district"),
            @Result(property = "lines", column = "id",
                    many = @Many(select = "com.linen.laundry.mapper.PickupOrderMapper.selectLines")),
            @Result(property = "outlet", column = "outlet_id",
                    one = @One(select = "com.linen.laundry.mapper.OutletMapper.selectById"))
    })
    List<PickupOrder> selectAll();

    /** 嵌套 @Many：收送单明细行（关联品类名称与单价） */
    @Select("""
            SELECT l.*, c.name AS category_name, c.spec AS spec, c.unit_price AS unit_price
              FROM pickup_order_line l
              LEFT JOIN linen_category c ON c.id = l.category_id
             WHERE l.order_id = #{orderId}
             ORDER BY l.id
            """)
    List<PickupOrderLine> selectLines(@Param("orderId") Long orderId);

    @Select("""
            SELECT p.*, o.name AS outlet_name, o.district AS district
              FROM pickup_order p
              LEFT JOIN outlet o ON o.id = p.outlet_id
             WHERE p.id = #{id}
            """)
    @ResultMap("orderMap")
    PickupOrder selectById(@Param("id") Long id);

    /** 动态条件：网点 / 状态 / 司机 / 车牌 / 起止日期 / 只看有短少 */
    @SelectProvider(type = PickupOrderSqlProvider.class, method = "search")
    @ResultMap("orderMap")
    List<PickupOrder> search(@Param("outletId") Long outletId,
                             @Param("status") String status,
                             @Param("driver") String driver,
                             @Param("from") String from,
                             @Param("to") String to,
                             @Param("shortageOnly") Boolean shortageOnly);

    @Insert("""
            INSERT INTO pickup_order (order_no, outlet_id, pickup_time, deliver_time,
                                      dirty_qty, clean_qty, driver, plate,
                                      shortage_qty, compensation, status, remark)
            VALUES (#{orderNo}, #{outletId}, #{pickupTime}, #{deliverTime},
                    #{dirtyQty}, #{cleanQty}, #{driver}, #{plate},
                    #{shortageQty}, #{compensation}, #{status}, #{remark})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(PickupOrder order);

    @Update("""
            UPDATE pickup_order
               SET deliver_time = #{deliverTime}, clean_qty = #{cleanQty},
                   shortage_qty = #{shortageQty}, compensation = #{compensation},
                   driver = IFNULL(#{driver}, driver), plate = IFNULL(#{plate}, plate),
                   status = #{status}, remark = IFNULL(#{remark}, remark)
             WHERE id = #{id}
            """)
    int updateDeliver(PickupOrder order);

    @Insert("""
            INSERT INTO pickup_order_line (order_id, category_id, dirty_qty, clean_qty,
                                           shortage_qty, loss_amount)
            VALUES (#{orderId}, #{categoryId}, #{dirtyQty}, #{cleanQty},
                    #{shortageQty}, #{lossAmount})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertLine(PickupOrderLine line);

    @Delete("DELETE FROM pickup_order_line WHERE order_id = #{orderId}")
    int deleteLines(@Param("orderId") Long orderId);

    @Delete("DELETE FROM pickup_order WHERE id = #{id}")
    int delete(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM pickup_order WHERE DATE(pickup_time) = CURDATE()")
    int countToday();

    @Select("SELECT IFNULL(MAX(id), 0) FROM pickup_order")
    int maxId();

    /** 收送单统计：今日收脏 / 送净 / 短少 / 赔付 */
    @Select("""
            SELECT COUNT(*)                                        AS order_count,
                   IFNULL(SUM(dirty_qty), 0)                       AS dirty_total,
                   IFNULL(SUM(clean_qty), 0)                       AS clean_total,
                   IFNULL(SUM(shortage_qty), 0)                    AS shortage_total,
                   IFNULL(SUM(compensation), 0)                    AS compensation_total
              FROM pickup_order
             WHERE DATE(pickup_time) = CURDATE() OR DATE(deliver_time) = CURDATE()
            """)
    Map<String, Object> todaySummary();

    /** 配送看板：按司机 + 车牌聚合 */
    @Select("""
            SELECT driver, plate,
                   COUNT(*)                        AS order_count,
                   IFNULL(SUM(dirty_qty), 0)       AS dirty_qty,
                   IFNULL(SUM(clean_qty), 0)       AS clean_qty,
                   IFNULL(SUM(shortage_qty), 0)    AS shortage_qty,
                   IFNULL(SUM(compensation), 0)    AS compensation,
                   GROUP_CONCAT(DISTINCT o.district ORDER BY o.district SEPARATOR '、') AS districts
              FROM pickup_order p
              LEFT JOIN outlet o ON o.id = p.outlet_id
             WHERE driver IS NOT NULL AND driver <> ''
             GROUP BY driver, plate
             ORDER BY dirty_qty DESC
            """)
    List<DispatchRow> dispatchBoard();
}
