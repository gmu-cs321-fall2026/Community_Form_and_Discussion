import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Ranks forum posts for the "Trending" view.
 *
 * Score = engagementPoints / (ageInHours + TIME_OFFSET) ^ GRAVITY
 *
 *   engagementPoints = 1
 *                    + UPVOTE_WEIGHT  * post upvotes
 *                    + REPLY_WEIGHT   * number of replies
 *                    + HELPFUL_BONUS  (once, if the author marked any reply helpful)
 *
 * Age works as a decay: a fresh post with little activity can outrank an old
 * post with a lot of activity, and every post slowly sinks as it ages.
 * Replies count more than upvotes because they are a stronger signal of an
 * active discussion.
 *
 * Lives in the default package next to ForumPost.java (Post and Reply are
 * package-private there).
 */
public class TrendingRanker {

    // ---- Tunable weights -------------------------------------------------
    private final double upvoteWeight;
    private final double replyWeight;
    private final double helpfulBonus;
    private final double gravity;       // higher = posts decay faster
    private final double timeOffset;    // hours; stops brand-new posts from dividing by ~0
    private final long maxAgeDays;      // posts older than this are not trending (matches v_trending_posts: 7)
    private final boolean pinnedFirst;  // staff-pinned posts always float to the top

    public TrendingRanker() {
        this(1.0, 2.0, 5.0, 1.5, 2.0, 7, true);
    }

    public TrendingRanker(double upvoteWeight, double replyWeight, double helpfulBonus,
                          double gravity, double timeOffset, long maxAgeDays,
                          boolean pinnedFirst) {
        this.upvoteWeight = upvoteWeight;
        this.replyWeight = replyWeight;
        this.helpfulBonus = helpfulBonus;
        this.gravity = gravity;
        this.timeOffset = timeOffset;
        this.maxAgeDays = maxAgeDays;
        this.pinnedFirst = pinnedFirst;
    }

    /** Trending score for one post at the given moment. */
    public double score(Post post, LocalDateTime now) {
        double ageHours = Math.max(0, Duration.between(post.getCreatedAt(), now).toMinutes() / 60.0);

        boolean hasHelpful = false;
        for (Reply r : post.getReplies()) {
            if (r.isHelpful()) {
                hasHelpful = true;
                break;
            }
        }

        double points = 1.0
                + upvoteWeight * post.getUpvoteCount()
                + replyWeight * post.getReplies().size()
                + (hasHelpful ? helpfulBonus : 0.0);

        return points / Math.pow(ageHours + timeOffset, gravity);
    }

    /** Returns a new list of posts ordered from most to least trending. */
    public List<Post> rank(List<Post> posts) {
        return rank(posts, LocalDateTime.now());
    }

    /** Same as rank(posts) but with an explicit clock, which makes testing deterministic. */
    public List<Post> rank(List<Post> posts, LocalDateTime now) {
        LocalDateTime cutoff = now.minusDays(maxAgeDays);
        List<Post> result = new ArrayList<>();
        for (Post p : posts) {
            if (!p.getCreatedAt().isBefore(cutoff)) {
                result.add(p);
            }
        }

        Comparator<Post> byScore = Comparator
                .comparingDouble((Post p) -> score(p, now)).reversed()
                .thenComparing(Post::getCreatedAt, Comparator.reverseOrder()); // tie-break: newer first

        Comparator<Post> order = pinnedFirst
                ? Comparator.comparing((Post p) -> !p.isPinned()).thenComparing(byScore)
                : byScore;

        result.sort(order);
        return result;
    }

    /** Convenience: trending posts straight from the forum service. */
    public List<Post> rank(ForumPost forum) {
        return rank(forum.getAllPosts());
    }

    /** Top N trending posts. */
    public List<Post> top(ForumPost forum, int n) {
        List<Post> ranked = rank(forum);
        return ranked.subList(0, Math.min(n, ranked.size()));
    }
}
