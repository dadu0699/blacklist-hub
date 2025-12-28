package com.blacklisthub.entity;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("ioc_audit_log")
public class IocAuditLogEntity {
    @Id
    private Long id;

    private IocType iocType;
    private Long indicatorId;

    private String action;
    private Long actorUserId;
    private String prevValue;
    private String newValue;

    private LocalDateTime createdAt;

}