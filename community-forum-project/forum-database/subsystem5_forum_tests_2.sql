/* =====================================================================
   Subsystem 5 Forum: Combined Sample Data & Verification Queries (MySQL 8)
   Run subsystem5_forum_mysql.sql first.

   Designed to run in one batch against a clean `mason_forum` database.
   ===================================================================== */
USE mason_forum;


/* =====================================================================
   PART A1: CORE SAMPLE DATA
   UUIDs go into variables so later rows can reference them.
   ===================================================================== */
SET @alice = UUID(), @bob = UUID(), @carol = UUID(), @staff = UUID(), @admin = UUID(), @alex = UUID();

INSERT INTO app_user (id, name, gmu_email, role) VALUES
  (@alice, 'Alice Student', 'alice@gmu.edu', 'STUDENT'),
  (@bob,   'Bob Student',   'bob@gmu.edu',   'STUDENT'),
  (@carol, 'Carol Mentor',  'carol@gmu.edu', 'MENTOR'),
  (@staff, 'Sam Staff',     'sam@gmu.edu',   'STAFF'),
  (@admin, 'Ada Admin',     'ada@gmu.edu',   'ADMIN'),
  (@alex,  'AlexUser',      'alex@gmu.edu',  'ADMIN');

INSERT INTO forum_user_settings (user_id, hide_post_history) VALUES
  (@alice, 0), (@bob, 1), (@alex, 0);

SET @cat_interview = (SELECT id FROM category WHERE name = 'Interview Prep' LIMIT 1);
SET @cat_offer     = (SELECT id FROM category WHERE name = 'Offer & Negotiation' LIMIT 1);
SET @cat_resume    = (SELECT id FROM category WHERE name = 'Resume & Portfolio' LIMIT 1);

-- Tags
INSERT IGNORE INTO tag (name, tag_type) VALUES
  ('Capital One', 'employer'),
  ('Software Engineer Intern', 'role');

SET @tag_c1  = (SELECT id FROM tag WHERE name = 'Capital One');
SET @tag_swe = (SELECT id FROM tag WHERE name = 'Software Engineer Intern');

-- Posts: one that gets replies, one that stays unanswered
INSERT INTO post (id, author_id, category_id, title, body, status) VALUES
  (UUID(), @alex, COALESCE(@cat_interview, 1), 'Tips for technical coding rounds?',
   'How do you pace yourself during 45-minute coding interviews?', 'visible');

SET @p1 = UUID(), @p2 = UUID();
INSERT INTO post (id, author_id, category_id, title, body, status) VALUES
  (@p1, @alice, COALESCE(@cat_interview, 1), 'Capital One SWE intern power day?',
        'Anyone done the power day recently? What were the technical rounds like?', 'visible'),
  (@p2, @bob,   COALESCE(@cat_offer, 1),     'Is it OK to negotiate an internship offer?',
        'Got an offer with a two week deadline. Worth asking for more?', 'visible');

INSERT IGNORE INTO post_tag (post_id, tag_id) VALUES
  (@p1, @tag_c1),
  (@p1, @tag_swe);

-- Replies: top-level, then a nested reply under it (same post)
SET @r1 = UUID(), @r2 = UUID();
INSERT INTO reply (id, post_id, author_id, body, is_helpful, status) VALUES
  (@r1, @p1, @bob, 'Two coding rounds plus a case interview. Practice mediums.', 0, 'visible');
INSERT INTO reply (id, post_id, parent_reply_id, author_id, body, is_helpful, status) VALUES
  (@r2, @p1, @r1, @carol, 'Agree. The case round is about talking through tradeoffs.', 0, 'visible');

-- Bump counters and last_activity_at on @p1
UPDATE post
   SET reply_count = reply_count + 2,
       last_activity_at = NOW()
 WHERE id = @p1;

-- Votes and score updates
INSERT IGNORE INTO post_vote  (post_id, user_id)  VALUES (@p1, @bob), (@p1, @carol);
INSERT IGNORE INTO reply_vote (reply_id, user_id) VALUES (@r1, @alice), (@r1, @carol);
UPDATE post  SET vote_score = vote_score + 2 WHERE id = @p1;
UPDATE reply SET vote_score = vote_score + 2 WHERE id = @r1;

-- Alice marks Bob's reply helpful
UPDATE post SET helpful_reply_id = @r1 WHERE id = @p1;

-- Staff pins Bob's negotiation post
UPDATE post SET is_pinned = 1, pinned_by = @staff WHERE id = @p2;

-- Flags: one on a reply, one on a post
SET @f1 = UUID(), @f2 = UUID();
INSERT INTO flag (id, post_id, reply_id, flagged_by, reason, status) VALUES
  (@f1, @p1, @r2,  @alice, 'Off topic', 'open'),
  (@f2, @p2, NULL, @carol, 'Names a recruiter by name', 'open');

-- Moderation: dismiss f1, remove the post behind f2
INSERT INTO moderation_action (id, flag_id, moderator_id, action, reason) VALUES
  (UUID(), @f1, @staff, 'dismiss', 'Relevant to the thread');
UPDATE flag SET status = 'dismissed' WHERE id = @f1;

INSERT INTO moderation_action (id, flag_id, moderator_id, action, reason) VALUES
  (UUID(), @f2, @admin, 'remove', 'Policy: no naming individual recruiters');
UPDATE flag SET status = 'actioned' WHERE id = @f2;
UPDATE post SET status = 'removed'  WHERE id = @p2;


/* =====================================================================
   PART A2: EXTRA SAMPLE DATA
   Covers: pinned visible post, unanswered visible post, 3-level nested
           reply chain, and an open flag in the moderation queue.
   ===================================================================== */
SET @dan = UUID(), @elena = UUID(), @mod2 = UUID();

INSERT INTO app_user (id, name, gmu_email, role) VALUES
  (@dan,   'Dan Student',   'dan@gmu.edu',   'STUDENT'),
  (@elena, 'Elena Mentor',  'elena@gmu.edu', 'MENTOR'),
  (@mod2,  'Maya Moderator','maya@gmu.edu',  'STAFF');

INSERT INTO forum_user_settings (user_id, hide_post_history) VALUES
  (@dan, 0), (@elena, 1), (@mod2, 0);

INSERT IGNORE INTO tag (name, tag_type) VALUES
  ('Amazon', 'employer'),
  ('Backend Engineer Intern', 'role'),
  ('System Design', 'topic');

SET @tag_amzn = (SELECT id FROM tag WHERE name = 'Amazon');
SET @tag_be   = (SELECT id FROM tag WHERE name = 'Backend Engineer Intern');
SET @tag_sd   = (SELECT id FROM tag WHERE name = 'System Design');

SET @p3 = UUID(), @p4 = UUID(), @p5 = UUID();

INSERT INTO post (id, author_id, category_id, title, body, status, is_pinned, pinned_by) VALUES
  (@p3, @elena, COALESCE(@cat_interview, 1),
        'System design prep tips for SWE interns?',
        'How deep do interviewers expect you to go on caching, sharding, and queues?',
        'visible', 1, @staff),
  (@p4, @dan, COALESCE(@cat_offer, 1),
        'Return offer timeline vs summer recruiting?',
        'My team mentioned a return offer decision in October—how do people handle overlapping deadlines?',
        'visible', 0, NULL),
  (@p5, @dan, COALESCE(@cat_resume, @cat_interview, 1),
        'Sharing my anonymized SWE resume + referral thread',
        'Happy to swap resume feedback and share tips on getting referrals.',
        'visible', 0, NULL);

INSERT IGNORE INTO post_tag (post_id, tag_id) VALUES
  (@p3, @tag_amzn),
  (@p3, @tag_be),
  (@p3, @tag_sd),
  (@p4, @tag_be),
  (@p5, @tag_amzn);

-- Threaded replies on @p3: top-level -> child -> grandchild
SET @r3 = UUID(), @r4 = UUID(), @r5 = UUID();

INSERT INTO reply (id, post_id, author_id, body, is_helpful, status) VALUES
  (@r3, @p3, @alice,
        'Start with requirements and back-of-the-envelope capacity estimates before drawing boxes.',
        0, 'visible');

INSERT INTO reply (id, post_id, parent_reply_id, author_id, body, is_helpful, status) VALUES
  (@r4, @p3, @r3, @bob,
        'Seconding this—calling out trade-offs like eventual consistency vs strong consistency helps a lot.',
        0, 'visible');

INSERT INTO reply (id, post_id, parent_reply_id, author_id, body, is_helpful, status) VALUES
  (@r5, @p3, @r4, @elena,
        'Great addition. Also practice explaining why you pick Redis or Kafka for a specific bottleneck.',
        0, 'visible');

-- Bump reply_count and last_activity_at on @p3
UPDATE post
   SET reply_count = reply_count + 3,
       last_activity_at = NOW()
 WHERE id = @p3;

-- Votes and score updates on @p3 and @r3
INSERT IGNORE INTO post_vote (post_id, user_id) VALUES
  (@p3, @alice),
  (@p3, @bob),
  (@p3, @carol);

INSERT IGNORE INTO reply_vote (reply_id, user_id) VALUES
  (@r3, @elena),
  (@r3, @carol);

UPDATE post  SET vote_score = vote_score + 3 WHERE id = @p3;
UPDATE reply SET vote_score = vote_score + 2 WHERE id = @r3;

-- Mark @r3 as helpful on @p3
UPDATE post SET helpful_reply_id = @r3 WHERE id = @p3;

-- Open flag on @p5 (left unresolved so v_moderation_queue & moderation UI show an active item)
SET @f3 = UUID();
INSERT INTO flag (id, post_id, reply_id, flagged_by, reason, status) VALUES
  (@f3, @p5, NULL, @carol, 'Check for personal contact details / spam links', 'open');


/* =====================================================================
   PART B: READ & VERIFICATION QUERIES
   ===================================================================== */

-- 1. Row counts per table
SELECT 'app_user' t, COUNT(*) n FROM app_user UNION ALL
SELECT 'category', COUNT(*) FROM category    UNION ALL
SELECT 'post',     COUNT(*) FROM post        UNION ALL
SELECT 'reply',    COUNT(*) FROM reply       UNION ALL
SELECT 'post_vote',COUNT(*) FROM post_vote   UNION ALL
SELECT 'flag',     COUNT(*) FROM flag        UNION ALL
SELECT 'moderation_action', COUNT(*) FROM moderation_action;

-- 2. GET /posts: visible posts, pinned first then newest activity
SELECT p.id, p.title, c.name AS category, u.name AS author,
       p.vote_score, p.reply_count, p.is_pinned, p.last_activity_at
  FROM post p
  JOIN category c ON c.id = p.category_id
  JOIN app_user u ON u.id = p.author_id
 WHERE p.status = 'visible'
 ORDER BY p.is_pinned DESC, p.last_activity_at DESC
 LIMIT 20 OFFSET 0;

-- 3. GET /posts/{id}: post detail with its threaded replies on @p3
SELECT r.id, r.parent_reply_id, u.name AS author, r.body, r.vote_score,
       (r.id = p.helpful_reply_id) AS is_helpful, r.created_at
  FROM reply r
  JOIN post p     ON p.id = r.post_id
  JOIN app_user u ON u.id = r.author_id
 WHERE r.post_id = @p3 AND r.status = 'visible'
 ORDER BY r.created_at;

-- 4. Filter by tag (posts tagged "Amazon")
SELECT p.title, t.name AS tag
  FROM post p
  JOIN post_tag pt ON pt.post_id = p.id
  JOIN tag t       ON t.id = pt.tag_id
 WHERE t.name = 'Amazon' AND p.status = 'visible';

-- 5. Full-text search
SELECT id, title, MATCH(title, body) AGAINST ('power day technical') AS relevance
  FROM post
 WHERE MATCH(title, body) AGAINST ('power day technical')
   AND status = 'visible'
 ORDER BY relevance DESC;

-- 6. Views
SELECT * FROM v_unanswered_posts;   -- includes @p4
SELECT * FROM v_trending_posts;
SELECT * FROM v_moderation_queue;   -- includes open flag @f3 on @p5

-- 7. Moderation log (auditable history)
SELECT m.created_at, mod_u.name AS moderator, m.action, m.reason,
       f.reason AS flag_reason, f.status AS flag_status
  FROM moderation_action m
  JOIN flag f          ON f.id = m.flag_id
  JOIN app_user mod_u  ON mod_u.id = m.moderator_id
 ORDER BY m.created_at;

-- 8. User posting history, respecting hide_post_history
SELECT u.name, p.title, p.created_at
  FROM post p
  JOIN app_user u ON u.id = p.author_id
  LEFT JOIN forum_user_settings s ON s.user_id = u.id
 WHERE COALESCE(s.hide_post_history, 0) = 0;

-- 9. Counter drift check: stored counters vs real counts (expect 0 rows)
SELECT p.id, p.reply_count, COUNT(r.id) AS actual_replies
  FROM post p
  LEFT JOIN reply r ON r.post_id = p.id
 GROUP BY p.id, p.reply_count
HAVING p.reply_count <> COUNT(r.id);

SELECT p.id, p.vote_score, COUNT(v.user_id) AS actual_votes
  FROM post p
  LEFT JOIN post_vote v ON v.post_id = p.id
 GROUP BY p.id, p.vote_score
HAVING p.vote_score <> COUNT(v.user_id);
