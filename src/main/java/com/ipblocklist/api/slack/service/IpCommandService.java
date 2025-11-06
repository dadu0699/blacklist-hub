package com.ipblocklist.api.slack.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.ipblocklist.api.entity.IpEntity;
import com.ipblocklist.api.repository.IpRepository;
import com.ipblocklist.api.slack.util.AuditHelper;
import com.ipblocklist.api.slack.util.IpCommandParser.Parsed;
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

    public Mono<String> execute(Parsed p, String slackUserId, String teamId, String channelId) {
        final String sub = p.sub() == null ? "" : p.sub();
        switch (sub) {
            case "add" -> {
                final String ip = firstArg(p);
                final String reason = tailOrNull(p);
                log.info("CMD add ip={} by user={} in channel={}", ip, slackUserId, channelId);
                return addIp(slackUserId, teamId, ip, reason);
            }
            case "deactivate" -> {
                final String ip = firstArg(p);
                final String reason = tailOrNull(p);
                log.info("CMD deactivate ip={} by user={} in channel={}", ip, slackUserId, channelId);
                return deactivateIp(slackUserId, teamId, ip, reason);
            }
            case "reactivate" -> {
                final String ip = firstArg(p);
                final String reason = tailOrNull(p);
                log.info("CMD reactivate ip={} by user={} in channel={}", ip, slackUserId, channelId);
                return reactivateIp(slackUserId, teamId, ip, reason);
            }
            case "edit" -> {
                final String ip = firstArg(p);
                final String newReason = tailOrNull(p);
                log.info("CMD edit ip={} by user={} in channel={}", ip, slackUserId, channelId);
                return editIp(slackUserId, teamId, ip, newReason);
            }
            case "list" -> {
                log.info("CMD list by user={} in channel={}", slackUserId, channelId);
                return listIps(true, 200);
            }
            case "" -> {
                return Mono.just("""
                        Usage:
                        • /ip add <IP> [reason]
                        • /ip deactivate <IP> [reason]
                        • /ip reactivate <IP> [reason]
                        • /ip edit <IP> <new reason>
                        • /ip list
                        """);
            }
            default -> {
                return Mono.just(":warning: Unknown subcommand: `" + sub + "`\n" +
                        "Try `/ip list` or see `/ip` usage.");
            }
        }
    }

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
                    log.error("Failed to add IP {} by {}: {}", ip, slackUserId, e.getMessage(), e);
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
                        .switchIfEmpty(Mono.just(":warning: IP not found: `" + ip + "`")))
                .onErrorResume(e -> {
                    log.error("Failed to deactivate IP {} by {}: {}", ip, slackUserId, e.getMessage(), e);
                    return Mono.just(":x: Error while deactivating `" + ip + "`: " + e.getMessage());
                });
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
                        .switchIfEmpty(Mono.just(":warning: IP not found: `" + ip + "`")))
                .onErrorResume(e -> {
                    log.error("Failed to reactivate IP {} by {}: {}", ip, slackUserId, e.getMessage(), e);
                    return Mono.just(":x: Error while reactivating `" + ip + "`: " + e.getMessage());
                });
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
                        .switchIfEmpty(Mono.just(":warning: IP not found: `" + ip + "`")))
                .onErrorResume(e -> {
                    log.error("Failed to edit IP {} by {}: {}", ip, slackUserId, e.getMessage(), e);
                    return Mono.just(":x: Error while editing `" + ip + "`: " + e.getMessage());
                });
    }

    public Mono<String> listIps(boolean onlyActive, int limit) {
        return (onlyActive ? ipRepository.findByActiveTrueOrderByIpAsc() : ipRepository.findAll())
                .map(IpEntity::getIp)
                .take(limit > 0 ? limit : Long.MAX_VALUE)
                .collectList()
                .map(list -> list.isEmpty()
                        ? "_(no IPs found)_"
                        : "```\n" + String.join("\n", list) + "\n```")
                .onErrorResume(e -> {
                    log.error("Error listing IPs: {}", e.getMessage(), e);
                    return Mono.just(":x: Error retrieving list: " + e.getMessage());
                });
    }

    private static String firstArg(Parsed p) {
        return (p.args() == null || p.args().isEmpty()) ? "" : p.args().get(0);
    }

    private static String tailOrNull(Parsed p) {
        return (p.tail() == null || p.tail().isBlank()) ? null : p.tail();
    }
}
