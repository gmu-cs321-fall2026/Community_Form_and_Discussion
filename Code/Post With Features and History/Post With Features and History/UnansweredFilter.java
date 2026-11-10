import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Filters forum posts for the "Unanswered" view.
 *
 * A post is unanswered when it has no replies. This mirrors the
 * v_unanswered_posts view in subsystem5_forum_mysql.sql (reply_count = 0).
 *
 * Optionally, replies written by the post's own author can be ignored, so a
 * student following up on their own thread does not remove it from the view.
 *
 * Lives in the default package next to ForumPost.java (Post and Reply are
 * package-private there).
 */
public class UnansweredFilter {

    private final boolean ignoreAuthorReplies;

    /** Default: any reply, including the author's own, counts as an answer. */
    public UnansweredFilter() {
        this(false);
    }

    /**
     * @param ignoreAuthorReplies if true, replies by the post's author do not
     *                            count; the post stays unanswered until someone
     *                            else replies.
     */
    public UnansweredFilter(boolean ignoreAuthorReplies) {
        this.ignoreAuthorReplies = ignoreAuthorReplies;
    }

    /** True if the post has not been answered yet. */
    public boolean isUnanswered(Post post) {
        if (post == null) {
            return false;
        }
        for (Reply reply : post.getReplies()) {
            if (!ignoreAuthorReplies
                    || !Objects.equals(reply.getAuthorId(), post.getAuthorId())) {
                return false;
            }
        }
        return true;
    }

    /** Unanswered posts from the given list, newest first. */
    public List<Post> filter(List<Post> posts) {
        List<Post> result = new ArrayList<>();
        if (posts != null) {
            for (Post p : posts) {
                if (isUnanswered(p)) {
                    result.add(p);
                }
            }
        }
        result.sort(Comparator.comparing(Post::getCreatedAt).reversed());
        return result;
    }

    /** Unanswered posts in one category, newest first */
    public List<Post> filter(List<Post> posts, Category category) {
        List<Post> result = new ArrayList<>();
        for (Post p : filter(posts)) {
            if (p.getCategory() == category) {
                result.add(p);
            }
        }
        return result;
    }

    /** Convenience: unanswered posts straight from the forum service. */
    public List<Post> filter(ForumPost forum) {
        return filter(forum.getAllPosts());
    }

    /** Convenience: unanswered posts in a category straight from the forum service. */
    public List<Post> filter(ForumPost forum, Category category) {
        return filter(forum.getAllPosts(), category);
    }
}
