package com.forum.service;

import com.forum.model.Post;
import com.forum.model.Reply;
import com.forum.model.Flag;
import com.forum.repository.PostRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class PostService {

    private final PostRepository postRepository;

    public PostService(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    public List<Post> getAllPosts() {
        return postRepository.findAll();
    }

    public Post createPost(String title, String body, String authorId, int categoryId) {
        Post post = new Post(title, body, authorId, categoryId);
        return postRepository.save(post);
    }

    public Post toggleUpvote(String postId, String userId) {
        Post post = postRepository.findById(postId).orElseThrow(() -> new RuntimeException("Post not found"));
        if (post.getUpvotes().contains(userId)) {
            post.getUpvotes().remove(userId);
        } else {
            post.getUpvotes().add(userId);
        }
        return postRepository.save(post);
    }

    @Transactional
    public Post flagPost(String postId, String userId, String reason) {
        Post post = postRepository.findById(postId).orElseThrow(() -> new RuntimeException("Post not found"));

        // Check if user already flagged this post to avoid duplicates (optional)
        boolean alreadyFlagged = post.getFlags().stream().anyMatch(f -> f.getFlaggedBy().equals(userId));
        if (!alreadyFlagged) {
            Flag flag = new Flag(postId, userId, reason);
            post.getFlags().add(flag);
        }
        
        return postRepository.save(post);
    }

    public Post togglePin(String postId, boolean isPinned, String userRole, String staffId) {
        if (!"CAREER_STAFF".equalsIgnoreCase(userRole)) {
            throw new SecurityException("Only Career Services Staff can pin or unpin posts.");
        }
        Post post = postRepository.findById(postId).orElseThrow(() -> new RuntimeException("Post not found"));
        post.setPinned(isPinned);
        post.setPinnedBy(isPinned ? staffId : null);
        return postRepository.save(post);
    }

    public Post addReply(String postId, String authorId, String body) {
        Post post = postRepository.findById(postId).orElseThrow(() -> new RuntimeException("Post not found"));
        Reply reply = new Reply(postId, authorId, body);
        post.getReplies().add(reply);
        return postRepository.save(post);
    }
    public Post toggleReplyUpvote(String postId, String replyId, String userId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));
        
        for (Reply reply : post.getReplies()) {
            if (reply.getId().equals(replyId)) {
                reply.toggleUpvote(userId);
                break;
            }
        }
        return postRepository.save(post);
    }
}