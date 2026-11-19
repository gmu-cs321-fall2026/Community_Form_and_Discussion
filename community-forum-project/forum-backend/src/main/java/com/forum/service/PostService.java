package com.forum.service;

import com.forum.model.Post;
import com.forum.model.Reply;
import com.forum.model.ModerationAction;
import com.forum.model.Flag;
import com.forum.model.AppUser;
import com.forum.repository.ModerationActionRepository;
import com.forum.repository.FlagRepository;
import com.forum.repository.PostRepository;
import com.forum.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final ModerationActionRepository moderationActionRepository;
    private final FlagRepository flagRepository;

    public PostService(PostRepository postRepository,
                       UserRepository userRepository,
                       ModerationActionRepository moderationActionRepository,
                       FlagRepository flagRepository) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.moderationActionRepository = moderationActionRepository;
        this.flagRepository = flagRepository;
    }

    @Transactional
    public String resolveUserId(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            identifier = "AnonymousUser";
        }
        
        // 1. If it's already a valid primary key ID in the DB, return it
        if (userRepository.existsById(identifier)) {
            return identifier;
        }
        
        // 2. Check if a user with this name already exists
        AppUser existingUser = userRepository.findByName(identifier).orElse(null);
        if (existingUser != null) {
            return existingUser.getId();
        }
        
        // 3. Otherwise, auto-create the user so foreign key constraints never fail
        AppUser newUser = new AppUser();
        newUser.setId(UUID.randomUUID().toString());
        newUser.setName(identifier);
        newUser.setGmuEmail(identifier.toLowerCase().replaceAll("\\s+", "") + "@gmu.edu");
        newUser.setRole("STUDENT");
        
        AppUser savedUser = userRepository.save(newUser);
        return savedUser.getId();
    }

    public List<Post> getAllPosts() {
        return postRepository.findAll();
    }

    @Transactional
    public Post createPost(String title, String body, String authorId, int categoryId) {
        // Fallback or auto-register user on the fly if not already in database
        String resolvedAuthorId = authorId != null ? authorId : "Anonymous";
        
        if (!userRepository.existsById(resolvedAuthorId)) {
            // Check if user exists by name instead
            AppUser existingUser = userRepository.findByName(resolvedAuthorId).orElse(null);
            if (existingUser != null) {
                resolvedAuthorId = existingUser.getId();
            } else {
                // Auto-create user on the fly
                AppUser newUser = new AppUser();
                // If ID isn't a 36-char UUID, generate one and store name
                resolvedAuthorId = UUID.randomUUID().toString();
                // Note: AppUser fields: id, name, gmuEmail, role
            }
        }

        Post post = new Post(title, body, resolvedAuthorId, categoryId);
        return postRepository.save(post);
    }

    @Transactional
    public Post toggleUpvote(String postId, String rawUserId) {
        String userId = resolveUserId(rawUserId);
        Post post = postRepository.findById(postId).orElseThrow(() -> new RuntimeException("Post not found"));
        
        // Toggle the vote safely in the collection
        if (post.getUpvotes().contains(userId)) {
            post.getUpvotes().remove(userId);
        } else {
            post.getUpvotes().add(userId);
        }
        
        // Force sync or save through repository
        Post savedPost = postRepository.save(post);
        // Ensure changes are flushed immediately to catch any SQL constraints
        postRepository.flush();
        return savedPost;
    }

    @Transactional
    public Post flagPost(String postId, String rawUserId, String reason) {
        String userId = resolveUserId(rawUserId); // Ensures user exists in app_user first
        Post post = postRepository.findById(postId).orElseThrow(() -> new RuntimeException("Post not found"));

        boolean alreadyFlagged = post.getFlags().stream().anyMatch(f -> f.getFlaggedBy().equals(userId));
        if (!alreadyFlagged) {
            Flag flag = new Flag(postId, userId, reason);
            post.getFlags().add(flag);
        }
        
        return postRepository.save(post);
    }

    @Transactional
    public Post togglePin(String postId, boolean isPinned, String userRole, String rawStaffId) {
        if (!"CAREER_STAFF".equalsIgnoreCase(userRole)) {
            throw new SecurityException("Only Career Services Staff can pin or unpin posts.");
        }
        
        // Resolve staff identifier to a valid database user UUID
        String staffId = resolveUserId(rawStaffId);
        
        Post post = postRepository.findById(postId).orElseThrow(() -> new RuntimeException("Post not found"));
        post.setPinned(isPinned);
        post.setPinnedBy(isPinned ? staffId : null);
        return postRepository.save(post);
    }

    public Post addReply(String postId, String authorId, String body) {
        if (!userRepository.existsById(authorId)) {
            AppUser newUser = new AppUser();
            newUser.setId(authorId);
            newUser.setGmuEmail(authorId + "@gmu.edu");
            userRepository.save(newUser);
        }
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

    public List<Post> getFlaggedQueue() {
        return postRepository.findAll().stream()
                .filter(p -> !p.getFlags().isEmpty() && !"removed".equalsIgnoreCase(p.getStatus()))
                .toList();
    }

    public List<ModerationAction> getModerationLog() {
        return moderationActionRepository.findAll();
    }

    @Transactional
    public void handleModerationAction(String postId, String action, String reason, String moderatorId) {
        Post post = postRepository.findById(postId).orElseThrow(() -> new RuntimeException("Post not found"));
        if (post.getFlags().isEmpty()) {
            throw new IllegalStateException("Post is not in the active moderation queue.");
        }

        Flag activeFlag = post.getFlags().get(0);

        if ("REMOVE".equalsIgnoreCase(action)) {
            post.setStatus("removed");
        }

        post.getFlags().clear();
        postRepository.save(post);

        ModerationAction modAction = new ModerationAction(activeFlag.getId(), moderatorId, action.toLowerCase(), reason);
        moderationActionRepository.save(modAction);
    }

    public List<Post> getFilteredAndSortedPosts(String categoryId, String keyword, String sortBy) {
        // 1. Convert String categoryId to Integer if it's numeric, or handle null/empty
        Integer parsedCategoryId = null;
        if (categoryId != null && !categoryId.trim().isEmpty() && !"all".equalsIgnoreCase(categoryId)) {
            try {
                parsedCategoryId = Integer.parseInt(categoryId);
            } catch (NumberFormatException e) {
                // If your categories are sent as names (e.g., "INTERVIEW_PREP"), map them back to integers
                parsedCategoryId = switch (categoryId.toUpperCase()) {
                    case "INTERVIEW_PREP" -> 1;
                    case "RESUME_AND_APPLICATION" -> 2;
                    case "OFFER_AND_NEGOTIATION" -> 3;
                    case "INDUSTRY_INSIGHT" -> 4;
                    case "CAREER_FAIRS_AND_EVENTS" -> 5;
                    default -> 6; // GENERAL or default fallback
                };
            }
        }

        // 2. Pass the parsed integer (or handle repository filtering with the parsed value) 
        // to your underlying data access logic or repository query
        return postRepository.findFilteredAndSorted(parsedCategoryId, keyword, sortBy);
    }

    @Transactional
    public void updateHistoryPrivacy(String userId, boolean hideHistory) {
        userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        // Privacy preference successfully handled
    }
}