package com.forum.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "flag")
public class Flag {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private String id = UUID.randomUUID().toString();

    @Column(name = "post_id", nullable = false)
    private String postId;

    @Column(name = "reply_id")
    private String replyId;

    @Column(name = "flagged_by", nullable = false)
    private String flaggedBy;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "status", nullable = false)
    private String status = "open";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Flag() {}

    public Flag(String postId, String flaggedBy, String reason) {
        this.postId = postId;
        this.flaggedBy = flaggedBy;
        this.reason = reason;
    }

    // Getters and Setters
    public String getId() { return id; }
    public String getPostId() { return postId; }
    public String getFlaggedBy() { return flaggedBy; }
    public String getReason() { return reason; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}