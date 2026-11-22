package com.forum.repository;

import com.forum.model.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostRepository extends JpaRepository<Post, String> {

    @Query("SELECT p FROM Post p WHERE " +
           "(:categoryId IS NULL OR p.categoryId = :categoryId) AND " +
           "(:keyword IS NULL OR :keyword = '' OR LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(p.body) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Post> findFilteredAndSorted(
        @Param("categoryId") Integer categoryId,
        @Param("keyword") String keyword,
        @Param("sortBy") String sortBy
    );
}