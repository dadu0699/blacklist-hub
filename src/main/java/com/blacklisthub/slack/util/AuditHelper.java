package com.blacklisthub.slack.util;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.blacklisthub.entity.IocAuditLogEntity;
import com.blacklisthub.entity.IocType;
import com.blacklisthub.repository.IocAuditLogRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
@SuppressWarnings("null")
public class AuditHelper {

        private final IocAuditLogRepository auditRepository;

        /**
         * Logs a polymorphic IoC action.
         *
         * @param iocType     The type of indicator (IP, HASH, DOMAIN, URL)
         * @param indicatorId The ID of the indicator in its respective table
         * @param action      The action taken (CREATE, UPDATE, etc.)
         * @param actorUserId The local DB ID of the Slack user
         * @param prevJson    Old values (JSON string)
         * @param newJson     New values (JSON string)
         * @return Mono<Void>
         */
        public Mono<Void> log(IocType iocType, Long indicatorId, String action, Long actorUserId, String prevJson,
                        String newJson) {
                return auditRepository.save(IocAuditLogEntity.builder()
                                .iocType(iocType)
                                .indicatorId(indicatorId)
                                .action(action)
                                .actorUserId(actorUserId)
                                .prevValue(prevJson)
                                .newValue(newJson)
                                .createdAt(LocalDateTime.now())
                                .build())
                                .doOnSuccess(a -> log.debug("Audit [{}] for {}#{} by user {}",
                                                action, iocType, indicatorId, actorUserId))
                                .then();
        }
}