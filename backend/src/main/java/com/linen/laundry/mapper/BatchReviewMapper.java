package com.linen.laundry.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.linen.laundry.domain.BatchDeviationReview;

/**
 * 批次参数偏差复核记录。
 */
@Mapper
public interface BatchReviewMapper {

    @Select("""
            SELECT r.*, b.batch_no
              FROM batch_deviation_review r
              LEFT JOIN wash_batch b ON b.id = r.batch_id
             WHERE r.batch_id = #{batchId}
             ORDER BY r.id DESC
            """)
    @Results(id = "reviewMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "batchId", column = "batch_id"),
            @Result(property = "stepCode", column = "step_code"),
            @Result(property = "stepName", column = "step_name"),
            @Result(property = "stepOrder", column = "step_order"),
            @Result(property = "stdWaterTemp", column = "std_water_temp"),
            @Result(property = "stdDetergentMl", column = "std_detergent_ml"),
            @Result(property = "actualWaterTemp", column = "actual_water_temp"),
            @Result(property = "actualDetergentMl", column = "actual_detergent_ml"),
            @Result(property = "targetStep", column = "target_step"),
            @Result(property = "nextWaterTemp", column = "next_water_temp"),
            @Result(property = "nextDetergentMl", column = "next_detergent_ml"),
            @Result(property = "operator", column = "operator"),
            @Result(property = "status", column = "status"),
            @Result(property = "reason", column = "reason"),
            @Result(property = "reviewer", column = "reviewer"),
            @Result(property = "decidedAt", column = "decided_at"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "batchNo", column = "batch_no")
    })
    List<BatchDeviationReview> selectByBatchId(@Param("batchId") Long batchId);

    /** 悲观行锁：复核结论与批次推进互斥 */
    @Select("SELECT * FROM batch_deviation_review WHERE id = #{id} FOR UPDATE")
    BatchDeviationReview selectByIdForUpdate(@Param("id") Long id);

    @Insert("""
            INSERT INTO batch_deviation_review (batch_id, step_code, step_name, step_order,
                                                std_water_temp, std_detergent_ml,
                                                actual_water_temp, actual_detergent_ml,
                                                target_step, next_water_temp, next_detergent_ml,
                                                operator, status, created_at)
            VALUES (#{batchId}, #{stepCode}, #{stepName}, #{stepOrder},
                    #{stdWaterTemp}, #{stdDetergentMl},
                    #{actualWaterTemp}, #{actualDetergentMl},
                    #{targetStep}, #{nextWaterTemp}, #{nextDetergentMl},
                    #{operator}, #{status}, #{createdAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(BatchDeviationReview review);

    /** 复核结论落库（CAS：仅待复核可结论，防两人同时复核） */
    @Update("""
            UPDATE batch_deviation_review
               SET status = #{status}, reason = #{reason}, reviewer = #{reviewer}, decided_at = NOW()
             WHERE id = #{id} AND status = 'PENDING'
            """)
    int resolve(@Param("id") Long id, @Param("status") String status,
                @Param("reason") String reason, @Param("reviewer") String reviewer);

    @Select("SELECT COUNT(*) FROM batch_deviation_review WHERE batch_id = #{batchId} AND status = 'PENDING'")
    int countPending(@Param("batchId") Long batchId);

    @Delete("DELETE FROM batch_deviation_review WHERE batch_id = #{batchId}")
    int deleteByBatchId(@Param("batchId") Long batchId);
}
