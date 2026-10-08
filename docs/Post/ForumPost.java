import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

enum Category {
    GENERAL,
    CAREER_ADVICE,
    JOB_BOARD,
    TECHNICAL,
    ANNOUNCEMENT
}

class Post {
    private final String id;
    private String title;
    private String body;
    private String authorId;
    private Category category;
    private Map<String, String> tags;
    private boolean isPinned;
    private final LocalDateTime createdAt;

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
    }

    public void addTag(String key, String value) {
        if (key != null && value != null) {
            this.tags.put(key.toUpperCase().trim(), value.trim());
        }
    }

    public String getTag(String key) {
        return key != null ? this.tags.get(key.toUpperCase().trim()) : null;
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
        return "Post{id='" + id + "', title='" + title + "', category=" + category + ", tags=" + tags + "}";
    }
}

public class ForumPost {
    private final List<Post> postStore = new ArrayList<>();

    public Post createPost(String title, String body, String authorId, Category category, Map<String, String> tags) {
        Post newPost = new Post(title, body, authorId, category);
        if (tags != null) {
            tags.forEach(newPost::addTag);
        }
        postStore.add(newPost);
        return newPost;
    }

    public List<Post> getAllPosts() {
        return new ArrayList<>(postStore);
    }

}