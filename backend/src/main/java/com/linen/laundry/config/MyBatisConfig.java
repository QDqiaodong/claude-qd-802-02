package com.linen.laundry.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * 本仓数据访问以 MyBatis 注解方式为主：Mapper 接口上直接写 @Select / @Insert / @Update / @Delete，
 * 关联映射用 @Results + @ResultMap + 嵌套 @Many / @One，动态条件用 @SelectProvider。
 */
@Configuration
@MapperScan("com.linen.laundry.mapper")
public class MyBatisConfig {
}
