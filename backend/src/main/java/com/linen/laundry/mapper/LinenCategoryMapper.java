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
import org.apache.ibatis.annotations.Update;

import com.linen.laundry.domain.LinenCategory;
import com.linen.laundry.domain.WashBatch;

/**
 * 布草品类台账：列表带出周转缺口、在库金额，并用嵌套 @Many 带出该品类近期洗涤批次。
 */
@Mapper
public interface LinenCategoryMapper {

    @Select("""
            SELECT c.*,
                   GREATEST(0, c.min_turnover - c.stock_qty)                       AS gap,
                   ROUND(c.stock_qty * c.unit_price, 2)                            AS stock_amount,
                   (SELECT COUNT(*) FROM wash_batch b WHERE b.category_id = c.id)  AS batch_count
              FROM linen_category c
             ORDER BY gap DESC, c.id
            """)
    @Results(id = "categoryMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "code", column = "code"),
            @Result(property = "name", column = "name"),
            @Result(property = "unitPrice", column = "unit_price"),
            @Result(property = "stockQty", column = "stock_qty"),
            @Result(property = "scrapRate", column = "scrap_rate"),
            @Result(property = "minTurnover", column = "min_turnover"),
            @Result(property = "gap", column = "gap"),
            @Result(property = "stockAmount", column = "stock_amount"),
            @Result(property = "recentBatches", column = "id",
                    many = @Many(select = "com.linen.laundry.mapper.WashBatchMapper.selectByCategoryId"))
    })
    List<LinenCategory> selectAll();

    /**
     * 供 @One 嵌套使用的「单向」查询：不带任何关联，避免 category → batch → category 循环嵌套。
     */
    @Select("""
            SELECT c.*,
                   GREATEST(0, c.min_turnover - c.stock_qty) AS gap,
                   ROUND(c.stock_qty * c.unit_price, 2)      AS stock_amount
              FROM linen_category c WHERE c.id = #{id}
            """)
    LinenCategory selectById(@Param("id") Long id);

    @Select("SELECT * FROM linen_category WHERE code = #{code}")
    LinenCategory selectByCode(@Param("code") String code);

    /** 悲观行锁：配方版本发布 / 停用 / 新建草稿前锁品类行，串行化同品类并发操作 */
    @Select("SELECT * FROM linen_category WHERE id = #{id} FOR UPDATE")
    LinenCategory selectByIdForUpdate(@Param("id") Long id);

    @Select("""
            SELECT c.*,
                   GREATEST(0, c.min_turnover - c.stock_qty) AS gap,
                   ROUND(c.stock_qty * c.unit_price, 2)      AS stock_amount
              FROM linen_category c
             WHERE c.stock_qty < c.min_turnover
             ORDER BY gap DESC
            """)
    @ResultMap("categoryMap")
    List<LinenCategory> selectShortage();

    @Insert("""
            INSERT INTO linen_category (code, name, spec, unit_price, stock_qty,
                                        scrap_rate, min_turnover, owner_type, remark)
            VALUES (#{code}, #{name}, #{spec}, #{unitPrice}, #{stockQty},
                    #{scrapRate}, #{minTurnover}, #{ownerType}, #{remark})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(LinenCategory category);

    @Update("""
            UPDATE linen_category
               SET name = #{name}, spec = #{spec}, unit_price = #{unitPrice},
                   scrap_rate = #{scrapRate}, min_turnover = #{minTurnover},
                   owner_type = #{ownerType}, remark = #{remark}
             WHERE id = #{id}
            """)
    int update(LinenCategory category);

    /** 快速盘点落库 */
    @Update("UPDATE linen_category SET stock_qty = #{stockQty} WHERE id = #{id}")
    int updateStock(@Param("id") Long id, @Param("stockQty") Integer stockQty);

    /** 折叠完成入库：净布草回补在库数 */
    @Update("UPDATE linen_category SET stock_qty = stock_qty + #{qty} WHERE id = #{id}")
    int addStock(@Param("id") Long id, @Param("qty") Integer qty);

    @Delete("DELETE FROM linen_category WHERE id = #{id}")
    int delete(@Param("id") Long id);
}
