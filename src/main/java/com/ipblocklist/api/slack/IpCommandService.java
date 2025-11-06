package com.ipblocklist.api.slack;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.ipblocklist.api.entity.IpEntity;
import com.ipblocklist.api.repository.IpRepository;
import com.ipblocklist.api.slack.util.AuditHelper;
import com.ipblocklist.api.slack.util.IpUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class IpCommandService {

    private final IpRepository ipRepository;
    private final AuditHelper auditHelper;
    private final SlackUserService slackUserService;

    public Mono<String> addIp(String slackUserId, String teamId, String ip, String reason) {
        if (!IpUtils.isValidIp(ip))
            return Mono.just(":warning: Invalid IP: `" + ip + "`");

        return slackUserService.ensureAndEnrichSlackUser(slackUserId, teamId)
                .flatMap(user -> ipRepository.findByIp(ip)
                        .flatMap(found -> {
                            if (Boolean.TRUE.equals(found.getActive())) {
                                return Mono.just(":information_source: IP already active: `" + ip + "`");
                            }
                            found.setActive(true);
                            found.setReason(reason);
                            found.setUpdatedAt(LocalDateTime.now());
                            found.setDeactivatedBy(null);
                            found.setDeactivatedAt(null);
                            return ipRepository.save(found)
                                    .flatMap(saved -> auditHelper.log("REACTIVATE", user.getId(), saved,
                                            "{\"active\":0}", "{\"active\":1}"))
                                    .thenReturn(":white_check_mark: Reactivated `" + ip + "`");
                        })
                        .switchIfEmpty(
                                ipRepository.save(IpEntity.builder()
                                        .ip(ip)
                                        .reason(reason)
                                        .active(true)
                                        .createdBy(user.getId())
                                        .createdAt(LocalDateTime.now())
                                        .build())
                                        .flatMap(saved -> auditHelper.log("CREATE", user.getId(), saved, null,
                                                "{" + String.join(",",
                                                        IpUtils.jsonKV("ip", ip, true),
                                                        IpUtils.jsonKV("reason", reason, true),
                                                        IpUtils.jsonKV("active", "1", false)) + "}"))
                                        .thenReturn(":white_check_mark: Added `" + ip + "`")))
                .onErrorResume(e -> {
                    log.error("Failed to add IP {}: {}", ip, e.getMessage());
                    return Mono.just(":x: Error while adding `" + ip + "`: " + e.getMessage());
                });
    }

    public Mono<String> deactivateIp(String slackUserId, String teamId, String ip, String reason) {
        if (!IpUtils.isValidIp(ip))
            return Mono.just(":warning: Invalid IP: `" + ip + "`");

        return slackUserService.ensureAndEnrichSlackUser(slackUserId, teamId)
                .flatMap(user -> ipRepository.findByIp(ip)
                        .flatMap(found -> {
                            if (!Boolean.TRUE.equals(found.getActive())) {
                                return Mono.just(":information_source: IP already inactive: `" + ip + "`");
                            }
                            found.setActive(false);
                            found.setUpdatedAt(LocalDateTime.now());
                            found.setDeactivatedBy(user.getId());
                            found.setDeactivatedAt(LocalDateTime.now());
                            if (reason != null && !reason.isBlank())
                                found.setReason(reason);

                            String prev = "{\"active\":1}";
                            String next = "{" + String.join(",",
                                    IpUtils.jsonKV("active", "0", false),
                                    IpUtils.jsonKV("reason", found.getReason(), true)) + "}";

                            return ipRepository.save(found)
                                    .flatMap(saved -> auditHelper.log("DEACTIVATE", user.getId(), saved, prev, next))
                                    .thenReturn(":white_check_mark: Deactivated `" + ip + "`");
                        })
                        .switchIfEmpty(Mono.just(":warning: IP not found: `" + ip + "`")));
    }

    public Mono<String> reactivateIp(String slackUserId, String teamId, String ip, String reason) {
        if (!IpUtils.isValidIp(ip))
            return Mono.just(":warning: Invalid IP: `" + ip + "`");

        return slackUserService.ensureAndEnrichSlackUser(slackUserId, teamId)
                .flatMap(user -> ipRepository.findByIp(ip)
                        .flatMap(found -> {
                            if (Boolean.TRUE.equals(found.getActive())) {
                                return Mono.just(":information_source: IP already active: `" + ip + "`");
                            }
                            found.setActive(true);
                            found.setUpdatedAt(LocalDateTime.now());
                            found.setDeactivatedBy(null);
                            found.setDeactivatedAt(null);
                            if (reason != null && !reason.isBlank())
                                found.setReason(reason);

                            String prev = "{\"active\":0}";
                            String next = "{" + String.join(",",
                                    IpUtils.jsonKV("active", "1", false),
                                    IpUtils.jsonKV("reason", found.getReason(), true)) + "}";

                            return ipRepository.save(found)
                                    .flatMap(saved -> auditHelper.log("REACTIVATE", user.getId(), saved, prev, next))
                                    .thenReturn(":white_check_mark: Reactivated `" + ip + "`");
                        })
                        .switchIfEmpty(Mono.just(":warning: IP not found: `" + ip + "`")));
    }

    public Mono<String> editIp(String slackUserId, String teamId, String ip, String newReason) {
        if (!IpUtils.isValidIp(ip))
            return Mono.just(":warning: Invalid IP: `" + ip + "`");

        return slackUserService.ensureAndEnrichSlackUser(slackUserId, teamId)
                .flatMap(user -> ipRepository.findByIp(ip)
                        .flatMap(found -> {
                            String prev = "{" + IpUtils.jsonKV("reason", found.getReason(), true) + "}";
                            found.setReason(newReason);
                            found.setUpdatedAt(LocalDateTime.now());
                            String next = "{" + IpUtils.jsonKV("reason", newReason, true) + "}";
                            return ipRepository.save(found)
                                    .flatMap(saved -> auditHelper.log("UPDATE", user.getId(), saved, prev, next))
                                    .thenReturn(":white_check_mark: Updated `" + ip + "` reason");
                        })
                        .switchIfEmpty(Mono.just(":warning: IP not found: `" + ip + "`")));
    }

    public Mono<String> listIps(boolean onlyActive, int limit) {
        return (onlyActive ? ipRepository.findByActiveTrueOrderByIpAsc() : ipRepository.findAll())
                .map(IpEntity::getIp)
                .take(limit > 0 ? limit : Long.MAX_VALUE)
                .collectList()
                .map(list -> list.isEmpty()
                        ? "_(no IPs found)_"
                        : "```\n" + String.join("\n", list) + "\n```");
    }
}
