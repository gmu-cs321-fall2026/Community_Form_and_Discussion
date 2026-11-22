package com.forum.service;

import com.forum.model.Post;
import com.forum.model.Reply;
import com.forum.model.ModerationAction;
import com.forum.model.Flag;
import com.forum.model.AppUser;
import com.forum.model.ForumUserSettings;
import com.forum.model.Tag;
import com.forum.repository.ForumUserSettingsRepository;
import com.forum.repository.ModerationActionRepository;
import com.forum.repository.FlagRepository;
import com.forum.repository.PostRepository;
import com.forum.repository.TagRepository;
import com.forum.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final ModerationActionRepository moderationActionRepository;
    private final FlagRepository flagRepository;
    private final ForumUserSettingsRepository forumUserSettingsRepository;
    private final TagRepository tagRepository;

    public PostService(PostRepository postRepository,
                       UserRepository userRepository,
                       ForumUserSettingsRepository forumUserSettingsRepository,
                       ModerationActionRepository moderationActionRepository,
                       FlagRepository flagRepository,
                       TagRepository tagRepository) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.forumUserSettingsRepository = forumUserSettingsRepository;
        this.moderationActionRepository = moderationActionRepository;
        this.flagRepository = flagRepository;
        this.tagRepository = tagRepository;
    }

    @Transactional
    public String resolveUserId(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            identifier = "AnonymousUser";
        }

        if (userRepository.existsById(identifier)) {
            return identifier;
        }

        AppUser existingUser = userRepository.findByName(identifier).orElse(null);
        if (existingUser != null) {
            return existingUser.getId();
        }

        AppUser newUser = new AppUser();
        newUser.setId(UUID.randomUUID().toString());
        newUser.setName(identifier);
        newUser.setGmuEmail(identifier.toLowerCase().replaceAll("\\s+", "") + "@gmu.edu");
        newUser.setRole("STUDENT");

        AppUser savedUser = userRepository.save(newUser);
        return savedUser.getId();
    }

    public String findExistingUserIdOrKeep(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            return identifier;
        }
        String trimmed = identifier.trim();
        return userRepository.findById(trimmed)
                .or(() -> userRepository.findByName(trimmed))
                .or(() -> userRepository.findByGmuEmail(trimmed))
                .map(AppUser::getId)
                .orElse(trimmed);
    }

    public List<Post> getAllPosts() {
        List<Post> posts = postRepository.findAll();
        attachAuthorNames(posts);
        return posts;
    }

    @Transactional
    public Post createPost(String title, String body, String authorId, int categoryId) {
        return createPost(title, body, authorId, categoryId, null, null);
    }

    @Transactional
    public Post createPost(String title, String body, String authorId, int categoryId,
                           String employerTag, String roleTag) {
        String resolvedAuthorId = resolveUserId(authorId);
        Post post = new Post(title, body, resolvedAuthorId, categoryId);

        Tag employer = resolveOrCreateTag(employerTag, "employer");
        Tag role = resolveOrCreateTag(roleTag, "role");

        if (employer != null) {
            post.getTags().add(employer);
        }
        if (role != null) {
            post.getTags().add(role);
        }

        return postRepository.save(post);
    }

    private Tag resolveOrCreateTag(String rawName, String tagType) {
        if (rawName == null) {
            return null;
        }
        String cleaned = rawName.trim().replaceAll("\\s+", " ");
        if (cleaned.isEmpty()) {
            return null;
        }
        if (cleaned.length() > 80) {
            cleaned = cleaned.substring(0, 80).trim();
        }

        final String finalName = cleaned;
        return tagRepository.findByNameIgnoreCase(finalName)
                .orElseGet(() -> {
                    Tag created = new Tag(finalName, tagType);
                    return tagRepository.save(created);
                });
    }

    @Transactional
    public Post toggleUpvote(String postId, String rawUserId) {
        String userId = resolveUserId(rawUserId);
        Post post = postRepository.findById(postId).orElseThrow(() -> new RuntimeException("Post not found"));

        if (post.getUpvotes().contains(userId)) {
            post.getUpvotes().remove(userId);
        } else {
            post.getUpvotes().add(userId);
        }

        Post savedPost = postRepository.save(post);
        postRepository.flush();
        return savedPost;
    }

    @Transactional
    public Post flagPost(String postId, String rawUserId, String reason) {
        String userId = resolveUserId(rawUserId);
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

        String staffId = resolveUserId(rawStaffId);

        Post post = postRepository.findById(postId).orElseThrow(() -> new RuntimeException("Post not found"));
        post.setPinned(isPinned);
        post.setPinnedBy(isPinned ? staffId : null);
        return postRepository.save(post);
    }

    @Transactional
    public Post addReply(String postId, String authorId, String body) {
        String candidateEmail = authorId.contains("@") ? authorId : authorId + "@gmu.edu";

        AppUser user = userRepository.findById(authorId)
                .or(() -> userRepository.findByGmuEmail(candidateEmail))
                .orElseGet(() -> {
                    AppUser newUser = new AppUser();
                    newUser.setId(authorId);
                    newUser.setName(authorId);
                    newUser.setGmuEmail(candidateEmail);
                    newUser.setRole("STUDENT");
                    return userRepository.save(newUser);
                });

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        Reply reply = new Reply(postId, user.getId(), body);
        post.getReplies().add(reply);

        return postRepository.save(post);
    }

    @Transactional
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

    private boolean isPostAuthor(Post post, String rawUserId) {
        if (post == null || rawUserId == null || rawUserId.isBlank()) {
            return false;
        }
        String trimmedCaller = rawUserId.trim();
        String postAuthorId = post.getAuthorId();
        if (postAuthorId == null) {
            return false;
        }

        if (postAuthorId.equalsIgnoreCase(trimmedCaller)) {
            return true;
        }

        String resolvedCallerId = findExistingUserIdOrKeep(trimmedCaller);
        if (postAuthorId.equalsIgnoreCase(resolvedCallerId)) {
            return true;
        }

        return userRepository.findById(postAuthorId)
                .map(author -> trimmedCaller.equalsIgnoreCase(author.getName())
                        || trimmedCaller.equalsIgnoreCase(author.getGmuEmail()))
                .orElse(false);
    }

    @Transactional
    public Post toggleReplyHelpful(String postId, String replyId, String rawUserId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        if (!isPostAuthor(post, rawUserId)) {
            throw new SecurityException("Only the post creator can mark or unmark replies as helpful.");
        }

        if (post.getReplies() != null) {
            for (Reply reply : post.getReplies()) {
                if (reply.getId().equals(replyId)) {
                    reply.setHelpful(!reply.isHelpful());
                    break;
                }
            }
        }

        return postRepository.save(post);
    }

    public List<Post> getFlaggedQueue() {
        List<Post> flaggedPosts = postRepository.findAll().stream()
                .filter(p -> !"removed".equalsIgnoreCase(p.getStatus()))
                .filter(p -> p.getFlags() != null && p.getFlags().stream().anyMatch(f ->
                        f.getId() != null
                                && !"dismissed".equalsIgnoreCase(f.getStatus())
                                && !"actioned".equalsIgnoreCase(f.getStatus())
                                && !moderationActionRepository.existsByFlagId(f.getId())))
                .toList();
        attachAuthorNames(flaggedPosts);
        return flaggedPosts;
    }

    public List<ModerationAction> getModerationLog() {
        List<ModerationAction> actions = moderationActionRepository.findAll();
        Map<String, String> userNames = new HashMap<>();
        for (AppUser user : userRepository.findAll()) {
            userNames.put(user.getId(), user.getName());
        }

        for (ModerationAction action : actions) {
            String name = userNames.get(action.getModeratorId());
            if (name != null && !name.isBlank()) {
                action.setModeratorName(name);
            } else {
                action.setModeratorName(action.getModeratorId());
            }
        }
        return actions;
    }

    @Transactional(noRollbackFor = IllegalStateException.class)
    public void handleModerationAction(String postId, String action, String reason, String moderatorId) {
        if (postId == null || postId.isBlank()) {
            throw new IllegalArgumentException("Post ID is required.");
        }
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("A moderation action is required.");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("A brief reason is required.");
        }

        String normalizedAction = action.trim().toUpperCase();
        if (!normalizedAction.equals("REMOVE")
                && !normalizedAction.equals("WARN")
                && !normalizedAction.equals("DISMISS")) {
            throw new IllegalArgumentException("Unsupported moderation action: " + action);
        }

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        if (post.getFlags() == null || post.getFlags().isEmpty()) {
            throw new IllegalStateException("Post is not in the active moderation queue.");
        }

        Flag activeFlag = post.getFlags().stream()
                .filter(f -> f.getId() != null
                        && !"dismissed".equalsIgnoreCase(f.getStatus())
                        && !"actioned".equalsIgnoreCase(f.getStatus())
                        && !moderationActionRepository.existsByFlagId(f.getId()))
                .findFirst()
                .orElse(null);

        if (activeFlag == null) {
            throw new IllegalStateException("This flag has already been moderated and cleared from the queue.");
        }

        String resolvedModerator = (moderatorId != null && !moderatorId.isBlank())
                ? resolveUserId(moderatorId.trim())
                : resolveUserId("local-test-admin");

        activeFlag.setStatus("DISMISS".equals(normalizedAction) ? "dismissed" : "actioned");

        if ("REMOVE".equals(normalizedAction)) {
            post.setStatus("removed");
        }

        postRepository.save(post);

        ModerationAction modAction = new ModerationAction(
                activeFlag.getId(),
                resolvedModerator,
                normalizedAction.toLowerCase(),
                reason.trim()
        );
        moderationActionRepository.save(modAction);
    }

    public List<Post> getFilteredAndSortedPosts(String categoryId, String keyword, String sortBy) {
        Integer parsedCategoryId = null;
        if (categoryId != null && !categoryId.trim().isEmpty() && !"all".equalsIgnoreCase(categoryId)) {
            try {
                parsedCategoryId = Integer.parseInt(categoryId);
            } catch (NumberFormatException e) {
                parsedCategoryId = switch (categoryId.toUpperCase()) {
                    case "INTERVIEW_PREP" -> 1;
                    case "RESUME_AND_APPLICATION" -> 2;
                    case "OFFER_AND_NEGOTIATION" -> 3;
                    case "INDUSTRY_INSIGHT" -> 4;
                    case "CAREER_FAIRS_AND_EVENTS" -> 5;
                    default -> 6;
                };
            }
        }

        List<Post> posts = postRepository.findFilteredAndSorted(parsedCategoryId, keyword, sortBy)
                .stream()
                .filter(p -> p.getStatus() == null || !"removed".equalsIgnoreCase(p.getStatus()))
                .toList();

        String normalizedSort = sortBy != null ? sortBy.trim().toLowerCase() : "recent";

        if ("unanswered".equals(normalizedSort)) {
            posts = posts.stream()
                    .filter(p -> p.getReplies() == null || p.getReplies().isEmpty())
                    .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                    .toList();
        } else if ("trending".equals(normalizedSort)) {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
            List<Post> recent = posts.stream()
                    .filter(p -> p.getCreatedAt() != null && !p.getCreatedAt().isBefore(cutoff))
                    .toList();
            List<Post> pool = recent.isEmpty() ? posts : recent;

            posts = pool.stream()
                    .sorted((a, b) -> {
                        int scoreA = ((a.getReplies() != null ? a.getReplies().size() : 0) * 2)
                                + (a.getUpvotes() != null ? a.getUpvotes().size() : 0);
                        int scoreB = ((b.getReplies() != null ? b.getReplies().size() : 0) * 2)
                                + (b.getUpvotes() != null ? b.getUpvotes().size() : 0);
                        if (scoreB != scoreA) {
                            return Integer.compare(scoreB, scoreA);
                        }
                        return b.getCreatedAt().compareTo(a.getCreatedAt());
                    })
                    .toList();
        }

        attachAuthorNames(posts);
        return posts;
    }

    @Transactional
    public ForumUserSettings updateHistoryPrivacy(String rawUserId, boolean hideHistory) {
        String resolvedUserId = resolveUserId(rawUserId);

        ForumUserSettings settings = forumUserSettingsRepository.findById(resolvedUserId)
                .orElseGet(() -> {
                    ForumUserSettings created = new ForumUserSettings();
                    created.setUserId(resolvedUserId);
                    return created;
                });

        settings.setHidePostHistory(hideHistory);
        return forumUserSettingsRepository.save(settings);
    }

    public boolean isHistoryHidden(String rawUserId) {
        String resolvedUserId = findExistingUserIdOrKeep(rawUserId);
        return forumUserSettingsRepository.findById(resolvedUserId)
                .map(ForumUserSettings::isHidePostHistory)
                .orElse(false);
    }

    private void attachAuthorNames(List<Post> posts) {
        if (posts == null || posts.isEmpty()) {
            return;
        }

        Map<String, String> userNames = new HashMap<>();
        for (AppUser user : userRepository.findAll()) {
            userNames.put(user.getId(), user.getName());
        }

        for (Post post : posts) {
            String postAuthorName = userNames.get(post.getAuthorId());
            if (postAuthorName != null && !postAuthorName.isBlank()) {
                post.setAuthorName(postAuthorName);
            }

            if (post.getReplies() != null) {
                for (Reply reply : post.getReplies()) {
                    String replyAuthorName = userNames.get(reply.getAuthorId());
                    if (replyAuthorName != null && !replyAuthorName.isBlank()) {
                        reply.setAuthorName(replyAuthorName);
                    }
                }
            }
        }
    }
}