/* =====================================================================
   Subsystem 5: Community Forum & Discussion
   MySQL 8.0.16+ version (needs 8.0.13+ for DEFAULT (UUID()) and
   8.0.16+ for CHECK constraints to actually be enforced)

   Run in MySQL Workbench with "Execute All" (the plain lightning bolt),
   not "Execute Current Statement".

   Business rules (counter updates, no self-voting, moderation flow,
   reply not its own parent) live in the Java service layer, same as
   the Oracle version.
   ===================================================================== */

CREATE DATABASE IF NOT EXISTS mason_forum
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE mason_forum;


/* =====================================================================
   0. DROP EXISTING TABLES (safe to re-run)
   MySQL has no anonymous PL/SQL blocks. Turning FK checks off lets the
   tables drop in any order.
   ===================================================================== */
SET FOREIGN_KEY_CHECKS = 0;
DROP VIEW  IF EXISTS v_moderation_queue, v_trending_posts, v_unanswered_posts;
DROP TABLE IF EXISTS moderation_action, flag, reply_vote, post_vote, post_tag,
                     tag, forum_user_settings, reply, post, category, app_user;
SET FOREIGN_KEY_CHECKS = 1;


/* =====================================================================
   1. APP_USER (owned by Shared Core)
   Local stand-in so the foreign keys have something to point at.
   ===================================================================== */
CREATE TABLE app_user (
  id         CHAR(36)     NOT NULL DEFAULT (UUID()),
  name       VARCHAR(120) NOT NULL,
  gmu_email  VARCHAR(254) NOT NULL,
  role       VARCHAR(10)  NOT NULL,
  CONSTRAINT pk_app_user        PRIMARY KEY (id),
  CONSTRAINT uq_app_user_email  UNIQUE (gmu_email),
  CONSTRAINT ck_app_user_role   CHECK (role IN ('STUDENT','MENTOR','STAFF','ADMIN'))
) ENGINE = InnoDB;


/* =====================================================================
   2. FORUM TABLES
   NOTE: MySQL silently ignores inline "col REFERENCES t(id)". Every FK
   below is written as a table-level FOREIGN KEY so it is really enforced.
   ===================================================================== */

-- CATEGORY: fixed list managed by admins.
CREATE TABLE category (
  id           INT          NOT NULL AUTO_INCREMENT,
  name         VARCHAR(60)  NOT NULL,
  description  VARCHAR(400),
  sort_order   SMALLINT     NOT NULL DEFAULT 0,
  is_active    TINYINT(1)   NOT NULL DEFAULT 1,
  CONSTRAINT pk_category         PRIMARY KEY (id),
  CONSTRAINT uq_category_name    UNIQUE (name),
  CONSTRAINT ck_category_active  CHECK (is_active IN (0,1))
) ENGINE = InnoDB;

-- POST: top-level thread. vote_score, reply_count and last_activity_at
-- are denormalized counters maintained by the Java service layer.
CREATE TABLE post (
  id                CHAR(36)     NOT NULL DEFAULT (UUID()),
  author_id         CHAR(36)     NOT NULL,
  category_id       INT          NOT NULL,
  title             VARCHAR(200) NOT NULL,
  body              TEXT         NOT NULL,
  vote_score        INT          NOT NULL DEFAULT 0,
  reply_count       INT          NOT NULL DEFAULT 0,
  helpful_reply_id  CHAR(36),                         -- FK added in section 3
  is_pinned         TINYINT(1)   NOT NULL DEFAULT 0,
  pinned_by         CHAR(36),
  status            VARCHAR(10)  NOT NULL DEFAULT 'visible',
  created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_activity_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT pk_post            PRIMARY KEY (id),
  CONSTRAINT fk_post_author     FOREIGN KEY (author_id)   REFERENCES app_user(id),
  CONSTRAINT fk_post_category   FOREIGN KEY (category_id) REFERENCES category(id),
  CONSTRAINT fk_post_pinned_by  FOREIGN KEY (pinned_by)   REFERENCES app_user(id),
  CONSTRAINT ck_post_pinned     CHECK (is_pinned IN (0,1)),
  CONSTRAINT ck_post_status     CHECK (status IN ('visible','removed')),
  -- A pinned post must record who pinned it. An unpinned post must not.
  CONSTRAINT ck_post_pin_pair   CHECK (
    (is_pinned = 0 AND pinned_by IS NULL) OR
    (is_pinned = 1 AND pinned_by IS NOT NULL)),
  CONSTRAINT ck_post_counts     CHECK (vote_score >= 0 AND reply_count >= 0)
) ENGINE = InnoDB;

-- REPLY: threaded under a post, optionally nested under another reply.
CREATE TABLE reply (
  id               CHAR(36)    NOT NULL DEFAULT (UUID()),
  post_id          CHAR(36)    NOT NULL,
  parent_reply_id  CHAR(36),
  author_id        CHAR(36)    NOT NULL,
  body             TEXT        NOT NULL,
  vote_score       INT         NOT NULL DEFAULT 0,
  status           VARCHAR(10) NOT NULL DEFAULT 'visible',
  created_at       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT pk_reply          PRIMARY KEY (id),
  -- (id, post_id) pair lets other tables prove a reply belongs to a given post
  CONSTRAINT uq_reply_id_post  UNIQUE (id, post_id),
  CONSTRAINT fk_reply_post     FOREIGN KEY (post_id)   REFERENCES post(id) ON DELETE CASCADE,
  CONSTRAINT fk_reply_author   FOREIGN KEY (author_id) REFERENCES app_user(id),
  -- A nested reply must sit under a parent in the SAME post
  CONSTRAINT fk_reply_parent   FOREIGN KEY (parent_reply_id, post_id)
    REFERENCES reply(id, post_id) ON DELETE CASCADE,
  CONSTRAINT ck_reply_score    CHECK (vote_score >= 0),
  CONSTRAINT ck_reply_status   CHECK (status IN ('visible','removed'))
  -- Oracle's ck_reply_not_own_parent is gone: MySQL forbids a CHECK on a
  -- column that also has ON DELETE CASCADE (error 3823). Enforce
  -- "parent_reply_id <> id" in Java instead.
) ENGINE = InnoDB;

-- TAG: optional labels such as an employer or role name.
CREATE TABLE tag (
  id        INT         NOT NULL AUTO_INCREMENT,
  name      VARCHAR(80) NOT NULL,
  tag_type  VARCHAR(10) NOT NULL DEFAULT 'other',
  CONSTRAINT pk_tag       PRIMARY KEY (id),
  CONSTRAINT uq_tag_name  UNIQUE (name),
  CONSTRAINT ck_tag_type  CHECK (tag_type IN ('employer','role','other'))
) ENGINE = InnoDB;

-- POST_TAG: many-to-many bridge between POST and TAG.
CREATE TABLE post_tag (
  post_id  CHAR(36) NOT NULL,
  tag_id   INT      NOT NULL,
  CONSTRAINT pk_post_tag       PRIMARY KEY (post_id, tag_id),
  CONSTRAINT fk_post_tag_post  FOREIGN KEY (post_id) REFERENCES post(id) ON DELETE CASCADE,
  CONSTRAINT fk_post_tag_tag   FOREIGN KEY (tag_id)  REFERENCES tag(id)  ON DELETE CASCADE
) ENGINE = InnoDB;

-- POST_VOTE: composite PK means one vote per user per post.
CREATE TABLE post_vote (
  post_id     CHAR(36)   NOT NULL,
  user_id     CHAR(36)   NOT NULL,
  vote_value  TINYINT    NOT NULL DEFAULT 1,
  created_at  DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT pk_post_vote        PRIMARY KEY (post_id, user_id),
  CONSTRAINT fk_post_vote_post   FOREIGN KEY (post_id) REFERENCES post(id) ON DELETE CASCADE,
  CONSTRAINT fk_post_vote_user   FOREIGN KEY (user_id) REFERENCES app_user(id),
  CONSTRAINT ck_post_vote_value  CHECK (vote_value = 1)        -- upvote only, per SOW
) ENGINE = InnoDB;

-- REPLY_VOTE: same idea for replies.
CREATE TABLE reply_vote (
  reply_id    CHAR(36)   NOT NULL,
  user_id     CHAR(36)   NOT NULL,
  vote_value  TINYINT    NOT NULL DEFAULT 1,
  created_at  DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT pk_reply_vote         PRIMARY KEY (reply_id, user_id),
  CONSTRAINT fk_reply_vote_reply   FOREIGN KEY (reply_id) REFERENCES reply(id) ON DELETE CASCADE,
  CONSTRAINT fk_reply_vote_user    FOREIGN KEY (user_id)  REFERENCES app_user(id),
  CONSTRAINT ck_reply_vote_value   CHECK (vote_value = 1)
) ENGINE = InnoDB;

-- FLAG: a user reports a post, or a specific reply inside a post.
CREATE TABLE flag (
  id          CHAR(36)     NOT NULL DEFAULT (UUID()),
  post_id     CHAR(36)     NOT NULL,
  reply_id    CHAR(36),
  flagged_by  CHAR(36)     NOT NULL,
  reason      VARCHAR(500) NOT NULL,
  status      VARCHAR(10)  NOT NULL DEFAULT 'open',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT pk_flag         PRIMARY KEY (id),
  CONSTRAINT fk_flag_post    FOREIGN KEY (post_id)    REFERENCES post(id) ON DELETE CASCADE,
  CONSTRAINT fk_flag_user    FOREIGN KEY (flagged_by) REFERENCES app_user(id),
  -- If a reply is flagged, it must belong to the post on the same row.
  -- When reply_id is NULL, MySQL skips this check (flag is on the post itself).
  CONSTRAINT fk_flag_reply   FOREIGN KEY (reply_id, post_id)
    REFERENCES reply(id, post_id) ON DELETE CASCADE,
  CONSTRAINT ck_flag_status  CHECK (status IN ('open','actioned','dismissed'))
) ENGINE = InnoDB;

-- MODERATION_ACTION: at most one resolving action per flag (UNIQUE flag_id).
CREATE TABLE moderation_action (
  id            CHAR(36)     NOT NULL DEFAULT (UUID()),
  flag_id       CHAR(36)     NOT NULL,
  moderator_id  CHAR(36)     NOT NULL,
  action        VARCHAR(10)  NOT NULL,
  reason        VARCHAR(500) NOT NULL,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT pk_moderation_action  PRIMARY KEY (id),
  CONSTRAINT uq_mod_flag           UNIQUE (flag_id),
  CONSTRAINT fk_mod_flag           FOREIGN KEY (flag_id)      REFERENCES flag(id),
  CONSTRAINT fk_mod_user           FOREIGN KEY (moderator_id) REFERENCES app_user(id),
  CONSTRAINT ck_mod_action         CHECK (action IN ('remove','warn','dismiss'))
) ENGINE = InnoDB;

-- FORUM_USER_SETTINGS: forum-only per-user data, 1-to-0..1 with APP_USER.
CREATE TABLE forum_user_settings (
  user_id            CHAR(36)   NOT NULL,
  hide_post_history  TINYINT(1) NOT NULL DEFAULT 0,
  reputation         INT        NOT NULL DEFAULT 0,
  CONSTRAINT pk_forum_user_settings  PRIMARY KEY (user_id),
  CONSTRAINT fk_fus_user  FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE,
  CONSTRAINT ck_fus_hide  CHECK (hide_post_history IN (0,1))
) ENGINE = InnoDB;


/* =====================================================================
   3. CONSTRAINTS THAT NEED BOTH TABLES TO EXIST
   The helpful reply must be a reply ON THIS POST. Skipped when
   helpful_reply_id is NULL ("no helpful reply yet").
   ===================================================================== */
ALTER TABLE post ADD CONSTRAINT fk_post_helpful_reply
  FOREIGN KEY (helpful_reply_id, id) REFERENCES reply(id, post_id);


/* =====================================================================
   4. INDEXES
   InnoDB auto-creates an index for each FK if none exists, but these
   composite ones match how the API filters and sorts.
   ===================================================================== */
CREATE INDEX ix_post_category_activity ON post (category_id, status, last_activity_at);
CREATE INDEX ix_post_author            ON post (author_id);
CREATE INDEX ix_post_unanswered        ON post (status, reply_count, created_at);
CREATE INDEX ix_reply_post             ON reply (post_id, created_at);
CREATE INDEX ix_reply_author           ON reply (author_id);
CREATE INDEX ix_reply_parent           ON reply (parent_reply_id, post_id);
CREATE INDEX ix_post_tag_tag           ON post_tag (tag_id);
CREATE INDEX ix_post_vote_user         ON post_vote (user_id);
CREATE INDEX ix_reply_vote_user        ON reply_vote (user_id);
CREATE INDEX ix_flag_status            ON flag (status, created_at);
CREATE INDEX ix_flag_post_reply        ON flag (reply_id, post_id);

-- Full-text search on posts. No special role needed in MySQL.
-- Query with: WHERE MATCH(title, body) AGAINST ('amazon interview' IN NATURAL LANGUAGE MODE)
CREATE FULLTEXT INDEX ftx_post_title_body ON post (title, body);


/* =====================================================================
   5. VIEWS
   ===================================================================== */

-- "Unanswered" tab: visible posts with no replies.
CREATE OR REPLACE VIEW v_unanswered_posts AS
SELECT p.id, p.title, p.category_id, c.name AS category_name,
       p.author_id, p.vote_score, p.created_at
  FROM post p
  JOIN category c ON c.id = p.category_id
 WHERE p.status = 'visible'
   AND p.reply_count = 0;

-- "Trending" tab: visible posts active in the last 7 days.
CREATE OR REPLACE VIEW v_trending_posts AS
SELECT p.id, p.title, p.category_id, p.vote_score, p.reply_count,
       p.last_activity_at,
       (p.reply_count * 2 + p.vote_score) AS trend_score
  FROM post p
 WHERE p.status = 'visible'
   AND p.last_activity_at >= NOW() - INTERVAL 7 DAY;

-- Moderation queue for STAFF and ADMIN (role check happens in the API).
CREATE OR REPLACE VIEW v_moderation_queue AS
SELECT f.id AS flag_id, f.post_id, p.title AS post_title,
       f.reply_id, f.flagged_by, f.reason, f.created_at
  FROM flag f
  JOIN post p ON p.id = f.post_id
 WHERE f.status = 'open';


/* =====================================================================
   6. SEED DATA: the six SOW categories
   ===================================================================== */
INSERT INTO category (name, description, sort_order) VALUES
  ('Interview Prep',         'Interview questions, loops, and debriefs', 1),
  ('Resume & Applications',  'Resumes, cover letters, and applying',     2),
  ('Offer & Negotiation',    'Offers, comparing them, and negotiating',  3),
  ('Industry Insight',       'What working in a given field is like',    4),
  ('Career Fairs & Events',  'Fairs, info sessions, and workshops',      5),
  ('General',                'Anything else about the search',           6);
