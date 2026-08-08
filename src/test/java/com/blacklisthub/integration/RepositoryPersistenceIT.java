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

import com.blacklisthub.entity.IocAuditLogEntity;
import com.blacklisthub.entity.IocType;
import com.blacklisthub.entity.IpEntity;
import com.blacklisthub.entity.SlackChannelWhitelistEntity;
import com.blacklisthub.entity.SlackUserEntity;
import com.blacklisthub.entity.UrlEntity;
import com.blacklisthub.repository.IocAuditLogRepository;
import com.blacklisthub.repository.IpRepository;
import com.blacklisthub.repository.SlackChannelWhitelistRepository;
import com.blacklisthub.repository.SlackUserRepository;
import com.blacklisthub.repository.UrlRepository;

import reactor.test.StepVerifier;

/**
 * T-10: repository/persistence integration tests against a real MySQL, covering
 * the non-trivial database features the app relies on:
 * <ul>
 * <li>the generated {@code ip_bin} column (INET6_ATON) and the custom
 * {@code findByIpNormalized} query;</li>
 * <li>the polymorphic audit log, including the {@code IocType} enum mapping to
 * the {@code ENUM} column (the end-to-end gap left open by T-02);</li>
 * <li>the channel-whitelist authorization query.</li>
 * </ul>
 */
@Testcontainers(disabledWithoutDocker = true)
@DataR2dbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
class RepositoryPersistenceIT {

    @Container
    @SuppressWarnings("resource")
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
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
    @Autowired
    IpRepository ipRepository;
    @Autowired
    IocAuditLogRepository auditRepository;
    @Autowired
    SlackChannelWhitelistRepository channelWhitelistRepository;
    @Autowired
    UrlRepository urlRepository;

    private static SlackUserEntity newUser(String slackUserId, String displayName) {
        return SlackUserEntity.builder()
                .slackUserId(slackUserId)
                .teamId("T0001")
                .displayName(displayName)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void ipIsFoundThroughTheGeneratedBinaryColumnQuery() {
        StepVerifier.create(
                slackUserRepository.save(newUser("U-ip", "ip creator"))
                        .flatMap(user -> ipRepository.save(IpEntity.builder()
                                .ip("203.0.113.7")
                                .reason("abuse")
                                .active(true)
                                .createdBy(user.getId())
                                .createdAt(LocalDateTime.now())
                                .build()))
                        .then(ipRepository.findByIpNormalized("203.0.113.7")))
                .assertNext(found -> {
                    assertThat(found.getId()).isNotNull();
                    assertThat(found.getIp()).isEqualTo("203.0.113.7");
                    assertThat(found.getActive()).isTrue();
                    assertThat(found.getReason()).isEqualTo("abuse");
                })
                .verifyComplete();
    }

    @Test
    void polymorphicAuditRowPersistsEnumAndJson() {
        StepVerifier.create(
                slackUserRepository.save(newUser("U-audit", "auditor"))
                        .flatMap(user -> auditRepository.save(IocAuditLogEntity.builder()
                                .iocType(IocType.HASH)
                                .indicatorId(4242L)
                                .action("CREATE")
                                .actorUserId(user.getId())
                                .prevValue(null)
                                .newValue("{\"active\":1}")
                                .createdAt(LocalDateTime.now())
                                .build()))
                        .flatMap(saved -> auditRepository.findById(saved.getId())))
                .assertNext(audit -> {
                    assertThat(audit.getIocType()).isEqualTo(IocType.HASH);
                    assertThat(audit.getAction()).isEqualTo("CREATE");
                    assertThat(audit.getIndicatorId()).isEqualTo(4242L);
                    assertThat(audit.getNewValue()).contains("active");
                })
                .verifyComplete();
    }

    @Test
    void duplicateUrlIsRejectedByTheUniqueConstraint() {
        StepVerifier.create(
                slackUserRepository.save(newUser("U-url", "url creator"))
                        .flatMap(user -> urlRepository.save(newUrl("http://dup.example.com/a", user.getId()))
                                .then(urlRepository.save(newUrl("http://dup.example.com/a", user.getId())))))
                .expectError()
                .verify();
    }

    private static UrlEntity newUrl(String urlValue, Long createdBy) {
        return UrlEntity.builder()
                .urlValue(urlValue)
                .reason("dup test")
                .active(true)
                .createdBy(createdBy)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void channelWhitelistExistenceReflectsActiveRows() {
        StepVerifier.create(
                channelWhitelistRepository.save(SlackChannelWhitelistEntity.builder()
                        .channelId("C-allowed")
                        .channelName("secops")
                        .active(true)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build())
                        .then(channelWhitelistRepository.existsByChannelIdAndActiveTrue("C-allowed")))
                .assertNext(exists -> assertThat(exists).isTrue())
                .verifyComplete();

        StepVerifier.create(channelWhitelistRepository.existsByChannelIdAndActiveTrue("C-unknown"))
                .assertNext(exists -> assertThat(exists).isFalse())
                .verifyComplete();
    }
}
