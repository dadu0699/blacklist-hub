package com.blacklisthub.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.data.r2dbc.mapping.R2dbcMappingContext;
import org.springframework.data.relational.core.mapping.RelationalPersistentEntity;
import org.springframework.data.relational.core.mapping.RelationalPersistentProperty;

import com.blacklisthub.entity.IocAuditLogEntity;
import com.blacklisthub.entity.IpEntity;
import com.blacklisthub.entity.SlackChannelWhitelistEntity;
import com.blacklisthub.entity.SlackUserEntity;

/**
 * T-02 (R2 / C-map): verifies how the application resolves entity property names
 * to DB column names. The app defines no custom NamingStrategy bean, so this uses
 * the same default that Spring Boot's auto-configured R2dbcMappingContext uses.
 *
 * <p>
 * The DB schema is snake_case (see Flyway migrations). If the default strategy
 * does NOT convert camelCase to snake_case, the entities without explicit
 * {@code @Column} annotations would map to non-existent columns at runtime.
 */
class EntityColumnMappingDiagnosticTest {

    private final R2dbcMappingContext ctx = new R2dbcMappingContext();

    private String columnOf(Class<?> entity, String property) {
        RelationalPersistentEntity<?> pe = ctx.getRequiredPersistentEntity(entity);
        RelationalPersistentProperty prop = pe.getRequiredPersistentProperty(property);
        return prop.getColumnName().getReference();
    }

    @Test
    void controlAnnotatedColumnIsResolvedCorrectly() {
        // IpEntity.createdBy carries @Column("created_by"); this must always hold.
        assertThat(columnOf(IpEntity.class, "createdBy")).isEqualTo("created_by");
    }

    @Test
    void unannotatedEntitiesMustMapToSnakeCaseColumns() {
        assertThat(columnOf(SlackUserEntity.class, "slackUserId")).isEqualTo("slack_user_id");
        assertThat(columnOf(IocAuditLogEntity.class, "iocType")).isEqualTo("ioc_type");
        assertThat(columnOf(SlackChannelWhitelistEntity.class, "channelId")).isEqualTo("channel_id");
    }
}
