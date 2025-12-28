package com.blacklisthub.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import com.blacklisthub.entity.IocAuditLogEntity;

public interface IocAuditLogRepository extends ReactiveCrudRepository<IocAuditLogEntity, Long> {
}