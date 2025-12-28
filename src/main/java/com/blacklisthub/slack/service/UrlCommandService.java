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
import com.blacklisthub.entity.UrlEntity;
import com.blacklisthub.repository.UrlRepository;
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
public class UrlCommandService {

    private final UrlRepository urlRepository;
    private final AuditHelper auditHelper;
    private final SlackUserService slackUserService;

    public Mono<String> execute(Parsed p, String slackUserId, String teamId, String channelId) {
        final String sub = p.sub() == null ? "" : p.sub();
        switch (sub) {
            case "add" -> {
                final String url = firstArg(p);
                final String reason = tailOrNull(p);
                log.info("CMD add url={} by user={} in channel={}", url, slackUserId, channelId);
                return addUrl(slackUserId, teamId, url, reason);
            }
            case "deactivate" -> {
                final String url = firstArg(p);
                final String reason = tailOrNull(p);
                log.info("CMD deactivate url={} by user={} in channel={}", url, slackUserId, channelId);
                return deactivateUrl(slackUserId, teamId, url, reason);
            }
            case "reactivate" -> {
                final String url = firstArg(p);
                final String reason = tailOrNull(p);
                log.info("CMD reactivate url={} by user={} in channel={}", url, slackUserId, channelId);
                return reactivateUrl(slackUserId, teamId, url, reason);
            }
            case "edit" -> {
                final String url = firstArg(p);
                final String newReason = tailOrNull(p);
                log.info("CMD edit url={} by user={} in channel={}", url, slackUserId, channelId);
                return editUrl(slackUserId, teamId, url, newReason);
            }
            case "list" -> {
                log.info("CMD list url by user={} in channel={}", slackUserId, channelId);
                return listUrls(true, 100);
            }
            case "bulk" -> {
                final String csv = firstArg(p);
                final List<String> urls = parseCsv(csv);
                final String reason = tailOrNull(p);
                log.info("CMD bulk add {} urls by user={} in channel={}", urls.size(), slackUserId, channelId);
                return bulkAdd(slackUserId, teamId, urls, reason);
            }
            case "" -> {
                return Mono.just("""
                        Usage:
                        • /url add <URL> [reason]
                        • /url deactivate <URL> [reason]
                        • /url reactivate <URL> [reason]
                        • /url edit <URL> <new reason>
                        • /url list
                        """);
            }
            default -> {
                return Mono.just(":warning: Unknown subcommand: `" + sub + "`\n" +
                        "Try `/url list` or see `/url` usage.");
            }
        }
    }

    private String normalize(String url) {
        return IocUtils.normalizeUrl(url);
    }

    public Mono<String> addUrl(String slackUserId, String teamId, String url, String reason) {
        final String normUrl = normalize(url);
        if (!IocUtils.isValidUrl(normUrl))
            return Mono.just(":warning: Invalid URL: `" + url + "`");

        return slackUserService.ensureAndEnrichSlackUser(slackUserId, teamId)
                .flatMap(user -> urlRepository.findByUrlValue(normUrl)
                        .flatMap(found -> {
                            if (Boolean.TRUE.equals(found.getActive())) {
                                return Mono.just(":information_source: URL already active: `" + url + "`");
                            }
                            found.setActive(true);
                            found.setReason(reason);
                            // ...
                            return urlRepository.save(found)
                                    .flatMap(saved -> auditHelper.log(IocType.URL, saved.getId(), "REACTIVATE",
                                            user.getId(), "{\"active\":0}", "{\"active\":1}"))
                                    .thenReturn(":white_check_mark: Reactivated `" + url + "`");
                        })
                        .switchIfEmpty(
                                urlRepository.save(UrlEntity.builder()
                                        .urlValue(normUrl)
                                        .reason(reason)
                                        .active(true)
                                        .createdBy(user.getId())
                                        .createdAt(LocalDateTime.now())
                                        .build())
                                        .flatMap(saved -> auditHelper.log(
                                                IocType.URL,
                                                saved.getId(),
                                                "CREATE",
                                                user.getId(), null,
                                                "{" + String.join(",",
                                                        IocUtils.jsonKV("url", normUrl, true),
                                                        IocUtils.jsonKV("reason", reason, true),
                                                        IocUtils.jsonKV("active", "1", false)) + "}"))
                                        .thenReturn(":white_check_mark: Added `" + url + "`")))
                .onErrorResume(e -> {
                    log.error("Failed to add URL {} by {}: {}", url, slackUserId, e.getMessage(), e);
                    return Mono.just(":x: Error while adding `" + url + "`: " + e.getMessage());
                });
    }

    public Mono<String> deactivateUrl(String slackUserId, String teamId, String url, String reason) {
        final String normUrl = normalize(url);
        if (!IocUtils.isValidUrl(normUrl))
            return Mono.just(":warning: Invalid URL: `" + url + "`");

        return slackUserService.ensureAndEnrichSlackUser(slackUserId, teamId)
                .flatMap(user -> urlRepository.findByUrlValue(normUrl)
                        .flatMap(found -> {
                            if (!Boolean.TRUE.equals(found.getActive())) {
                                return Mono.just(":information_source: URL already inactive: `" + url + "`");
                            }
                            found.setActive(false);
                            if (reason != null && !reason.isBlank())
                                found.setReason(reason);

                            String prev = "{\"active\":1}";
                            String next = "{" + String.join(",",
                                    IocUtils.jsonKV("active", "0", false),
                                    IocUtils.jsonKV("reason", found.getReason(), true)) + "}";

                            return urlRepository.save(found)
                                    .flatMap(saved -> auditHelper.log(IocType.URL, saved.getId(), "DEACTIVATE",
                                            user.getId(), prev, next))
                                    .thenReturn(":white_check_mark: Deactivated `" + url + "`");
                        })
                        .switchIfEmpty(Mono.just(":warning: URL not found: `" + url + "`")))
                .onErrorResume(e -> {
                    log.error("Failed to deactivate URL {} by {}: {}", url, slackUserId, e.getMessage(), e);
                    return Mono.just(":x: Error while deactivating `" + url + "`: " + e.getMessage());
                });
    }

    public Mono<String> reactivateUrl(String slackUserId, String teamId, String url, String reason) {
        final String normUrl = normalize(url);
        if (!IocUtils.isValidUrl(normUrl))
            return Mono.just(":warning: Invalid URL: `" + url + "`");

        return slackUserService.ensureAndEnrichSlackUser(slackUserId, teamId)
                .flatMap(user -> urlRepository.findByUrlValue(normUrl)
                        .flatMap(found -> {
                            if (Boolean.TRUE.equals(found.getActive())) {
                                return Mono.just(":information_source: URL already active: `" + url + "`");
                            }
                            found.setActive(true);
                            if (reason != null && !reason.isBlank())
                                found.setReason(reason);

                            String prev = "{\"active\":0}";
                            String next = "{" + String.join(",",
                                    IocUtils.jsonKV("active", "1", false),
                                    IocUtils.jsonKV("reason", found.getReason(), true)) + "}";

                            return urlRepository.save(found)
                                    .flatMap(saved -> auditHelper.log(IocType.URL, saved.getId(), "REACTIVATE",
                                            user.getId(), prev, next))
                                    .thenReturn(":white_check_mark: Reactivated `" + url + "`");
                        })
                        .switchIfEmpty(Mono.just(":warning: URL not found: `" + url + "`")))
                .onErrorResume(e -> {
                    log.error("Failed to reactivate URL {} by {}: {}", url, slackUserId, e.getMessage(), e);
                    return Mono.just(":x: Error while reactivating `" + url + "`: " + e.getMessage());
                });
    }

    public Mono<String> editUrl(String slackUserId, String teamId, String url, String newReason) {
        final String normUrl = normalize(url);
        if (!IocUtils.isValidUrl(normUrl))
            return Mono.just(":warning: Invalid URL: `" + url + "`");

        return slackUserService.ensureAndEnrichSlackUser(slackUserId, teamId)
                .flatMap(user -> urlRepository.findByUrlValue(normUrl)
                        .flatMap(found -> {
                            String prev = "{" + IocUtils.jsonKV("reason", found.getReason(), true) + "}";
                            found.setReason(newReason);
                            found.setUpdatedAt(LocalDateTime.now());
                            String next = "{" + IocUtils.jsonKV("reason", newReason, true) + "}";
                            return urlRepository.save(found)
                                    .flatMap(saved -> auditHelper.log(IocType.URL, saved.getId(), "UPDATE",
                                            user.getId(), prev, next))
                                    .thenReturn(":white_check_mark: Updated `" + url + "` reason");
                        })
                        .switchIfEmpty(Mono.just(":warning: URL not found: `" + url + "`")))
                .onErrorResume(e -> {
                    log.error("Failed to edit URL {} by {}: {}", url, slackUserId, e.getMessage(), e);
                    return Mono.just(":x: Error while editing `" + url + "`: " + e.getMessage());
                });
    }

    public Mono<String> listUrls(boolean onlyActive, int limit) {
        return (onlyActive ? urlRepository.findByActiveTrueOrderByUrlValueAsc() : urlRepository.findAll())
                .map(UrlEntity::getUrlValue)
                .take(limit > 0 ? limit : Long.MAX_VALUE)
                .collectList()
                .map(list -> list.isEmpty()
                        ? "_(no URLs found)_"
                        : "```\n" + String.join("\n", list) + "\n```")
                .onErrorResume(e -> {
                    log.error("Error listing URLs: {}", e.getMessage(), e);
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

    public Mono<String> bulkAdd(String slackUserId, String teamId, List<String> urls, String reason) {
        if (urls == null || urls.isEmpty()) {
            return Mono.just(":warning: No URLs provided for bulk operation.");
        }

        final int maxBatch = 500;
        if (urls.size() > maxBatch) {
            return Mono.just(":warning: Bulk limit exceeded. Max " + maxBatch + " URLs allowed per bulk.");
        }

        return slackUserService.ensureAndEnrichSlackUser(slackUserId, teamId)
                .flatMapMany(user -> {

                    AtomicInteger added = new AtomicInteger(0);
                    AtomicInteger reactivated = new AtomicInteger(0);
                    AtomicInteger alreadyActive = new AtomicInteger(0);
                    AtomicInteger invalid = new AtomicInteger(0);
                    AtomicInteger errors = new AtomicInteger(0);

                    return Flux.fromIterable(urls)
                            .flatMap(url -> {
                                final String normUrl = normalize(url);
                                if (!IocUtils.isValidUrl(normUrl)) {
                                    invalid.incrementAndGet();
                                    return Mono.just(String.format(":warning: Invalid `%s`", url));
                                }

                                return urlRepository.findByUrlValue(normUrl)
                                        .flatMap(found -> {
                                            if (Boolean.TRUE.equals(found.getActive())) {
                                                alreadyActive.incrementAndGet();
                                                return Mono.just(
                                                        String.format(":information_source: Already active `%s`", url));
                                            }

                                            found.setActive(true);
                                            found.setReason(reason);
                                            found.setUpdatedAt(LocalDateTime.now());
                                            found.setDeactivatedBy(null);
                                            found.setDeactivatedAt(null);

                                            return urlRepository.save(found)
                                                    .flatMap(saved -> auditHelper.log(IocType.URL, saved.getId(),
                                                            "REACTIVATE", user.getId(),
                                                            "{\"active\":0}", "{\"active\":1}"))
                                                    .then(Mono.fromCallable(() -> {
                                                        reactivated.incrementAndGet();
                                                        return String.format(":white_check_mark: Reactivated `%s`",
                                                                url);
                                                    }));
                                        })
                                        .switchIfEmpty(
                                                urlRepository.save(UrlEntity.builder()
                                                        .urlValue(normUrl)
                                                        .reason(reason)
                                                        .active(true)
                                                        .createdBy(user.getId())
                                                        .createdAt(LocalDateTime.now())
                                                        .build())
                                                        .flatMap(saved -> auditHelper.log(IocType.URL, saved.getId(),
                                                                "CREATE", user.getId(), null,
                                                                "{" + String.join(",",
                                                                        IocUtils.jsonKV("url", normUrl, true),
                                                                        IocUtils.jsonKV("reason", reason, true),
                                                                        IocUtils.jsonKV("active", "1", false)) + "}"))
                                                        .then(Mono.fromCallable(() -> {
                                                            added.incrementAndGet();
                                                            return String.format(":white_check_mark: Added `%s`", url);
                                                        })))
                                        .onErrorResume(e -> {
                                            errors.incrementAndGet();
                                            log.error("Error handling URL {} in bulk: {}", url, e.getMessage(), e);
                                            return Mono.just(String.format(":x: Error `%s`: %s", url, e.getMessage()));
                                        });
                            }, /* concurrency */ 10)
                            .collectList()
                            .map(individualResults -> {
                                StringBuilder sb = new StringBuilder();
                                sb.append("*Bulk result overview*\n");
                                sb.append(String.format("• Total requested: %d\n", urls.size()));
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
                    log.error("bulkAdd (url) failed for user {}: {}", slackUserId, e.getMessage(), e);
                    return Mono.just(":x: Bulk operation failed: " + e.getMessage());
                });
    }

}