import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents the available system categories for forum posts.
 */
enum Category {
    GENERAL,
    CAREER_ADVICE,
    JOB_BOARD,
    TECHNICAL,
    ANNOUNCEMENT
}

/**
 * Represents a single user reply attached to a forum post.
 * Implements a standard Q&A structure where replies link only to the main post.
 */
class Reply {
    private final String id;
    private final String postId;
    private final String authorId;
    private String body;
    private final LocalDateTime createdAt;

    /**
     * Constructs a new Reply.
     *
     * @param postId   The unique identifier of the parent post.
     * @param authorId The unique identifier of the user creating the reply.
     * @param body     The content text of the reply.
     * @throws IllegalArgumentException if postId or body is null or blank.
     */
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
    }

    public String getId() { return id; }
    public String getPostId() { return postId; }
    public String getAuthorId() { return authorId; }
    public String getBody() { return body; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}

/**
 * Represents a main forum discussion post, containing metadata, tags, and replies.
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

    /**
     * Constructs a new Post instance with validation rules.
     *
     * @param title    The title of the post (1 to 120 characters).
     * @param body     The main body content of the post.
     * @param authorId The user ID of the post author.
     * @param category The classification category of the post.
     * @throws IllegalArgumentException if title or body violates validation constraints.
     */
    public Post(String title, String body, String authorId, Category category) {
        // Validation: Title between 1 and 120 characters
        if (title == null || title.trim().isEmpty() || title.trim().length() > 120) {
            throw new IllegalArgumentException("Title must be between 1 and 120 characters.");
        }
        // Validation: Non-empty body
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
    }

    /**
     * Adds or updates a tag key-value pair on the post. Keys are normalized to uppercase.
     *
     * @param key   The tag key name (e.g., "LOCATION").
     * @param value The tag value (e.g., "REMOTE").
     */
    public void addTag(String key, String value) {
        if (key != null && value != null) {
            this.tags.put(key.toUpperCase().trim(), value.trim());
        }
    }

    /**
     * Retrieves a tag value by key.
     *
     * @param key The tag name to look up.
     * @return The associated tag value, or null if not found.
     */
    public String getTag(String key) {
        return key != null ? this.tags.get(key.toUpperCase().trim()) : null;
    }

    /**
     * Creates and attaches a new top-level reply directly to this post.
     *
     * @param authorId The ID of the replying user.
     * @param body     The text content of the reply.
     * @return The newly created Reply object.
     */
    public Reply addReply(String authorId, String body) {
        Reply reply = new Reply(this.id, authorId, body);
        this.replies.add(reply);
        return reply;
    }

    /**
     * Returns an unmodifiable list of replies attached to this post.
     *
     * @return Immutable list of replies.
     */
    public List<Reply> getReplies() {
        return Collections.unmodifiableList(replies);
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public Category getCategory() { return category; }
    public Map<String, String> getTags() { return Collections.unmodifiableMap(tags); }
    
    public void setPinned(boolean pinned) {
        this.isPinned = pinned;
    }

    public boolean isPinned() { return isPinned; }

    @Override
    public String toString() {
        return "Post{id='" + id + "', title='" + title + "', category=" + category + ", tags=" + tags + ", replyCount=" + replies.size() + "}";
    }
}

/**
 * Service class that manages in-memory collection and creation of posts and replies.
 */
public class ForumPost {
    private final List<Post> postStore = new ArrayList<>();

    /**
     * Creates a new post and registers it in the internal store.
     *
     * @param title    Post title.
     * @param body     Post body content.
     * @param authorId Author ID.
     * @param category Post category.
     * @param tags     Optional key-value map of tags to apply.
     * @return The created Post instance.
     */
    public Post createPost(String title, String body, String authorId, Category category, Map<String, String> tags) {
        Post newPost = new Post(title, body, authorId, category);
        if (tags != null) {
            tags.forEach(newPost::addTag);
        }
        postStore.add(newPost);
        return newPost;
    }

    /**
     * Finds a target post by its ID and attaches a reply to it.
     *
     * @param postId   Target post unique identifier.
     * @param authorId Author ID of the replier.
     * @param body     Content of the reply.
     * @return The created Reply instance.
     * @throws IllegalArgumentException if target post ID is not found.
     */
    public Reply addReplyToPost(String postId, String authorId, String body) {
        Post post = postStore.stream()
                .filter(p -> p.getId().equals(postId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Post not found with ID: " + postId));
        return post.addReply(authorId, body);
    }

    /**
     * Retrieves a copy of all posts in the store.
     *
     * @return List containing all created posts.
     */
    public List<Post> getAllPosts() {
        return new ArrayList<>(postStore);
    }
}