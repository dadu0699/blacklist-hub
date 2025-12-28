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
@Table("slack_channel_whitelist")
public class SlackChannelWhitelistEntity {
    @Id
    private Long id;

    private String channelId;
    private String channelName;
    private String teamId;
    private Boolean active;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
