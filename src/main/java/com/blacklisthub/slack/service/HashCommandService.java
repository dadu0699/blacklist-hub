package com.blacklisthub.slack.service;

import static com.blacklisthub.slack.util.CommandTextUtils.firstArg;
import static com.blacklisthub.slack.util.CommandTextUtils.tailOrNull;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.blacklisthub.entity.HashEntity;
import com.blacklisthub.entity.IocType;
import com.blacklisthub.repository.HashRepository;
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
public class HashCommandService {

    private final HashRepository hashRepository;
    private final AuditHelper auditHelper;
    private final SlackUserService slackUserService;

    public Mono<String> execute(Parsed p, String slackUserId, String teamId, String channelId) {
        final String sub = p.sub() == null ? "" : p.sub();
        switch (sub) {
            case "add" -> {
                final String hash = firstArg(p);
                final String reason = tailOrNull(p);
                log.info("CMD add hash={} by user={} in channel={}", hash, slackUserId, channelId);
                return addHash(slackUserId, teamId, hash, reason);
            }
            case "deactivate" -> {
                final String hash = firstArg(p);
                final String reason = tailOrNull(p);
                log.info("CMD deactivate hash={} by user={} in channel={}", hash, slackUserId, channelId);
                return deactivateHash(slackUserId, teamId, hash, reason);
            }
            case "reactivate" -> {
                final String hash = firstArg(p);
                final String reason = tailOrNull(p);
                log.info("CMD reactivate hash={} by user={} in channel={}", hash, slackUserId, channelId);
                return reactivateHash(slackUserId, teamId, hash, reason);
            }
            case "edit" -> {
                final String hash = firstArg(p);
                final String newReason = tailOrNull(p);
                log.info("CMD edit hash={} by user={} in channel={}", hash, slackUserId, channelId);
                return editHash(slackUserId, teamId, hash, newReason);
            }
            case "list" -> {
                log.info("CMD list hash by user={} in channel={}", slackUserId, channelId);
                return listHashes(true, 200);
            }
            case "bulk" -> {
                final String csv = firstArg(p);
                final List<String> hashes = parseCsv(csv);
                final String reason = tailOrNull(p);
                log.info("CMD bulk add {} hashes by user={} in channel={}", hashes.size(), slackUserId, channelId);
                return bulkAdd(slackUserId, teamId, hashes, reason);
            }
            case "" -> {
                return Mono.just("""
                        Usage:
                        • /hash add <HASH> [reason]
                        • /hash deactivate <HASH> [reason]
                        • /hash reactivate <HASH> [reason]
                        • /hash edit <HASH> <new reason>
                        • /hash list
                        """);
            }
            default -> {
                return Mono.just(":warning: Unknown subcommand: `" + sub + "`\n" +
                        "Try `/hash list` or see `/hash` usage.");
            }
        }
    }

    private String normalize(String hash) {
        return hash.toLowerCase();
    }

    public Mono<String> addHash(String slackUserId, String teamId, String hash, String reason) {
        final String normHash = normalize(hash);
        if (!IocUtils.isValidHash(normHash))
            return Mono.just(":warning: Invalid HASH: `" + hash + "`");

        return slackUserService.ensureAndEnrichSlackUser(slackUserId, teamId)
                .flatMap(user -> hashRepository.findByNormalizedHash(normHash)
                        .flatMap(found -> {
                            if (Boolean.TRUE.equals(found.getActive())) {
                                return Mono.just(":information_source: HASH already active: `" + hash + "`");
                            }
                            found.setActive(true);
                            found.setReason(reason);
                            found.setUpdatedAt(LocalDateTime.now());
                            found.setDeactivatedBy(null);
                            found.setDeactivatedAt(null);
                            return hashRepository.save(found)
                                    .flatMap(saved -> auditHelper.log(IocType.HASH, saved.getId(), "REACTIVATE",
                                            user.getId(), "{\"active\":0}", "{\"active\":1}"))
                                    .thenReturn(":white_check_mark: Reactivated `" + hash + "`");
                        })
                        .switchIfEmpty(
                                hashRepository.save(HashEntity.builder()
                                        .hashValue(normHash)
                                        .reason(reason)
                                        .active(true)
                                        .createdBy(user.getId())
                                        .createdAt(LocalDateTime.now())
                                        .build())
                                        .flatMap(saved -> auditHelper.log(IocType.HASH, saved.getId(), "CREATE",
                                                user.getId(), null,
                                                "{" + String.join(",",
                                                        IocUtils.jsonKV("hash", normHash, true),
                                                        IocUtils.jsonKV("reason", reason, true),
                                                        IocUtils.jsonKV("active", "1", false)) + "}"))
                                        .thenReturn(":white_check_mark: Added `" + hash + "`")))
                .onErrorResume(e -> {
                    log.error("Failed to add HASH {} by {}: {}", hash, slackUserId, e.getMessage(), e);
                    return Mono.just(":x: Error while adding `" + hash + "`: " + e.getMessage());
                });
    }

    public Mono<String> deactivateHash(String slackUserId, String teamId, String hash, String reason) {
        final String normHash = normalize(hash);
        if (!IocUtils.isValidHash(normHash))
            return Mono.just(":warning: Invalid HASH: `" + hash + "`");

        return slackUserService.ensureAndEnrichSlackUser(slackUserId, teamId)
                .flatMap(user -> hashRepository.findByNormalizedHash(normHash)
                        .flatMap(found -> {
                            if (!Boolean.TRUE.equals(found.getActive())) {
                                return Mono.just(":information_source: HASH already inactive: `" + hash + "`");
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

                            return hashRepository.save(found)
                                    .flatMap(saved -> auditHelper.log(IocType.HASH, saved.getId(), "DEACTIVATE",
                                            user.getId(), prev, next))
                                    .thenReturn(":white_check_mark: Deactivated `" + hash + "`");
                        })
                        .switchIfEmpty(Mono.just(":warning: HASH not found: `" + hash + "`")))
                .onErrorResume(e -> {
                    log.error("Failed to deactivate HASH {} by {}: {}", hash, slackUserId, e.getMessage(), e);
                    return Mono.just(":x: Error while deactivating `" + hash + "`: " + e.getMessage());
                });
    }

    public Mono<String> reactivateHash(String slackUserId, String teamId, String hash, String reason) {
        final String normHash = normalize(hash);
        if (!IocUtils.isValidHash(normHash))
            return Mono.just(":warning: Invalid HASH: `" + hash + "`");

        return slackUserService.ensureAndEnrichSlackUser(slackUserId, teamId)
                .flatMap(user -> hashRepository.findByNormalizedHash(normHash)
                        .flatMap(found -> {
                            if (Boolean.TRUE.equals(found.getActive())) {
                                return Mono.just(":information_source: HASH already active: `" + hash + "`");
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

                            return hashRepository.save(found)
                                    .flatMap(saved -> auditHelper.log(IocType.HASH, saved.getId(), "REACTIVATE",
                                            user.getId(), prev, next))
                                    .thenReturn(":white_check_mark: Reactivated `" + hash + "`");
                        })
                        .switchIfEmpty(Mono.just(":warning: HASH not found: `" + hash + "`")))
                .onErrorResume(e -> {
                    log.error("Failed to reactivate HASH {} by {}: {}", hash, slackUserId, e.getMessage(), e);
                    return Mono.just(":x: Error while reactivating `" + hash + "`: " + e.getMessage());
                });
    }

    public Mono<String> editHash(String slackUserId, String teamId, String hash, String newReason) {
        final String normHash = normalize(hash);
        if (!IocUtils.isValidHash(normHash))
            return Mono.just(":warning: Invalid HASH: `" + hash + "`");

        return slackUserService.ensureAndEnrichSlackUser(slackUserId, teamId)
                .flatMap(user -> hashRepository.findByNormalizedHash(normHash)
                        .flatMap(found -> {
                            String prev = "{" + IocUtils.jsonKV("reason", found.getReason(), true) + "}";
                            found.setReason(newReason);
                            found.setUpdatedAt(LocalDateTime.now());
                            String next = "{" + IocUtils.jsonKV("reason", newReason, true) + "}";
                            return hashRepository.save(found)
                                    .flatMap(saved -> auditHelper.log(IocType.HASH, saved.getId(), "UPDATE",
                                            user.getId(), prev, next))
                                    .thenReturn(":white_check_mark: Updated `" + hash + "` reason");
                        })
                        .switchIfEmpty(Mono.just(":warning: HASH not found: `" + hash + "`")))
                .onErrorResume(e -> {
                    log.error("Failed to edit HASH {} by {}: {}", hash, slackUserId, e.getMessage(), e);
                    return Mono.just(":x: Error while editing `" + hash + "`: " + e.getMessage());
                });
    }

    public Mono<String> listHashes(boolean onlyActive, int limit) {
        return (onlyActive ? hashRepository.findByActiveTrueOrderByHashValueAsc() : hashRepository.findAll())
                .map(HashEntity::getHashValue)
                .take(limit > 0 ? limit : Long.MAX_VALUE)
                .collectList()
                .map(list -> list.isEmpty()
                        ? "_(no hashes found)_"
                        : "```\n" + String.join("\n", list) + "\n```")
                .onErrorResume(e -> {
                    log.error("Error listing hashes: {}", e.getMessage(), e);
                    return Mono.just(":x: Error retrieving list: " + e.getMessage());
                });
    }

    private static List<String> parseCsv(String csv) {
        if (csv == null || csv.isBlank())
            return List.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .collect(Collectors.toList());
    }

    public Mono<String> bulkAdd(String slackUserId, String teamId, List<String> hashes, String reason) {
        if (hashes == null || hashes.isEmpty()) {
            return Mono.just(":warning: No hashes provided for bulk operation.");
        }

        final int maxBatch = 500;
        if (hashes.size() > maxBatch) {
            return Mono.just(":warning: Bulk limit exceeded. Max " + maxBatch + " hashes allowed per bulk.");
        }

        return slackUserService.ensureAndEnrichSlackUser(slackUserId, teamId)
                .flatMapMany(user -> {
                    AtomicInteger added = new AtomicInteger(0);
                    AtomicInteger reactivated = new AtomicInteger(0);
                    AtomicInteger alreadyActive = new AtomicInteger(0);
                    AtomicInteger invalid = new AtomicInteger(0);
                    AtomicInteger errors = new AtomicInteger(0);

                    return Flux.fromIterable(hashes)
                            .flatMap(hash -> {
                                final String normHash = normalize(hash);
                                if (!IocUtils.isValidHash(normHash)) {
                                    invalid.incrementAndGet();
                                    return Mono.just(String.format(":warning: Invalid `%s`", hash));
                                }

                                return hashRepository.findByNormalizedHash(normHash)
                                        .flatMap(found -> {
                                            if (Boolean.TRUE.equals(found.getActive())) {
                                                alreadyActive.incrementAndGet();
                                                return Mono.just(
                                                        String.format(":information_source: Already active `%s`",
                                                                hash));
                                            }
                                            found.setActive(true);
                                            found.setReason(reason);

                                            return hashRepository.save(found)
                                                    .flatMap(saved -> auditHelper.log(
                                                            IocType.HASH,
                                                            saved.getId(),
                                                            "REACTIVATE",
                                                            user.getId(),
                                                            "{\"active\":0}", "{\"active\":1}"))
                                                    .then(Mono.fromCallable(() -> {
                                                        reactivated.incrementAndGet();
                                                        return String.format(":white_check_mark: Reactivated `%s`",
                                                                hash);
                                                    }));
                                        })
                                        .switchIfEmpty(
                                                hashRepository.save(HashEntity.builder()
                                                        .hashValue(normHash)
                                                        .reason(reason)
                                                        .active(true)
                                                        .createdBy(user.getId())
                                                        .createdAt(LocalDateTime.now())
                                                        .build())
                                                        .flatMap(saved -> auditHelper.log(
                                                                IocType.HASH,
                                                                saved.getId(),
                                                                "CREATE",
                                                                user.getId(),
                                                                null,
                                                                "{" + String.join(",",
                                                                        IocUtils.jsonKV("hash", normHash, true),
                                                                        IocUtils.jsonKV("reason", reason, true),
                                                                        IocUtils.jsonKV("active", "1", false)) + "}"))
                                                        .then(Mono.fromCallable(() -> {
                                                            added.incrementAndGet();
                                                            return String.format(":white_check_mark: Added `%s`", hash);
                                                        })))
                                        .onErrorResume(e -> {
                                            errors.incrementAndGet();
                                            log.error("Error handling hash {} in bulk: {}", hash, e.getMessage(), e);
                                            return Mono.just(String.format(":x: Error `%s`: %s", hash, e.getMessage()));
                                        });
                            }, /* concurrency */ 10)
                            .collectList()
                            .map(individualResults -> {
                                StringBuilder sb = new StringBuilder();
                                sb.append("*Bulk result overview*\n");
                                sb.append(String.format("• Total requested: %d\n", hashes.size()));
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
                    log.error("bulkAdd (hash) failed for user {}: {}", slackUserId, e.getMessage(), e);
                    return Mono.just(":x: Bulk operation failed: " + e.getMessage());
                });
    }
}