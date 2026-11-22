package com.forum.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "reply")
public class Reply {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private String id = UUID.randomUUID().toString();

    @Column(name = "post_id", nullable = false)
    private String postId;

    @Column(name = "author_id", nullable = false)
    private String authorId;

    @Transient
    private String authorName;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "is_helpful", nullable = false)
    private boolean helpful = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @ElementCollection
    @CollectionTable(name = "reply_vote", joinColumns = @JoinColumn(name = "reply_id"))
    @Column(name = "user_id")
    private Set<String> upvotes = new HashSet<>();

    public Reply() {}

    public Reply(String postId, String authorId, String body) {
        this.id = UUID.randomUUID().toString();
        this.postId = postId;
        this.authorId = authorId;
        this.body = body;
        this.createdAt = LocalDateTime.now();
    }

    public void toggleUpvote(String userId) {
        if (upvotes.contains(userId)) {
            upvotes.remove(userId);
        } else {
            upvotes.add(userId);
        }
    }

    // Getters
    public String getId() { return id; }
    public String getPostId() { return postId; }
    public String getAuthorId() { return authorId; }
    public String getBody() { return body; }
    public boolean isHelpful() { return helpful; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public Set<String> getUpvotes() { return upvotes; }
    public String getAuthorName() {
    if (authorName != null && !authorName.isBlank()) {
        return authorName;
    }
    return "Unknown User";
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }
    public void setHelpful(boolean helpful) {
        this.helpful = helpful;
    }
}