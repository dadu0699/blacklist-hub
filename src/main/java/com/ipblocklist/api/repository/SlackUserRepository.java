package com.ipblocklist.api.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import com.ipblocklist.api.entity.SlackUserEntity;

import reactor.core.publisher.Mono;

public interface SlackUserRepository extends ReactiveCrudRepository<SlackUserEntity, Long> {
    Mono<SlackUserEntity> findBySlackUserId(String slackUserId);
}
