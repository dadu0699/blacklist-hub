package com.blacklisthub.entity;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("ip_addresses")
public class IpEntity {
    @Id
    private Long id;

    private String ip;
    private String reason;
    private Boolean active;

    @Column("created_by")
    private Long createdBy;
    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Column("deactivated_by")
    private Long deactivatedBy;
    @Column("deactivated_at")
    private LocalDateTime deactivatedAt;

}
