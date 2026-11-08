package com.forum.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "post")
public class Post {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private String id = UUID.randomUUID().toString();

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "author_id", nullable = false)
    private String authorId;

    @Column(name = "category_id", nullable = false)
    private Integer categoryId;

    @Column(name = "is_pinned", nullable = false)
    private boolean isPinned = false;

    @Column(name = "pinned_by")
    private String pinnedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "post_id")
    private List<Reply> replies = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "post_vote", joinColumns = @JoinColumn(name = "post_id"))
    @Column(name = "user_id")
    private Set<String> upvotes = new HashSet<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "post_id")
    private List<Flag> flags = new ArrayList<>();

    public Post() {}

    public Post(String title, String body, String authorId, Integer categoryId) {
        this.title = title;
        this.body = body;
        this.authorId = authorId;
        this.categoryId = categoryId;
    }

    // Getters and Setters required by frontend and JSON serialization
    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getAuthorId() { return authorId; }
    public Integer getCategoryId() { return categoryId; }
    
    // Crucial for frontend: post.pinned looks for isPinned() or getPinned()
    public boolean isPinned() { return isPinned; }
    public void setPinned(boolean pinned) { isPinned = pinned; }
    
    public String getPinnedBy() { return pinnedBy; }
    public void setPinnedBy(String pinnedBy) { this.pinnedBy = pinnedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public List<Reply> getReplies() { return replies; }
    public Set<String> getUpvotes() { return upvotes; }
    public List<String> getFlaggedBy() {
        List<String> userIds = new ArrayList<>();
        for (Flag f : flags) {
            userIds.add(f.getFlaggedBy());
        }
        return userIds;
    }
    public List<Flag> getFlags() { return flags; }
}