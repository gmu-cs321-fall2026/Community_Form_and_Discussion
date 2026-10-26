import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents the available system categories for forum posts.
 */
enum Category {
    INTERVIEW_PREP,
    RESUME_AND_APPLICATION,
    OFFER_AND_NEGOTIATION,
    INDUSTRY_INSIGHT,
    CAREER_FAIRS_AND_EVENTS,
    GENERAL
}

/**
 * Manages user privacy preferences for public posting history.
 */
class UserProfile {
    private final String userId;
    private boolean showPostingHistory; // True by default (opt-out privacy model)

    public UserProfile(String userId) {
        this.userId = userId;
        this.showPostingHistory = true;
    }

    public String getUserId() { return userId; }
    public boolean isShowPostingHistory() { return showPostingHistory; }
    public void setShowPostingHistory(boolean showPostingHistory) { 
        this.showPostingHistory = showPostingHistory; 
    }
}

/**
 * Represents a single user reply attached to a forum post.
 * Supports upvoting and Stack Overflow-style helpful marking.
 */
class Reply {
    private final String id;
    private final String postId;
    private final String authorId;
    private String body;
    private final LocalDateTime createdAt;
    private final Set<String> upvotedUserIds;
    private boolean isHelpful;

    public Reply(String postId, String authorId, String body) {
        if (postId == null || postId.trim().isEmpty()) {
            throw new IllegalArgumentException("Reply must be linked to a valid postId.");
        }
        if (body == null || body.trim().isEmpty()) {
            throw new IllegalArgumentException("Reply body cannot be empty.");
        }
        this.id = UUID.randomUUID().toString();
        this.postId = postId;
        this.authorId = authorId;
        this.body = body;
        this.createdAt = LocalDateTime.now();
        this.upvotedUserIds = new HashSet<>();
        this.isHelpful = false;
    }

    public void toggleUpvote(String userId) {
        if (userId == null || userId.trim().isEmpty()) return;
        if (upvotedUserIds.contains(userId)) {
            upvotedUserIds.remove(userId);
        } else {
            upvotedUserIds.add(userId);
        }
    }

    public void toggleHelpful(String actorId, String postAuthorId) {
        if (!Objects.equals(actorId, postAuthorId)) {
            throw new SecurityException("Only the post creator can mark a reply as helpful.");
        }
        this.isHelpful = !this.isHelpful;
    }

    public String getId() { return id; }
    public String getPostId() { return postId; }
    public String getAuthorId() { return authorId; }
    public String getBody() { return body; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public Set<String> getUpvotedUserIds() { return Collections.unmodifiableSet(upvotedUserIds); }
    public int getUpvoteCount() { return upvotedUserIds.size(); }
    public boolean isHelpful() { return isHelpful; }
}

/**
 * Represents a main forum discussion post, containing metadata, tags, replies, upvotes, pins, and flags.
 */
class Post {
    private final String id;
    private String title;
    private String body;
    private String authorId;
    private Category category;
    private Map<String, String> tags;
    private boolean isPinned;
    private final LocalDateTime createdAt;
    private final List<Reply> replies;
    private final Set<String> upvotedUserIds;
    private final Set<String> flaggedBy;

    public Post(String title, String body, String authorId, Category category) {
        if (title == null || title.trim().isEmpty() || title.trim().length() > 120) {
            throw new IllegalArgumentException("Title must be between 1 and 120 characters.");
        }
        if (body == null || body.trim().isEmpty()) {
            throw new IllegalArgumentException("Post body cannot be empty.");
        }
        this.id = UUID.randomUUID().toString();
        this.title = title;
        this.body = body;
        this.authorId = authorId;
        this.category = category;
        this.tags = new HashMap<>();
        this.createdAt = LocalDateTime.now();
        this.replies = new ArrayList<>();
        this.upvotedUserIds = new HashSet<>();
        this.flaggedBy = new HashSet<>();
        this.isPinned = false;
    }

    public void toggleUpvote(String userId) {
        if (userId == null || userId.trim().isEmpty()) return;
        if (upvotedUserIds.contains(userId)) {
            upvotedUserIds.remove(userId);
        } else {
            upvotedUserIds.add(userId);
        }
    }

    public void setPinned(boolean pinned, String userRole) {
        if (!"CAREER_STAFF".equalsIgnoreCase(userRole)) {
            throw new SecurityException("Only Career Services Staff can pin or unpin posts.");
        }
        this.isPinned = pinned;
    }

    public void flagPost(String userId) {
        if (userId != null && !userId.trim().isEmpty()) {
            this.flaggedBy.add(userId);
        }
    }

    public void addTag(String key, String value) {
        if (key != null && value != null) {
            this.tags.put(key.toUpperCase().trim(), value.trim());
        }
    }

    String getTag(String key) {
        return key != null ? this.tags.get(key.toUpperCase().trim()) : null;
    }

    public Reply addReply(String authorId, String body) {
        Reply reply = new Reply(this.id, authorId, body);
        this.replies.add(reply);
        return reply;
    }

    public List<Reply> getReplies() {
        return Collections.unmodifiableList(replies);
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getAuthorId() { return authorId; }
    public Category getCategory() { return category; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public Map<String, String> getTags() { return Collections.unmodifiableMap(tags); }
    public boolean isPinned() { return isPinned; }
    public Set<String> getUpvotedUserIds() { return Collections.unmodifiableSet(upvotedUserIds); }
    public int getUpvoteCount() { return upvotedUserIds.size(); }
    public Set<String> getFlaggedBy() { return Collections.unmodifiableSet(flaggedBy); }

    @Override
    public String toString() {
        return "Post{id='" + id + "', title='" + title + "', category=" + category + ", upvotes=" + getUpvoteCount() + ", pinned=" + isPinned + ", flags=" + flaggedBy.size() + "}";
    }
}

/**
 * Service class that manages in-memory collection of posts, replies, and user profile settings.
 */
public class ForumPost {
    private final List<Post> postStore = new ArrayList<>();
    private final Map<String, UserProfile> userProfiles = new HashMap<>();

    private UserProfile getOrCreateProfile(String userId) {
        return userProfiles.computeIfAbsent(userId, UserProfile::new);
    }

    public void setUserHistoryVisibility(String userId, boolean visible) {
        UserProfile profile = getOrCreateProfile(userId);
        profile.setShowPostingHistory(visible);
    }

    public Post createPost(String title, String body, String authorId, Category category, Map<String, String> tags) {
        Post newPost = new Post(title, body, authorId, category);
        if (tags != null) {
            tags.forEach(newPost::addTag);
        }
        postStore.add(newPost);
        return newPost;
    }

    public Reply addReplyToPost(String postId, String authorId, String body) {
        Post post = postStore.stream()
                .filter(p -> p.getId().equals(postId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Post not found with ID: " + postId));
        return post.addReply(authorId, body);
    }

    public Map<String, Object> getUserProfileHistory(String targetUserId, String requesterId) {
        UserProfile profile = getOrCreateProfile(targetUserId);
        boolean isSelf = Objects.equals(targetUserId, requesterId);

        Map<String, Object> result = new HashMap<>();
        result.put("userId", targetUserId);
        result.put("isVisible", isSelf || profile.isShowPostingHistory());

        if (!isSelf && !profile.isShowPostingHistory()) {
            result.put("posts", Collections.emptyList());
            result.put("replies", Collections.emptyList());
            return result;
        }

        List<Post> userPosts = postStore.stream()
                .filter(p -> p.getAuthorId().equals(targetUserId))
                .collect(Collectors.toList());

        List<Map<String, String>> userReplies = new ArrayList<>();
        for (Post p : postStore) {
            for (Reply r : p.getReplies()) {
                if (r.getAuthorId().equals(targetUserId)) {
                    Map<String, String> replyInfo = new HashMap<>();
                    replyInfo.put("postId", p.getId());
                    replyInfo.put("postTitle", p.getTitle());
                    replyInfo.put("replyBody", r.getBody());
                    replyInfo.put("createdAt", r.getCreatedAt().toString());
                    userReplies.add(replyInfo);
                }
            }
        }

        result.put("posts", userPosts);
        result.put("replies", userReplies);
        return result;
    }

    public List<Post> getAllPosts() {
        return new ArrayList<>(postStore);
    }
}
