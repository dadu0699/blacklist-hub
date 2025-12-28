package com.blacklisthub.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import com.blacklisthub.entity.UrlEntity;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UrlRepository extends ReactiveCrudRepository<UrlEntity, Long> {

    Flux<UrlEntity> findByActiveTrueOrderByUrlValueAsc();

    Mono<UrlEntity> findByUrlValue(String urlValue);
}