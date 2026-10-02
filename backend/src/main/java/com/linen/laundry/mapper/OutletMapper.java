package com.linen.laundry.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Many;
import org.apache.ibatis.annotations.Mapper;
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

/**
 * 客户网点：MyBatis 注解方式，列表一步带出最近收送单（嵌套 @Many）。
 */
@Mapper
public interface OutletMapper {

    @Select("""
            SELECT o.*,
                   (SELECT IFNULL(SUM(p.dirty_qty), 0) FROM pickup_order p
                     WHERE p.outlet_id = o.id AND DATE(p.pickup_time) = CURDATE()) AS today_dirty,
                   (SELECT IFNULL(SUM(p.clean_qty), 0) FROM pickup_order p
                     WHERE p.outlet_id = o.id AND DATE(p.deliver_time) = CURDATE()) AS today_clean
              FROM outlet o
             ORDER BY o.dirty_pending DESC, o.id
            """)
    @Results(id = "outletMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "code", column = "code"),
            @Result(property = "name", column = "name"),
            @Result(property = "dirtyPending", column = "dirty_pending"),
            @Result(property = "warnThreshold", column = "warn_threshold"),
            @Result(property = "recentOrders", column = "id",
                    many = @Many(select = "com.linen.laundry.mapper.OutletMapper.selectRecentOrders"))
    })
    List<Outlet> selectAll();

    /** 嵌套映射用的子查询：网点最近 3 张收送单 */
    @Select("SELECT * FROM pickup_order WHERE outlet_id = #{outletId} ORDER BY pickup_time DESC LIMIT 3")
    List<PickupOrder> selectRecentOrders(@Param("outletId") Long outletId);

    @Select("SELECT * FROM outlet WHERE id = #{id}")
    @ResultMap("outletMap")
    Outlet selectById(@Param("id") Long id);

    @Select("SELECT * FROM outlet WHERE code = #{code}")
    Outlet selectByCode(@Param("code") String code);

    @Select("SELECT COUNT(*) FROM pickup_order WHERE outlet_id = #{id}")
    int countOrders(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM pickup_order WHERE DATE(pickup_time) = CURDATE()")
    int countToday();

    /** 动态条件检索：关键字 / 类型 / 结算方式 / 只看预警网点 */
    @SelectProvider(type = OutletSqlProvider.class, method = "search")
    @ResultMap("outletMap")
    List<Outlet> search(@Param("keyword") String keyword,
                        @Param("outletType") String outletType,
                        @Param("settleType") String settleType,
                        @Param("warnOnly") Boolean warnOnly);

    @Select("""
            SELECT IFNULL(SUM(dirty_pending), 0)        AS pending_total,
                   IFNULL(SUM(credit_limit), 0)         AS credit_total,
                   COUNT(*)                             AS outlet_count,
                   SUM(CASE WHEN dirty_pending >= warn_threshold AND warn_threshold > 0 THEN 1 ELSE 0 END) AS warn_count
              FROM outlet WHERE status = 'ACTIVE'
            """)
    java.util.Map<String, Object> summary();

    @Insert("""
            INSERT INTO outlet (code, name, outlet_type, district, contact, phone,
                                settle_type, credit_limit, dirty_pending, warn_threshold,
                                map_x, map_y, status, created_at)
            VALUES (#{code}, #{name}, #{outletType}, #{district}, #{contact}, #{phone},
                    #{settleType}, #{creditLimit}, #{dirtyPending}, #{warnThreshold},
                    #{mapX}, #{mapY}, #{status}, NOW())
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(Outlet outlet);

    @Update("""
            UPDATE outlet
               SET name = #{name}, outlet_type = #{outletType}, district = #{district},
                   contact = #{contact}, phone = #{phone}, settle_type = #{settleType},
                   credit_limit = #{creditLimit}, dirty_pending = #{dirtyPending},
                   warn_threshold = #{warnThreshold}, map_x = #{mapX}, map_y = #{mapY},
                   status = #{status}
             WHERE id = #{id}
            """)
    int update(Outlet outlet);

    /** 收脏后扣减待收量 */
    @Update("UPDATE outlet SET dirty_pending = GREATEST(0, dirty_pending - #{qty}) WHERE id = #{id}")
    int reducePending(@Param("id") Long id, @Param("qty") Integer qty);

    @Update("UPDATE outlet SET dirty_pending = dirty_pending + #{qty} WHERE id = #{id}")
    int addPending(@Param("id") Long id, @Param("qty") Integer qty);

    @Delete("DELETE FROM outlet WHERE id = #{id}")
    int delete(@Param("id") Long id);
}
