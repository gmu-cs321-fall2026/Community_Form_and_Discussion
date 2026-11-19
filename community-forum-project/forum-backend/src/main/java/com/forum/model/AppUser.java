package com.forum.model;

import jakarta.persistence.*;

@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "gmu_email", nullable = false)
    private String gmuEmail;

    @Column(name = "role", nullable = false)
    private String role;

    public AppUser() {}

    public AppUser(String name, String gmuEmail, String role) {
        this.name = name;
        this.gmuEmail = gmuEmail;
        this.role = role;
    }
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getGmuEmail() { return gmuEmail; }
    public void setGmuEmail(String gmuEmail) { this.gmuEmail = gmuEmail; }
    
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}