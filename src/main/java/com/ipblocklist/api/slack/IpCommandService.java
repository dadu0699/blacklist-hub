package com.ipblocklist.api.slack;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.ipblocklist.api.entity.IpAuditLogEntity;
import com.ipblocklist.api.entity.IpEntity;
import com.ipblocklist.api.entity.SlackUserEntity;
import com.ipblocklist.api.repository.IpAuditLogRepository;
import com.ipblocklist.api.repository.IpRepository;
import com.ipblocklist.api.repository.SlackUserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class IpCommandService {

    private final IpRepository ipRepository;
    private final SlackUserRepository slackUserRepository;
    private final IpAuditLogRepository auditRepository;

    /**
     * Validate if a string is a valid IPv4 or IPv6 address.
     */
    public static boolean isValidIp(String ip) {
        try {
            return InetAddress.getByName(ip) != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Add or reactivate an IP in the blocklist.
     * - If the IP already exists and is active → warn user.
     * - If the IP exists but inactive → reactivate.
     * - If the IP doesn't exist → create new record.
     */
    public Mono<String> addIp(String slackUserId, String teamId, String ip, String reason) {
        if (!isValidIp(ip)) {
            return Mono.just(":warning: Invalid IP: `" + ip + "`");
        }

        return ensureSlackUser(slackUserId, teamId)
                .flatMap(user -> ipRepository.findByIp(ip)
                        .flatMap(found -> {
                            if (Boolean.TRUE.equals(found.getActive())) {
                                // Already active
                                return Mono.just(":information_source: IP already active: `" + ip + "`");
                            } else {
                                // Reactivate a previously deactivated IP
                                found.setActive(true);
                                found.setReason(reason);
                                found.setUpdatedAt(LocalDateTime.now());
                                found.setDeactivatedBy(null);
                                found.setDeactivatedAt(null);
                                log.info("Reactivating IP {} by Slack user {}", ip, slackUserId);
                                return ipRepository.save(found)
                                        .flatMap(saved -> audit("REACTIVATE", user.getId(), saved,
                                                "{\"active\":0}", "{\"active\":1}"))
                                        .thenReturn(":white_check_mark: Reactivated `" + ip + "`");
                            }
                        })
                        .switchIfEmpty(
                                // New IP record
                                ipRepository.save(IpEntity.builder()
                                        .ip(ip)
                                        .reason(reason)
                                        .active(true)
                                        .createdBy(user.getId())
                                        .createdAt(LocalDateTime.now())
                                        .build())
                                        .flatMap(saved -> audit("CREATE", user.getId(), saved, null,
                                                "{\"ip\":\"" + ip + "\",\"reason\":" +
                                                        (reason == null ? "null" : "\"" + reason + "\"") +
                                                        ",\"active\":1}"))
                                        .thenReturn(":white_check_mark: Added `" + ip + "`")))
                .onErrorResume(e -> {
                    log.error("Failed to add IP {} by user {}: {}", ip, slackUserId, e.getMessage(), e);
                    return Mono.just(":x: Error while adding `" + ip + "`: " + e.getMessage());
                });
    }

    /**
     * List IPs from the database (active or all).
     * Default is to show active ones, limited by 'limit'.
     */
    public Mono<String> listIps(boolean onlyActive, int limit) {
        return (onlyActive
                ? ipRepository.findByActiveTrueOrderByIpAsc()
                : ipRepository.findAll())
                .map(IpEntity::getIp)
                .take(limit > 0 ? limit : Long.MAX_VALUE)
                .collectList()
                .map(list -> {
                    if (list.isEmpty())
                        return "_(no IPs found)_";
                    return "```\n" + String.join("\n", list) + "\n```";
                })
                .onErrorResume(e -> {
                    log.error("Error listing IPs: {}", e.getMessage(), e);
                    return Mono.just(":x: Error retrieving list: " + e.getMessage());
                });
    }

    /**
     * Ensure that a Slack user exists in the database.
     * If not found, it creates a new record with basic info.
     */
    private Mono<SlackUserEntity> ensureSlackUser(String slackUserId, String teamId) {
        return slackUserRepository.findBySlackUserId(slackUserId)
                .switchIfEmpty(
                        slackUserRepository.save(SlackUserEntity.builder()
                                .slackUserId(slackUserId)
                                .teamId(teamId)
                                .displayName(slackUserId) // later can be enriched via Slack API
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build())
                                .doOnSuccess(
                                        u -> log.info("Registered new Slack user {} (team {})", slackUserId, teamId)));
    }

    /**
     * Save an audit log entry to track user actions on IPs.
     */
    private Mono<Void> audit(String action, Long actorUserId, IpEntity ip, String prevJson, String newJson) {
        return auditRepository.save(IpAuditLogEntity.builder()
                .ipId(ip.getId())
                .action(action)
                .actorUserId(actorUserId)
                .prevValue(prevJson)
                .newValue(newJson)
                .createdAt(LocalDateTime.now())
                .build())
                .doOnSuccess(a -> log.debug("Audit log [{}] for IP {} by user {}", action, ip.getIp(), actorUserId))
                .then();
    }

    /**
     * Utility class to parse incoming Slack commands.
     * Example: "/ip add 203.0.113.5 reason text"
     */
    public static record Parsed(String sub, List<String> args, String tail) {
    }

    public static Parsed parse(String text) {
        var trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty())
            return new Parsed("", List.of(), "");
        var parts = trimmed.split("\\s+", 3);
        var sub = parts[0].toLowerCase();
        var ip = parts.length > 1 ? parts[1] : "";
        var tail = parts.length > 2 ? parts[2] : "";
        return new Parsed(sub, List.of(ip), tail);
    }
}
