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

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "is_helpful", nullable = false)
    private boolean isHelpful = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @ElementCollection
    @CollectionTable(name = "reply_vote", joinColumns = @JoinColumn(name = "reply_id"))
    @Column(name = "user_id")
    private Set<String> upvotes = new HashSet<>();

    public Reply() {}

    public Reply(String postId, String authorId, String body) {
        this.postId = postId;
        this.authorId = authorId;
        this.body = body;
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
    public boolean isHelpful() { return isHelpful; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public Set<String> getUpvotes() { return upvotes; }
}