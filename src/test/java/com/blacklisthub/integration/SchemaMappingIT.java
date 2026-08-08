package com.blacklisthub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.blacklisthub.entity.SlackUserEntity;
import com.blacklisthub.repository.SlackUserRepository;

import reactor.test.StepVerifier;

/**
 * T-09 smoke integration test: proves the Testcontainers + Flyway + R2DBC harness
 * works end-to-end. It also closes the gap left by T-02 by confirming that an
 * entity WITHOUT explicit {@code @Column} annotations (SlackUserEntity) actually
 * persists to and reads from the snake_case schema against a real MySQL.
 *
 * <p>
 * The class is skipped (not failed) when no Docker daemon is available, so a
 * plain {@code mvn verify} stays green in Docker-less environments; CI provides
 * Docker and runs it for real.
 */
@Testcontainers(disabledWithoutDocker = true)
@DataR2dbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
class SchemaMappingIT {

    @Container
    @SuppressWarnings("resource")
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("blacklist_hub");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> "r2dbc:mysql://%s:%d/%s".formatted(
                MYSQL.getHost(), MYSQL.getMappedPort(MySQLContainer.MYSQL_PORT), MYSQL.getDatabaseName()));
        registry.add("spring.r2dbc.username", MYSQL::getUsername);
        registry.add("spring.r2dbc.password", MYSQL::getPassword);
        registry.add("spring.flyway.url", MYSQL::getJdbcUrl);
        registry.add("spring.flyway.user", MYSQL::getUsername);
        registry.add("spring.flyway.password", MYSQL::getPassword);
    }

    @Autowired
    SlackUserRepository slackUserRepository;

    @Test
    void flywayMigratesAndUnannotatedEntityRoundTripsThroughSnakeCaseColumns() {
        SlackUserEntity toSave = SlackUserEntity.builder()
                .slackUserId("U12345")
                .teamId("T0001")
                .displayName("Jane Doe")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        StepVerifier.create(slackUserRepository.save(toSave)
                .then(slackUserRepository.findBySlackUserId("U12345")))
                .assertNext(found -> {
                    assertThat(found.getId()).isNotNull();
                    assertThat(found.getSlackUserId()).isEqualTo("U12345");
                    assertThat(found.getDisplayName()).isEqualTo("Jane Doe");
                    assertThat(found.getTeamId()).isEqualTo("T0001");
                })
                .verifyComplete();
    }
}
