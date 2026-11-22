package com.forum.repository;

import com.forum.model.ForumUserSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ForumUserSettingsRepository extends JpaRepository<ForumUserSettings, String> {
}