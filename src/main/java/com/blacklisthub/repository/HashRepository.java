package com.blacklisthub.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import com.blacklisthub.entity.HashEntity;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface HashRepository extends ReactiveCrudRepository<HashEntity, Long> {
    Flux<HashEntity> findByActiveTrueOrderByHashValueAsc();

    /**
     * Finds a hash by its normalized (lowercase) value.
     * The service layer is responsible for passing a lowercase hash.
     */
    @Query("SELECT * FROM hash_indicators WHERE hash_norm = :normalizedHash")
    Mono<HashEntity> findByNormalizedHash(String normalizedHash);
}