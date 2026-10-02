package com.linen.laundry.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * 车间操作日志：Spring Data JPA 在本仓只承担这一处辅助落库（主链路全部走 MyBatis）。
 */
@Data
@Entity
@Table(name = "operation_log")
public class OperationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** OUTLET / CATEGORY / ORDER / BATCH / MACHINE */
    @Column(name = "biz_type", length = 16)
    private String bizType;

    @Column(name = "biz_id")
    private Long bizId;

    @Column(name = "action_name", length = 64)
    private String actionName;

    @Column(name = "detail", length = 255)
    private String detail;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
