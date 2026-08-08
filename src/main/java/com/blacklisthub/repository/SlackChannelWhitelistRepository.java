package com.blacklisthub.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import com.blacklisthub.entity.SlackChannelWhitelistEntity;

import reactor.core.publisher.Mono;

public interface SlackChannelWhitelistRepository
        extends ReactiveCrudRepository<SlackChannelWhitelistEntity, Long> {

    Mono<Boolean> existsByChannelIdAndActiveTrue(String channelId);

    Mono<SlackChannelWhitelistEntity> findByChannelId(String channelId);
}
