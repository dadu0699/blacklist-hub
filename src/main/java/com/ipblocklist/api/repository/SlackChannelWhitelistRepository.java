package com.ipblocklist.api.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

import com.ipblocklist.api.entity.SlackChannelWhitelistEntity;

import reactor.core.publisher.Mono;

@Repository
public interface SlackChannelWhitelistRepository
        extends ReactiveCrudRepository<SlackChannelWhitelistEntity, Long> {

    Mono<Boolean> existsByChannelIdAndActiveTrue(String channelId);

    Mono<SlackChannelWhitelistEntity> findByChannelId(String channelId);
}
