package com.blacklisthub.slack.service;

import static com.blacklisthub.slack.util.CommandTextUtils.firstArg;
import static com.blacklisthub.slack.util.CommandTextUtils.tailOrNull;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.blacklisthub.entity.IocType;
import com.blacklisthub.entity.IpEntity;
import com.blacklisthub.repository.IpRepository;
import com.blacklisthub.slack.util.AuditHelper;
import com.blacklisthub.slack.util.CommandParser.Parsed;
import com.blacklisthub.slack.util.IocUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
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
            case "bulk" -> {
                final String csv = firstArg(p);
                final List<String> ips = parseCsvIps(csv);
                final String reason = tailOrNull(p);
                log.info("CMD bulk add {} ips by user={} in channel={}", ips.size(), slackUserId, channelId);
                return bulkAdd(slackUserId, teamId, ips, reason);
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
        if (!IocUtils.isValidIp(ip))
            return Mono.just(":warning: Invalid IP: `" + ip + "`");

        return slackUserService.ensureAndEnrichSlackUser(slackUserId, teamId)
                .flatMap(user -> ipRepository.findByIpNormalized(ip)
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
                                    .flatMap(saved -> auditHelper.log(
                                            IocType.IP,
                                            saved.getId(),
                                            "REACTIVATE",
                                            user.getId(),
                                            "{\"active\":0}",
                                            "{\"active\":1}"))
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
                                        .flatMap(saved -> auditHelper.log(
                                                IocType.IP,
                                                saved.getId(),
                                                "CREATE",
                                                user.getId(), null,
                                                "{" + String.join(",",
                                                        IocUtils.jsonKV("ip", ip, true),
                                                        IocUtils.jsonKV("reason", reason, true),
                                                        IocUtils.jsonKV("active", "1", false)) + "}"))
                                        .thenReturn(":white_check_mark: Added `" + ip + "`")))
                .onErrorResume(e -> {
                    log.error("Failed to add IP {} by {}: {}", ip, slackUserId, e.getMessage(), e);
                    return Mono.just(":x: Error while adding `" + ip + "`: " + e.getMessage());
                });
    }

    public Mono<String> deactivateIp(String slackUserId, String teamId, String ip, String reason) {
        if (!IocUtils.isValidIp(ip))
            return Mono.just(":warning: Invalid IP: `" + ip + "`");

        return slackUserService.ensureAndEnrichSlackUser(slackUserId, teamId)
                .flatMap(user -> ipRepository.findByIpNormalized(ip)
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
                                    IocUtils.jsonKV("active", "0", false),
                                    IocUtils.jsonKV("reason", found.getReason(), true)) + "}";

                            return ipRepository.save(found)
                                    .flatMap(saved -> auditHelper.log(
                                            IocType.IP,
                                            saved.getId(),
                                            "DEACTIVATE",
                                            user.getId(), prev, next))
                                    .thenReturn(":white_check_mark: Deactivated `" + ip + "`");
                        })
                        .switchIfEmpty(Mono.just(":warning: IP not found: `" + ip + "`")))
                .onErrorResume(e -> {
                    log.error("Failed to deactivate IP {} by {}: {}", ip, slackUserId, e.getMessage(), e);
                    return Mono.just(":x: Error while deactivating `" + ip + "`: " + e.getMessage());
                });
    }

    public Mono<String> reactivateIp(String slackUserId, String teamId, String ip, String reason) {
        if (!IocUtils.isValidIp(ip))
            return Mono.just(":warning: Invalid IP: `" + ip + "`");

        return slackUserService.ensureAndEnrichSlackUser(slackUserId, teamId)
                .flatMap(user -> ipRepository.findByIpNormalized(ip)
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
                                    IocUtils.jsonKV("active", "1", false),
                                    IocUtils.jsonKV("reason", found.getReason(), true)) + "}";

                            return ipRepository.save(found)
                                    .flatMap(saved -> auditHelper.log(
                                            IocType.IP,
                                            saved.getId(),
                                            "REACTIVATE",
                                            user.getId(), prev, next))
                                    .thenReturn(":white_check_mark: Reactivated `" + ip + "`");
                        })
                        .switchIfEmpty(Mono.just(":warning: IP not found: `" + ip + "`")))
                .onErrorResume(e -> {
                    log.error("Failed to reactivate IP {} by {}: {}", ip, slackUserId, e.getMessage(), e);
                    return Mono.just(":x: Error while reactivating `" + ip + "`: " + e.getMessage());
                });
    }

    public Mono<String> editIp(String slackUserId, String teamId, String ip, String newReason) {
        if (!IocUtils.isValidIp(ip))
            return Mono.just(":warning: Invalid IP: `" + ip + "`");

        return slackUserService.ensureAndEnrichSlackUser(slackUserId, teamId)
                .flatMap(user -> ipRepository.findByIpNormalized(ip)
                        .flatMap(found -> {
                            String prev = "{" + IocUtils.jsonKV("reason", found.getReason(), true) + "}";
                            found.setReason(newReason);
                            found.setUpdatedAt(LocalDateTime.now());
                            String next = "{" + IocUtils.jsonKV("reason", newReason, true) + "}";
                            return ipRepository.save(found)
                                    .flatMap(saved -> auditHelper.log(
                                            IocType.IP,
                                            saved.getId(),
                                            "UPDATE",
                                            user.getId(), prev, next))
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

    private static List<String> parseCsvIps(String csv) {
        if (csv == null || csv.isBlank())
            return List.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .collect(Collectors.toList());
    }

    public Mono<String> bulkAdd(String slackUserId, String teamId, List<String> ips, String reason) {
        if (ips == null || ips.isEmpty()) {
            return Mono.just(":warning: No IPs provided for bulk operation.");
        }

        final int maxBatch = 500;
        if (ips.size() > maxBatch) {
            return Mono.just(":warning: Bulk limit exceeded. Max " + maxBatch + " IPs allowed per bulk.");
        }

        return slackUserService.ensureAndEnrichSlackUser(slackUserId, teamId)
                .flatMapMany(user -> {

                    AtomicInteger added = new AtomicInteger(0);
                    AtomicInteger reactivated = new AtomicInteger(0);
                    AtomicInteger alreadyActive = new AtomicInteger(0);
                    AtomicInteger invalid = new AtomicInteger(0);
                    AtomicInteger errors = new AtomicInteger(0);

                    return Flux.fromIterable(ips)
                            .flatMap(ip -> {
                                if (!IocUtils.isValidIp(ip)) {
                                    invalid.incrementAndGet();
                                    return Mono.just(String.format(":warning: Invalid `%s`", ip));
                                }

                                return ipRepository.findByIpNormalized(ip)
                                        .flatMap(found -> {
                                            if (Boolean.TRUE.equals(found.getActive())) {
                                                alreadyActive.incrementAndGet();
                                                return Mono.just(
                                                        String.format(":information_source: Already active `%s`", ip));
                                            }

                                            found.setActive(true);
                                            found.setReason(reason);
                                            found.setUpdatedAt(LocalDateTime.now());
                                            found.setDeactivatedBy(null);
                                            found.setDeactivatedAt(null);

                                            return ipRepository.save(found)
                                                    .flatMap(saved -> auditHelper.log(
                                                            IocType.IP,
                                                            saved.getId(),
                                                            "REACTIVATE",
                                                            user.getId(),
                                                            "{\"active\":0}", "{\"active\":1}"))
                                                    .then(Mono.fromCallable(() -> {
                                                        reactivated.incrementAndGet();
                                                        return String.format(":white_check_mark: Reactivated `%s`", ip);
                                                    }));
                                        })
                                        .switchIfEmpty(
                                                ipRepository.save(IpEntity.builder()
                                                        .ip(ip)
                                                        .reason(reason)
                                                        .active(true)
                                                        .createdBy(user.getId())
                                                        .createdAt(LocalDateTime.now())
                                                        .build())
                                                        .flatMap(saved -> auditHelper.log(
                                                                IocType.IP,
                                                                saved.getId(),
                                                                "CREATE", user.getId(), null,
                                                                "{" + String.join(",",
                                                                        IocUtils.jsonKV("ip", ip, true),
                                                                        IocUtils.jsonKV("reason", reason, true),
                                                                        IocUtils.jsonKV("active", "1", false)) + "}"))
                                                        .then(Mono.fromCallable(() -> {
                                                            added.incrementAndGet();
                                                            return String.format(":white_check_mark: Added `%s`", ip);
                                                        })))
                                        .onErrorResume(e -> {
                                            errors.incrementAndGet();
                                            log.error("Error handling ip {} in bulk: {}", ip, e.getMessage(), e);
                                            return Mono.just(String.format(":x: Error `%s`: %s", ip, e.getMessage()));
                                        });
                            }, /* concurrency */ 10)
                            .collectList()
                            .map(individualResults -> {
                                StringBuilder sb = new StringBuilder();
                                sb.append("*Bulk result overview*\n");
                                sb.append(String.format("• Total requested: %d\n", ips.size()));
                                sb.append(String.format("• Added: %d\n", added.get()));
                                sb.append(String.format("• Reactivated: %d\n", reactivated.get()));
                                sb.append(String.format("• Already active: %d\n", alreadyActive.get()));
                                sb.append(String.format("• Invalid: %d\n", invalid.get()));
                                sb.append(String.format("• Errors: %d\n\n", errors.get()));

                                sb.append("*Details:*\n");
                                individualResults.forEach(line -> sb.append("• ").append(line).append("\n"));

                                return sb.toString();
                            });
                })
                .singleOrEmpty()
                .onErrorResume(e -> {
                    log.error("bulkAdd failed for user {}: {}", slackUserId, e.getMessage(), e);
                    return Mono.just(":x: Bulk operation failed: " + e.getMessage());
                });
    }

}