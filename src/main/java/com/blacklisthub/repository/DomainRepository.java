package com.blacklisthub.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import com.blacklisthub.entity.DomainEntity;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface DomainRepository extends ReactiveCrudRepository<DomainEntity, Long> {
    Flux<DomainEntity> findByActiveTrueOrderByDomainNameAsc();

    /**
     * Finds a domain by its normalized (lowercase) value.
     * The service layer is responsible for passing a lowercase domain.
     */
    @Query("SELECT * FROM domain_indicators WHERE domain_norm = :normalizedDomain")
    Mono<DomainEntity> findByNormalizedDomain(String normalizedDomain);
}