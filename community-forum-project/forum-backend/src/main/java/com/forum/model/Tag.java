package com.forum.model;

import jakarta.persistence.*;

@Entity
@Table(name = "tag")
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "name", nullable = false, unique = true, length = 80)
    private String name;

    @Column(name = "tag_type", nullable = false, length = 10)
    private String tagType = "other"; // 'employer', 'role', 'other'

    public Tag() {}

    public Tag(String name, String tagType) {
        this.name = name;
        this.tagType = tagType;
    }

    public Integer getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getTagType() { return tagType; }
    public void setTagType(String tagType) { this.tagType = tagType; }
}