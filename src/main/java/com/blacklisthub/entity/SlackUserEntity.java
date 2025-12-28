package com.blacklisthub.entity;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("slack_users")
public class SlackUserEntity {
    @Id
    private Long id;

    private String slackUserId;
    private String teamId;
    private String displayName;
    private String realName;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
