package com.ipblocklist.api.slack.util;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.ipblocklist.api.entity.IpAuditLogEntity;
import com.ipblocklist.api.entity.IpEntity;
import com.ipblocklist.api.repository.IpAuditLogRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
@SuppressWarnings("null")
public class AuditHelper {

    private final IpAuditLogRepository auditRepository;

    public Mono<Void> log(String action, Long actorUserId, IpEntity ip, String prevJson, String newJson) {
        return auditRepository.save(IpAuditLogEntity.builder()
                .ipId(ip.getId())
                .action(action)
                .actorUserId(actorUserId)
                .prevValue(prevJson)
                .newValue(newJson)
                .createdAt(LocalDateTime.now())
                .build())
                .doOnSuccess(a -> log.debug("Audit [{}] for IP {} by user {}", action, ip.getIp(), actorUserId))
                .then();
    }
}
