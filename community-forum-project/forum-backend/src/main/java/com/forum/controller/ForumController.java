package com.forum.controller;

import com.forum.model.Post;
import com.forum.model.Reply;
import com.forum.service.PostService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ForumController {

    private final PostService postService;

    public ForumController(PostService postService) {
        this.postService = postService;
    }

    @GetMapping("/posts")
    public List<Post> getAllPosts() {
        return postService.getAllPosts();
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
}