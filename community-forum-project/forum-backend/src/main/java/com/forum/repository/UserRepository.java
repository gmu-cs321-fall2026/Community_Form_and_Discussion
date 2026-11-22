package com.forum.repository;

import com.forum.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<AppUser, String> {
    Optional<AppUser> findByName(String name);
    Optional<AppUser> findByGmuEmail(String gmuEmail);
}