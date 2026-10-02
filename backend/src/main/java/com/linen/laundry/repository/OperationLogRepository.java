package com.linen.laundry.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.linen.laundry.domain.OperationLog;

/**
 * Spring Data JPA 在本仓只承担「操作日志」这一处辅助落库，主链路全部走 MyBatis。
 */
public interface OperationLogRepository extends JpaRepository<OperationLog, Long> {

    List<OperationLog> findTop20ByOrderByIdDesc();
}
