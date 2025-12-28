package com.blacklisthub.slack.service;

import static com.blacklisthub.slack.util.CommandTextUtils.firstArg;
import static com.blacklisthub.slack.util.CommandTextUtils.tailOrNull;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.blacklisthub.entity.DomainEntity;
import com.blacklisthub.entity.IocType;
import com.blacklisthub.repository.DomainRepository;
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
public class DomainCommandService {

    private final DomainRepository domainRepository;
    private final AuditHelper auditHelper;
    private final SlackUserService slackUserService;

    public Mono<String> execute(Parsed p, String slackUserId, String teamId, String channelId) {
        final String sub = p.sub() == null ? "" : p.sub();
        switch (sub) {
            case "add" -> {
                final String domain = firstArg(p);
                final String reason = tailOrNull(p);
                log.info("CMD add domain={} by user={} in channel={}", domain, slackUserId, channelId);
                return addDomain(slackUserId, teamId, domain, reason);
            }
            case "deactivate" -> {
                final String domain = firstArg(p);
                final String reason = tailOrNull(p);
                log.info("CMD deactivate domain={} by user={} in channel={}", domain, slackUserId, channelId);
                return deactivateDomain(slackUserId, teamId, domain, reason);
            }
            case "reactivate" -> {
                final String domain = firstArg(p);
                final String reason = tailOrNull(p);
                log.info("CMD reactivate domain={} by user={} in channel={}", domain, slackUserId, channelId);
                return reactivateDomain(slackUserId, teamId, domain, reason);
            }
            case "edit" -> {
                final String domain = firstArg(p);
                final String newReason = tailOrNull(p);
                log.info("CMD edit domain={} by user={} in channel={}", domain, slackUserId, channelId);
                return editDomain(slackUserId, teamId, domain, newReason);
            }
            case "list" -> {
                log.info("CMD list domain by user={} in channel={}", slackUserId, channelId);
                return listDomains(true, 200);
            }
            case "bulk" -> {
                final String csv = firstArg(p);
                final List<String> domains = parseCsv(csv);
                final String reason = tailOrNull(p);
                log.info("CMD bulk add {} domains by user={} in channel={}", domains.size(), slackUserId, channelId);
                return bulkAdd(slackUserId, teamId, domains, reason);
            }
            case "" -> {
                return Mono.just("""
                        Usage:
                        • /domain add <DOMAIN> [reason]
                        • /domain deactivate <DOMAIN> [reason]
                        • /domain reactivate <DOMAIN> [reason]
                        • /domain edit <DOMAIN> <new reason>
                        • /domain list
                        """);
            }
            default -> {
                return Mono.just(":warning: Unknown subcommand: `" + sub + "`\n" +
                        "Try `/domain list` or see `/domain` usage.");
            }
        }
    }

    private String normalize(String domain) {
        return domain.toLowerCase();
    }

    public Mono<String> addDomain(String slackUserId, String teamId, String domain, String reason) {
        final String normDomain = normalize(domain);
        if (!IocUtils.isValidDomain(normDomain))
            return Mono.just(":warning: Invalid DOMAIN: `" + domain + "`");

        return slackUserService.ensureAndEnrichSlackUser(slackUserId, teamId)
                .flatMap(user -> domainRepository.findByNormalizedDomain(normDomain)
                        .flatMap(found -> {
                            if (Boolean.TRUE.equals(found.getActive())) {
                                return Mono.just(":information_source: DOMAIN already active: `" + domain + "`");
                            }
                            found.setActive(true);
                            found.setReason(reason);
                            return domainRepository.save(found)
                                    .flatMap(saved -> auditHelper.log(IocType.DOMAIN, saved.getId(), "REACTIVATE",
                                            user.getId(), "{\"active\":0}", "{\"active\":1}"))
                                    .thenReturn(":white_check_mark: Reactivated `" + domain + "`");
                        })
                        .switchIfEmpty(
                                domainRepository.save(DomainEntity.builder()
                                        .domainName(normDomain)
                                        .reason(reason)
                                        .active(true)
                                        .createdBy(user.getId())
                                        .createdAt(LocalDateTime.now())
                                        .build())
                                        .flatMap(saved -> auditHelper.log(IocType.DOMAIN, saved.getId(), "CREATE",
                                                user.getId(), null,
                                                "{" + String.join(",",
                                                        IocUtils.jsonKV("domain", normDomain, true),
                                                        IocUtils.jsonKV("reason", reason, true),
                                                        IocUtils.jsonKV("active", "1", false)) + "}"))
                                        .thenReturn(":white_check_mark: Added `" + domain + "`")))
                .onErrorResume(e -> {
                    log.error("Failed to add DOMAIN {} by {}: {}", domain, slackUserId, e.getMessage(), e);
                    return Mono.just(":x: Error while adding `" + domain + "`: " + e.getMessage());
                });
    }

    public Mono<String> deactivateDomain(String slackUserId, String teamId, String domain, String reason) {
        final String normDomain = normalize(domain);
        if (!IocUtils.isValidDomain(normDomain))
            return Mono.just(":warning: Invalid DOMAIN: `" + domain + "`");

        return slackUserService.ensureAndEnrichSlackUser(slackUserId, teamId)
                .flatMap(user -> domainRepository.findByNormalizedDomain(normDomain)
                        .flatMap(found -> {
                            if (!Boolean.TRUE.equals(found.getActive())) {
                                return Mono.just(":information_source: DOMAIN already inactive: `" + domain + "`");
                            }
                            found.setActive(false);
                            if (reason != null && !reason.isBlank())
                                found.setReason(reason);

                            String prev = "{\"active\":1}";
                            String next = "{" + String.join(",",
                                    IocUtils.jsonKV("active", "0", false),
                                    IocUtils.jsonKV("reason", found.getReason(), true)) + "}";

                            return domainRepository.save(found)
                                    .flatMap(saved -> auditHelper.log(IocType.DOMAIN, saved.getId(), "DEACTIVATE",
                                            user.getId(), prev, next))
                                    .thenReturn(":white_check_mark: Deactivated `" + domain + "`");
                        })
                        .switchIfEmpty(Mono.just(":warning: DOMAIN not found: `" + domain + "`")))
                .onErrorResume(e -> {
                    log.error("Failed to deactivate DOMAIN {} by {}: {}", domain, slackUserId, e.getMessage(), e);
                    return Mono.just(":x: Error while deactivating `" + domain + "`: " + e.getMessage());
                });
    }

    public Mono<String> reactivateDomain(String slackUserId, String teamId, String domain, String reason) {
        final String normDomain = normalize(domain);
        if (!IocUtils.isValidDomain(normDomain))
            return Mono.just(":warning: Invalid DOMAIN: `" + domain + "`");

        return slackUserService.ensureAndEnrichSlackUser(slackUserId, teamId)
                .flatMap(user -> domainRepository.findByNormalizedDomain(normDomain)
                        .flatMap(found -> {
                            if (Boolean.TRUE.equals(found.getActive())) {
                                return Mono.just(":information_source: DOMAIN already active: `" + domain + "`");
                            }
                            found.setActive(true);
                            if (reason != null && !reason.isBlank())
                                found.setReason(reason);

                            String prev = "{\"active\":0}";
                            String next = "{" + String.join(",",
                                    IocUtils.jsonKV("active", "1", false),
                                    IocUtils.jsonKV("reason", found.getReason(), true)) + "}";

                            return domainRepository.save(found)
                                    .flatMap(saved -> auditHelper.log(IocType.DOMAIN, saved.getId(), "REACTIVATE",
                                            user.getId(), prev, next))
                                    .thenReturn(":white_check_mark: Reactivated `" + domain + "`");
                        })
                        .switchIfEmpty(Mono.just(":warning: DOMAIN not found: `" + domain + "`")))
                .onErrorResume(e -> {
                    log.error("Failed to reactivate DOMAIN {} by {}: {}", domain, slackUserId, e.getMessage(), e);
                    return Mono.just(":x: Error while reactivating `" + domain + "`: " + e.getMessage());
                });
    }

    public Mono<String> editDomain(String slackUserId, String teamId, String domain, String newReason) {
        final String normDomain = normalize(domain);
        if (!IocUtils.isValidDomain(normDomain))
            return Mono.just(":warning: Invalid DOMAIN: `" + domain + "`");

        return slackUserService.ensureAndEnrichSlackUser(slackUserId, teamId)
                .flatMap(user -> domainRepository.findByNormalizedDomain(normDomain)
                        .flatMap(found -> {
                            String prev = "{" + IocUtils.jsonKV("reason", found.getReason(), true) + "}";
                            found.setReason(newReason);
                            found.setUpdatedAt(LocalDateTime.now());
                            String next = "{" + IocUtils.jsonKV("reason", newReason, true) + "}";
                            return domainRepository.save(found)
                                    .flatMap(saved -> auditHelper.log(IocType.DOMAIN, saved.getId(), "UPDATE",
                                            user.getId(), prev, next))
                                    .thenReturn(":white_check_mark: Updated `" + domain + "` reason");
                        })
                        .switchIfEmpty(Mono.just(":warning: DOMAIN not found: `" + domain + "`")))
                .onErrorResume(e -> {
                    log.error("Failed to edit DOMAIN {} by {}: {}", domain, slackUserId, e.getMessage(), e);
                    return Mono.just(":x: Error while editing `" + domain + "`: " + e.getMessage());
                });
    }

    public Mono<String> listDomains(boolean onlyActive, int limit) {
        return (onlyActive ? domainRepository.findByActiveTrueOrderByDomainNameAsc() : domainRepository.findAll())
                .map(DomainEntity::getDomainName)
                .take(limit > 0 ? limit : Long.MAX_VALUE)
                .collectList()
                .map(list -> list.isEmpty()
                        ? "_(no domains found)_"
                        : "```\n" + String.join("\n", list) + "\n```")
                .onErrorResume(e -> {
                    log.error("Error listing domains: {}", e.getMessage(), e);
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

    public Mono<String> bulkAdd(String slackUserId, String teamId, List<String> domains, String reason) {
        if (domains == null || domains.isEmpty()) {
            return Mono.just(":warning: No domains provided for bulk operation.");
        }

        final int maxBatch = 500;
        if (domains.size() > maxBatch) {
            return Mono.just(":warning: Bulk limit exceeded. Max " + maxBatch + " domains allowed per bulk.");
        }

        return slackUserService.ensureAndEnrichSlackUser(slackUserId, teamId)
                .flatMapMany(user -> {

                    AtomicInteger added = new AtomicInteger(0);
                    AtomicInteger reactivated = new AtomicInteger(0);
                    AtomicInteger alreadyActive = new AtomicInteger(0);
                    AtomicInteger invalid = new AtomicInteger(0);
                    AtomicInteger errors = new AtomicInteger(0);

                    return Flux.fromIterable(domains)
                            .flatMap(domain -> {
                                final String normDomain = normalize(domain);
                                if (!IocUtils.isValidDomain(normDomain)) {
                                    invalid.incrementAndGet();
                                    return Mono.just(String.format(":warning: Invalid `%s`", domain));
                                }

                                return domainRepository.findByNormalizedDomain(normDomain)
                                        .flatMap(found -> {
                                            if (Boolean.TRUE.equals(found.getActive())) {
                                                alreadyActive.incrementAndGet();
                                                return Mono.just(
                                                        String.format(":information_source: Already active `%s`",
                                                                domain));
                                            }

                                            found.setActive(true);
                                            found.setReason(reason);
                                            found.setUpdatedAt(LocalDateTime.now());
                                            found.setDeactivatedBy(null);
                                            found.setDeactivatedAt(null);

                                            return domainRepository.save(found)
                                                    .flatMap(saved -> auditHelper.log(IocType.DOMAIN, saved.getId(),
                                                            "REACTIVATE", user.getId(),
                                                            "{\"active\":0}", "{\"active\":1}"))
                                                    .then(Mono.fromCallable(() -> {
                                                        reactivated.incrementAndGet();
                                                        return String.format(":white_check_mark: Reactivated `%s`",
                                                                domain);
                                                    }));
                                        })
                                        .switchIfEmpty(
                                                domainRepository.save(DomainEntity.builder()
                                                        .domainName(normDomain)
                                                        .reason(reason)
                                                        .active(true)
                                                        .createdBy(user.getId())
                                                        .createdAt(LocalDateTime.now())
                                                        .build())
                                                        .flatMap(saved -> auditHelper.log(IocType.DOMAIN, saved.getId(),
                                                                "CREATE", user.getId(), null,
                                                                "{" + String.join(",",
                                                                        IocUtils.jsonKV("domain", normDomain, true),
                                                                        IocUtils.jsonKV("reason", reason, true),
                                                                        IocUtils.jsonKV("active", "1", false)) + "}"))
                                                        .then(Mono.fromCallable(() -> {
                                                            added.incrementAndGet();
                                                            return String.format(":white_check_mark: Added `%s`",
                                                                    domain);
                                                        })))
                                        .onErrorResume(e -> {
                                            errors.incrementAndGet();
                                            log.error("Error handling domain {} in bulk: {}", domain, e.getMessage(),
                                                    e);
                                            return Mono
                                                    .just(String.format(":x: Error `%s`: %s", domain, e.getMessage()));
                                        });
                            }, /* concurrency */ 10)
                            .collectList()
                            .map(individualResults -> {
                                StringBuilder sb = new StringBuilder();
                                sb.append("*Bulk result overview*\n");
                                sb.append(String.format("• Total requested: %d\n", domains.size()));
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
                    log.error("bulkAdd (domain) failed for user {}: {}", slackUserId, e.getMessage(), e);
                    return Mono.just(":x: Bulk operation failed: " + e.getMessage());
                });
    }
}