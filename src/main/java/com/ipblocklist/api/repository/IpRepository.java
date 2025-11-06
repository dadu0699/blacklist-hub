package com.ipblocklist.api.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import com.ipblocklist.api.entity.IpEntity;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface IpRepository extends ReactiveCrudRepository<IpEntity, Long> {
    Flux<IpEntity> findByActiveTrueOrderByIpAsc();

    Mono<IpEntity> findByIp(String ip);
}
