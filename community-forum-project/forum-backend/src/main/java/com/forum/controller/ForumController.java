package com.forum.controller;

import com.forum.model.Post;
import com.forum.model.Reply;
import com.forum.model.AppUser;
import com.forum.model.ModerationAction;
import com.forum.repository.UserRepository;
import com.forum.repository.ModerationActionRepository;
import com.forum.service.PostService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;


import java.util.List;
import java.util.ArrayList;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ForumController {

    private final PostService postService;
    private final UserRepository userRepository;

    public ForumController(PostService postService, UserRepository userRepository) {
        this.postService = postService;
        this.userRepository = userRepository;
    }

    @GetMapping("/posts")
    public List<Post> getFilteredPosts(
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false, name = "search") String keyword,
            @RequestParam(required = false, defaultValue = "recent") String sortBy) {
        return postService.getFilteredAndSortedPosts(categoryId, keyword, sortBy);
    }

    @PostMapping("/posts")
    public Post createPost(@RequestBody Map<String, String> payload) {
        int categoryId = switch (payload.getOrDefault("category", "GENERAL")) {
            case "INTERVIEW_PREP" -> 1;
            case "RESUME_AND_APPLICATION" -> 2;
            case "OFFER_AND_NEGOTIATION" -> 3;
            case "INDUSTRY_INSIGHT" -> 4;
            case "CAREER_FAIRS_AND_EVENTS" -> 5;
            default -> 6;
        };
        return postService.createPost(
            payload.get("title"), 
            payload.get("body"), 
            payload.get("authorId"), 
            categoryId
        );
    }

    @PostMapping("/posts/{id}/upvote")
    public Post togglePostUpvote(@PathVariable String id, @RequestBody Map<String, String> payload) {
        return postService.toggleUpvote(id, payload.get("userId"));
    }

    @PostMapping("/posts/{id}/flag")
    public Post flagPost(@PathVariable("id") String postId, @RequestBody Map<String, String> payload) {
        String userId = payload.get("userId");
        String reason = payload.getOrDefault("reason", "Inappropriate content");
        return postService.flagPost(postId, userId, reason);
    }

    @PostMapping("/posts/{id}/pin")
    public Post togglePin(@PathVariable String id, @RequestBody Map<String, String> payload) {
        boolean pinStatus = Boolean.parseBoolean(payload.getOrDefault("isPinned", "true"));
        return postService.togglePin(id, pinStatus, payload.get("userRole"), payload.get("userId"));
    }

    @PostMapping("/posts/{id}/replies")
    public Post addReply(@PathVariable String id, @RequestBody Map<String, String> payload) {
        return postService.addReply(id, payload.get("authorId"), payload.get("body"));
    }

    @PostMapping("/posts/{postId}/replies/{replyId}/upvote")
    public Post toggleReplyUpvote(@PathVariable String postId, @PathVariable String replyId, @RequestBody Map<String, String> payload) {
        return postService.toggleReplyUpvote(postId, replyId, payload.get("userId"));
    }

    @GetMapping("/users/{userId}/profile")
    public Map<String, Object> getUserProfile(@PathVariable String userId) {
        List<Post> allPosts = postService.getAllPosts();
        List<Post> userPosts = allPosts.stream().filter(p -> p.getAuthorId().equals(userId)).toList();
        List<Map<String, Object>> userReplies = new ArrayList<>();
        for (Post p : allPosts) {
            for (Reply r : p.getReplies()) {
                if (r.getAuthorId().equals(userId)) {
                    userReplies.add(Map.of("postTitle", p.getTitle(),"replyBody", r.getBody(),"createdAt", r.getCreatedAt()));
                }
            }
        }
        return Map.of("posts", userPosts, "replies", userReplies);
    }

    @PostMapping("/users/{userId}/privacy")
    public ResponseEntity<?> toggleUserPrivacy(@PathVariable String userId, @RequestBody Map<String, Boolean> payload) {
        boolean hideHistory = payload.getOrDefault("hideHistory", false);
        postService.updateHistoryPrivacy(userId, hideHistory);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/users")
    public List<AppUser> getAllUsers() {
        return userRepository.findAll();
    }

    @GetMapping("/moderation/queue")
    public List<Post> getModerationQueue() {
        return postService.getFlaggedQueue();
    }

    @GetMapping("/moderation/log")
    public List<ModerationAction> getModerationLog() {
        return postService.getModerationLog();
    }

    @PostMapping("/moderation/{postId}/action")
    public ResponseEntity<?> executeModerationAction(@PathVariable String postId, @RequestBody Map<String, String> payload) {
        String action = payload.get("action");
        String reason = payload.get("reason");
        String moderator = payload.getOrDefault("moderator", "local-test-admin");

        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("A brief reason is required.");
        }

        postService.handleModerationAction(postId, action, reason.trim(), moderator);
        return ResponseEntity.ok(Map.of("message", "Decision recorded."));
    }
}