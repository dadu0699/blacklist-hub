package com.blacklisthub.slack.service;

import java.time.LocalDateTime;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.blacklisthub.entity.SlackUserEntity;
import com.blacklisthub.repository.SlackUserRepository;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.request.users.UsersInfoRequest;
import com.slack.api.methods.response.users.UsersInfoResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class SlackUserService {

    private final SlackUserRepository slackUserRepository;
    private final MethodsClient slackMethods;

    public Mono<SlackUserEntity> ensureAndEnrichSlackUser(String slackUserId, String teamId) {
        return slackUserRepository.findBySlackUserId(slackUserId)
                .switchIfEmpty(slackUserRepository.save(SlackUserEntity.builder()
                        .slackUserId(slackUserId)
                        .teamId(teamId)
                        .displayName(slackUserId)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build()))
                .flatMap(u -> Mono
                        .fromCallable(
                                () -> slackMethods.usersInfo(UsersInfoRequest.builder().user(slackUserId).build()))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMap(resp -> updateNamesIfChanged(u, resp))
                        .onErrorResume(e -> {
                            log.warn("users.info failed for {}: {}", slackUserId, e.getMessage());
                            return Mono.just(u);
                        }));
    }

    private Mono<SlackUserEntity> updateNamesIfChanged(SlackUserEntity u, UsersInfoResponse resp) {
        if (resp == null || !resp.isOk() || resp.getUser() == null)
            return Mono.just(u);
        var profile = resp.getUser().getProfile();
        String newDisplay = profile != null ? profile.getDisplayNameNormalized() : null;
        String newReal = profile != null ? profile.getRealNameNormalized() : null;

        boolean changed = !Objects.equals(newDisplay, u.getDisplayName()) || !Objects.equals(newReal, u.getRealName());
        if (!changed)
            return Mono.just(u);

        u.setDisplayName(newDisplay != null ? newDisplay : u.getDisplayName());
        u.setRealName(newReal != null ? newReal : u.getRealName());
        u.setUpdatedAt(LocalDateTime.now());
        return slackUserRepository.save(u);
    }
}
