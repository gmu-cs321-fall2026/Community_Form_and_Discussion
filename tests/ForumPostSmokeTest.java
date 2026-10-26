
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;

public class ForumPostSmokeTest {

    public static void main(String[] args) {
        ForumPost forum = new ForumPost();

        // Test creating a forum post
        Post post = forum.createPost(
            "Internship Interview Question",
            "How should I prepare for interviews?",
            "student1",
            Category.INTERVIEW_PREP,
            Map.of("topic", "interview")
        );

        check(forum.getAllPosts().size() == 1,
              "Post creation failed");

        // Test upvoting a post
        post.toggleUpvote("student2");
        check(post.getUpvoteCount() == 1,
              "Upvote failed");

        // Test removing an upvote
        post.toggleUpvote("student2");
        check(post.getUpvoteCount() == 0,
              "Upvote removal failed");

        // Test adding a reply
        forum.addReplyToPost(
            post.getId(),
            "student2",
            "Practice common interview questions."
        );

        check(post.getReplies().size() == 1,
              "Reply creation failed");

        // Test posting history privacy
        forum.setUserHistoryVisibility("student1", false);

        Map<String, Object> history =
            forum.getUserProfileHistory("student1", "student2");

        check(((List<?>) history.get("posts")).isEmpty(),
              "Private posting history was exposed");

        // Test Trending Ranking
        TrendingRanker ranker = new TrendingRanker();
        LocalDateTime now = LocalDateTime.now();

        Post quiet = forum.createPost(
            "Quiet post", "Nobody has engaged here",
            "student3", Category.GENERAL, null
        );

        Reply helpfulReply = post.getReplies().get(0);
        helpfulReply.toggleHelpful("student1", "student1");

        List<Post> ranked = ranker.rank(forum.getAllPosts(), now);

        check(ranked.size() = 2,
              "Ranking dropped posts");
        check(ranked.get(0).getId().equals(post.getId()),
              "Engaged post should outrank quiet post");

        System.out.println("All forum tests passed!");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
