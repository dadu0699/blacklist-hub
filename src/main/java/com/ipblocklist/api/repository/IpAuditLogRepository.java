package com.ipblocklist.api.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import com.ipblocklist.api.entity.IpAuditLogEntity;

public interface IpAuditLogRepository extends ReactiveCrudRepository<IpAuditLogEntity, Long> {
}
