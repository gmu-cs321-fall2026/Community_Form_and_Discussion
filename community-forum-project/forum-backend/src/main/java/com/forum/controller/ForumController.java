package com.forum.controller;

import com.forum.model.Post;
import com.forum.model.Reply;
import com.forum.model.AppUser;
import com.forum.model.ModerationAction;
import com.forum.model.ForumUserSettings;
import com.forum.repository.ForumUserSettingsRepository;
import com.forum.repository.UserRepository;
import com.forum.service.PostService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;

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
    public Post createPost(@RequestBody Map<String, Object> payload) {
        String categoryStr = payload.get("category") != null ? payload.get("category").toString() : "GENERAL";
        int categoryId = switch (categoryStr) {
            case "INTERVIEW_PREP" -> 1;
            case "RESUME_AND_APPLICATION" -> 2;
            case "OFFER_AND_NEGOTIATION" -> 3;
            case "INDUSTRY_INSIGHT" -> 4;
            case "CAREER_FAIRS_AND_EVENTS" -> 5;
            default -> 6;
        };

        String title = payload.get("title") != null ? payload.get("title").toString() : "";
        String body = payload.get("body") != null ? payload.get("body").toString() : "";
        String authorId = payload.get("authorId") != null ? payload.get("authorId").toString() : "AlexUser";
        String employerTag = payload.get("employerTag") != null ? payload.get("employerTag").toString() : null;
        String roleTag = payload.get("roleTag") != null ? payload.get("roleTag").toString() : null;

        return postService.createPost(title, body, authorId, categoryId, employerTag, roleTag);
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
        AppUser user = userRepository.findById(userId)
                .orElseGet(() -> userRepository.findAll().stream()
                        .filter(u -> userId.equalsIgnoreCase(u.getName())
                                || userId.equalsIgnoreCase(u.getGmuEmail()))
                        .findFirst()
                        .orElse(null));

        String resolvedUserId = (user != null && user.getId() != null) ? user.getId() : userId;
        String displayName = (user != null && user.getName() != null) ? user.getName() : userId;

        boolean hideHistory = postService.isHistoryHidden(resolvedUserId)
                || (!resolvedUserId.equals(userId) && postService.isHistoryHidden(userId));

        List<Post> userPosts = new ArrayList<>();
        List<Map<String, Object>> userReplies = new ArrayList<>();

        if (!hideHistory) {
            List<Post> allPosts = postService.getAllPosts();
            userPosts = allPosts.stream()
                    .filter(p -> resolvedUserId.equals(p.getAuthorId()) || userId.equals(p.getAuthorId()))
                    .filter(p -> !"removed".equalsIgnoreCase(p.getStatus()))
                    .toList();

            for (Post p : allPosts) {
                if (p.getReplies() == null) continue;
                for (Reply r : p.getReplies()) {
                    if (resolvedUserId.equals(r.getAuthorId()) || userId.equals(r.getAuthorId())) {
                        Map<String, Object> replyEntry = new java.util.HashMap<>();
                        replyEntry.put("postTitle", p.getTitle() != null ? p.getTitle() : "");
                        replyEntry.put("replyBody", r.getBody() != null ? r.getBody() : "");
                        replyEntry.put("createdAt", r.getCreatedAt() != null ? r.getCreatedAt() : "");
                        userReplies.add(replyEntry);
                    }
                }
            }
        }

        Map<String, Object> response = new java.util.HashMap<>();
        response.put("userId", resolvedUserId);
        response.put("userName", displayName);
        response.put("hidePostHistory", hideHistory);
        response.put("posts", userPosts);
        response.put("replies", userReplies);
        return response;
    }

    @PostMapping("/users/{userId}/privacy")
    public ResponseEntity<?> toggleUserPrivacy(@PathVariable String userId, @RequestBody Map<String, Boolean> payload) {
        boolean hideHistory = Boolean.TRUE.equals(payload.get("hidePostHistory"))
                || Boolean.TRUE.equals(payload.get("hideHistory"));
        ForumUserSettings saved = postService.updateHistoryPrivacy(userId, hideHistory);
        return ResponseEntity.ok(Map.of(
                "userId", saved.getUserId(),
                "hidePostHistory", saved.isHidePostHistory()
        ));
    }
    @PostMapping("/posts/{postId}/replies/{replyId}/helpful")
    public ResponseEntity<?> toggleReplyHelpful(@PathVariable String postId,
                                              @PathVariable String replyId,
                                              @RequestBody(required = false) Map<String, String> payload) {
        String userId = payload != null ? payload.get("userId") : null;
        try {
            Post updated = postService.toggleReplyHelpful(postId, replyId, userId);
            return ResponseEntity.ok(updated);
        } catch (SecurityException ex) {
            return ResponseEntity.status(403).body(Map.of("error", ex.getMessage()));
        }
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
    public ResponseEntity<?> executeModerationAction(@PathVariable String postId,
                                                     @RequestBody Map<String, String> payload) {
        String action = payload != null ? payload.get("action") : null;
        String reason = payload != null ? payload.get("reason") : null;
        String moderator = (payload != null && payload.get("moderator") != null && !payload.get("moderator").isBlank())
                ? payload.get("moderator").trim()
                : "local-test-admin";

        try {
            postService.handleModerationAction(postId, action, reason, moderator);
            return ResponseEntity.ok(Map.of(
                    "message", "Decision recorded.",
                    "postId", postId,
                    "action", action.trim().toUpperCase()
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(409).body(Map.of("error", ex.getMessage()));
        } catch (DataIntegrityViolationException ex) {
            return ResponseEntity.status(409).body(Map.of("error", "A moderation action has already been recorded for this flag."));
        }catch (RuntimeException ex) {
            if ("Post not found".equalsIgnoreCase(ex.getMessage())) {
                return ResponseEntity.status(404).body(Map.of("error", ex.getMessage()));
            }
            return ResponseEntity.status(500).body(Map.of("error", ex.getMessage()));
        }
    }
}