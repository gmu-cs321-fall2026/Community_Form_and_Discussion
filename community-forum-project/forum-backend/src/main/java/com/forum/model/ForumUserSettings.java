package com.forum.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "forum_user_settings")
public class ForumUserSettings {

    @Id
    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Column(name = "hide_post_history", nullable = false)
    private boolean hidePostHistory = false;

    @Column(name = "reputation", nullable = false)
    private int reputation = 0;

    public ForumUserSettings() {}

    public ForumUserSettings(String userId) {
        this.userId = userId;
        this.hidePostHistory = false;
        this.reputation = 0;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public boolean isHidePostHistory() { return hidePostHistory; }
    public void setHidePostHistory(boolean hidePostHistory) { this.hidePostHistory = hidePostHistory; }

    public int getReputation() { return reputation; }
    public void setReputation(int reputation) { this.reputation = reputation; }
}