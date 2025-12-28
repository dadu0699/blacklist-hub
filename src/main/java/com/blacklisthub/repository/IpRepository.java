package com.blacklisthub.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import com.blacklisthub.entity.IpEntity;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface IpRepository extends ReactiveCrudRepository<IpEntity, Long> {
    Flux<IpEntity> findByActiveTrueOrderByIpAsc();

    /**
     * Finds an IP entity by its value using database-side normalization.
     * This query uses INET6_ATON to match the `ip_bin` column,
     * correctly handling IPv4/IPv6 normalization.
     */
    @Query("SELECT * FROM ip_addresses WHERE ip_bin = INET6_ATON(:ip)")
    Mono<IpEntity> findByIpNormalized(String ip);
}