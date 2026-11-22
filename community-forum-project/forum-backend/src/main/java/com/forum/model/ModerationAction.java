package com.forum.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "moderation_action")
public class ModerationAction {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private String id = UUID.randomUUID().toString();

    @Column(name = "flag_id", nullable = false, columnDefinition = "CHAR(36)")
    private String flagId;

    @Column(name = "moderator_id", nullable = false)
    private String moderatorId;

    @Column(name = "action", nullable = false)
    private String action; // 'remove', 'warn', 'dismiss'

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Transient
    private String moderatorName;

    public ModerationAction() {}

    public ModerationAction(String flagId, String moderatorId, String action, String reason) {
        this.id = UUID.randomUUID().toString();
        this.flagId = flagId;
        this.moderatorId = moderatorId;
        this.action = action;
        this.reason = reason;
        this.createdAt = LocalDateTime.now();
    }

    // Getters
    public String getId() { return id; }
    public String getFlagId() { return flagId; }
    public String getModeratorId() { return moderatorId; }
    public String getModeratorName() { return moderatorName; }
    public String getAction() { return action; }
    public String getReason() { return reason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setModeratorName(String moderatorName) {
        this.moderatorName = moderatorName;
    }
}