/* =====================================================================
   Subsystem 5 Forum: test queries (MySQL 8)
   Run subsystem5_forum_mysql.sql first.

   PART A and PART B: run with Execute All.
   PART C: negative tests. Each one SHOULD fail. Run them one at a time
           with Ctrl+Enter (Execute Current Statement), or Execute All
           would stop at the first error.
   ===================================================================== */
USE mason_forum;


/* =====================================================================
   PART A: SAMPLE DATA
   UUIDs go into variables so later rows can reference them.
   ===================================================================== */
SET @alice = UUID(), @bob = UUID(), @carol = UUID(), @staff = UUID(), @admin = UUID();

INSERT INTO app_user (id, name, gmu_email, role) VALUES
  (@alice, 'Alice Student', 'alice@gmu.edu', 'STUDENT'),
  (@bob,   'Bob Student',   'bob@gmu.edu',   'STUDENT'),
  (@carol, 'Carol Mentor',  'carol@gmu.edu', 'MENTOR'),
  (@staff, 'Sam Staff',     'sam@gmu.edu',   'STAFF'),
  (@admin, 'Ada Admin',     'ada@gmu.edu',   'ADMIN');

INSERT INTO forum_user_settings (user_id, hide_post_history) VALUES
  (@alice, 0), (@bob, 1);

SET @cat_interview = (SELECT id FROM category WHERE name = 'Interview Prep');
SET @cat_offer     = (SELECT id FROM category WHERE name = 'Offer & Negotiation');

-- Tags
INSERT INTO tag (name, tag_type) VALUES
  ('Capital One', 'employer'), ('Software Engineer Intern', 'role');
SET @tag_c1   = (SELECT id FROM tag WHERE name = 'Capital One');
SET @tag_swe  = (SELECT id FROM tag WHERE name = 'Software Engineer Intern');

-- Posts: one that gets replies, one that stays unanswered
SET @p1 = UUID(), @p2 = UUID();
INSERT INTO post (id, author_id, category_id, title, body) VALUES
  (@p1, @alice, @cat_interview, 'Capital One SWE intern power day?',
        'Anyone done the power day recently? What were the technical rounds like?'),
  (@p2, @bob,   @cat_offer,     'Is it OK to negotiate an internship offer?',
        'Got an offer with a two week deadline. Worth asking for more?');

INSERT INTO post_tag (post_id, tag_id) VALUES (@p1, @tag_c1), (@p1, @tag_swe);

-- Replies: top-level, then a nested reply under it (same post)
SET @r1 = UUID(), @r2 = UUID();
INSERT INTO reply (id, post_id, author_id, body) VALUES
  (@r1, @p1, @bob, 'Two coding rounds plus a case interview. Practice mediums.');
INSERT INTO reply (id, post_id, parent_reply_id, author_id, body) VALUES
  (@r2, @p1, @r1, @carol, 'Agree. The case round is about talking through tradeoffs.');

-- What the Java layer does after inserting replies: bump counters
UPDATE post SET reply_count = reply_count + 2, last_activity_at = NOW() WHERE id = @p1;

-- Votes (Java checks the voter is not the author first), then update scores
INSERT INTO post_vote  (post_id, user_id)  VALUES (@p1, @bob), (@p1, @carol);
INSERT INTO reply_vote (reply_id, user_id) VALUES (@r1, @alice), (@r1, @carol);
UPDATE post  SET vote_score = vote_score + 2 WHERE id = @p1;
UPDATE reply SET vote_score = vote_score + 2 WHERE id = @r1;

-- Alice marks Bob's reply helpful
UPDATE post SET helpful_reply_id = @r1 WHERE id = @p1;

-- Staff pins Bob's negotiation post
UPDATE post SET is_pinned = 1, pinned_by = @staff WHERE id = @p2;

-- Flags: one on a reply, one on a post
SET @f1 = UUID(), @f2 = UUID();
INSERT INTO flag (id, post_id, reply_id, flagged_by, reason) VALUES
  (@f1, @p1, @r2,  @alice, 'Off topic'),
  (@f2, @p2, NULL, @carol, 'Names a recruiter by name');

-- Moderation: dismiss f1, remove the post behind f2 (Java does the follow-up updates)
INSERT INTO moderation_action (flag_id, moderator_id, action, reason) VALUES
  (@f1, @staff, 'dismiss', 'Relevant to the thread');
UPDATE flag SET status = 'dismissed' WHERE id = @f1;

INSERT INTO moderation_action (flag_id, moderator_id, action, reason) VALUES
  (@f2, @admin, 'remove', 'Policy: no naming individual recruiters');
UPDATE flag SET status = 'actioned' WHERE id = @f2;
UPDATE post SET status = 'removed'  WHERE id = @p2;


/* =====================================================================
   PART B: READ QUERIES (what the API endpoints would run)
   ===================================================================== */

-- 1. Row counts per table (quick sanity check)
SELECT 'app_user' t, COUNT(*) n FROM app_user UNION ALL
SELECT 'category', COUNT(*) FROM category    UNION ALL
SELECT 'post',     COUNT(*) FROM post        UNION ALL
SELECT 'reply',    COUNT(*) FROM reply       UNION ALL
SELECT 'post_vote',COUNT(*) FROM post_vote   UNION ALL
SELECT 'flag',     COUNT(*) FROM flag        UNION ALL
SELECT 'moderation_action', COUNT(*) FROM moderation_action;

-- 2. GET /posts: visible posts, newest activity first, paginated (page 1, size 20)
SELECT p.id, p.title, c.name AS category, u.name AS author,
       p.vote_score, p.reply_count, p.is_pinned, p.last_activity_at
  FROM post p
  JOIN category c ON c.id = p.category_id
  JOIN app_user u ON u.id = p.author_id
 WHERE p.status = 'visible'
 ORDER BY p.is_pinned DESC, p.last_activity_at DESC
 LIMIT 20 OFFSET 0;

-- 3. GET /posts/{id}: post detail with its threaded replies
SELECT r.id, r.parent_reply_id, u.name AS author, r.body, r.vote_score,
       (r.id = p.helpful_reply_id) AS is_helpful, r.created_at
  FROM reply r
  JOIN post p     ON p.id = r.post_id
  JOIN app_user u ON u.id = r.author_id
 WHERE r.post_id = @p1 AND r.status = 'visible'
 ORDER BY r.created_at;

-- 4. Filter by tag (posts tagged "Capital One")
SELECT p.title, t.name AS tag
  FROM post p
  JOIN post_tag pt ON pt.post_id = p.id
  JOIN tag t       ON t.id = pt.tag_id
 WHERE t.name = 'Capital One' AND p.status = 'visible';

-- 5. Full-text search
SELECT id, title, MATCH(title, body) AGAINST ('power day technical') AS relevance
  FROM post
 WHERE MATCH(title, body) AGAINST ('power day technical')
   AND status = 'visible'
 ORDER BY relevance DESC;

-- 6. Views
SELECT * FROM v_unanswered_posts;   -- expect empty: p1 has replies, p2 was removed
SELECT * FROM v_trending_posts;     -- expect p1, trend_score = 2*2 + 2 = 6
SELECT * FROM v_moderation_queue;   -- expect empty: both flags were resolved

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
 WHERE COALESCE(s.hide_post_history, 0) = 0;  -- Bob's posts should not appear

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


/* =====================================================================
   PART C: NEGATIVE TESTS. Each should FAIL with the error shown.
   Run one at a time with Ctrl+Enter. PART A must have run in the SAME
   Workbench session so the @variables still exist.
   ===================================================================== */

-- C1. Duplicate vote by the same user        -> 1062 Duplicate entry (pk_post_vote)
INSERT INTO post_vote (post_id, user_id) VALUES (@p1, @bob);

-- C2. Downvote / vote value other than 1     -> 3819 Check 'ck_post_vote_value' violated
INSERT INTO post_vote (post_id, user_id, vote_value) VALUES (@p1, @admin, 2);

-- C3. Invalid role                           -> 3819 Check 'ck_app_user_role' violated
INSERT INTO app_user (name, gmu_email, role) VALUES ('Eve', 'eve@gmu.edu', 'HACKER');

-- C4. Pinned with no pinned_by               -> 3819 Check 'ck_post_pin_pair' violated
UPDATE post SET pinned_by = NULL WHERE id = @p2;

-- C5. Nested reply under a parent from a DIFFERENT post -> 1452 FK fk_reply_parent fails
INSERT INTO reply (post_id, parent_reply_id, author_id, body)
VALUES (@p2, @r1, @alice, 'This parent lives on p1, not p2');

-- C6. Helpful reply that belongs to another post        -> 1452 FK fk_post_helpful_reply fails
UPDATE post SET helpful_reply_id = @r1 WHERE id = @p2;

-- C7. Flag a reply but point at the wrong post          -> 1452 FK fk_flag_reply fails
INSERT INTO flag (post_id, reply_id, flagged_by, reason)
VALUES (@p2, @r1, @alice, 'Mismatched post and reply');

-- C8. Second moderation action on the same flag        -> 1062 Duplicate entry (uq_mod_flag)
INSERT INTO moderation_action (flag_id, moderator_id, action, reason)
VALUES (@f1, @admin, 'remove', 'Changed my mind');

-- C9. Invalid moderation action                        -> 3819 Check 'ck_mod_action' violated
SET @f3 = UUID();
INSERT INTO flag (id, post_id, flagged_by, reason) VALUES (@f3, @p1, @bob, 'test');
INSERT INTO moderation_action (flag_id, moderator_id, action, reason)
VALUES (@f3, @staff, 'ban', 'Not a valid action');   -- only this second insert should fail

-- C10. Delete a category that posts use                -> 1451 Cannot delete parent row
DELETE FROM category WHERE id = @cat_interview;

-- C11. Post in a category that does not exist          -> 1452 FK fk_post_category fails
INSERT INTO post (author_id, category_id, title, body) VALUES (@alice, 9999, 'x', 'y');


/* =====================================================================
   PART D: DELETE BEHAVIOR (run last, one statement at a time)
   ===================================================================== */

-- D1. Hard-deleting moderated content is BLOCKED -> 1451 Cannot delete parent row
-- Deleting r1 would cascade to r2, then to flag f1, but f1 has a
-- moderation_action row (fk_mod_flag, no cascade). This is intended:
-- the moderation log stays auditable, and the app soft-deletes with
-- status = 'removed' instead of DELETE.
DELETE FROM reply WHERE id = @r1;

-- D2. Cascade on unmoderated content works. Build a small thread, then delete it.
SET @r3 = UUID(), @r4 = UUID();
INSERT INTO reply (id, post_id, author_id, body) VALUES (@r3, @p1, @carol, 'Temp parent');
INSERT INTO reply (id, post_id, parent_reply_id, author_id, body)
VALUES (@r4, @p1, @r3, @bob, 'Temp child');
INSERT INTO reply_vote (reply_id, user_id) VALUES (@r3, @alice);
INSERT INTO flag (post_id, reply_id, flagged_by, reason) VALUES (@p1, @r4, @alice, 'temp');

DELETE FROM reply WHERE id = @r3;

SELECT COUNT(*) AS should_be_0 FROM reply      WHERE id IN (@r3, @r4);  -- child cascaded
SELECT COUNT(*) AS should_be_0 FROM reply_vote WHERE reply_id = @r3;    -- vote cascaded
SELECT COUNT(*) AS should_be_0 FROM flag       WHERE reply_id = @r4;    -- flag cascaded
