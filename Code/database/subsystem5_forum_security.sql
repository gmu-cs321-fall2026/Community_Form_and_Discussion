/* =====================================================================
   Subsystem 5 Forum: database accounts and permissions (MySQL 8)
   Run AFTER subsystem5_forum_mysql.sql, as root (or another admin).

   Why this file exists:
   The Java app should never connect as root. If an attacker ever slips
   SQL past the app (SQL injection), they can only do what the app's
   own account is allowed to do. So we give each account the smallest
   set of permissions it needs.

   Parameterized queries in the Java layer are the main defense against
   injection. This file is the second wall behind it. See
   docs/Database Docs/SQL_INJECTION_GUIDE.md for the Java side.

   Accounts:
     forum_app      the Spring Boot backend. Read/write on forum tables,
                    no DELETE, no schema changes, append-only mod log.
     forum_report   read-only, views only. For dashboards or the
                    Quality & Integration team poking at data.
   ===================================================================== */

USE mason_forum;


/* =====================================================================
   0. CLEAN UP (safe to re-run)
   ===================================================================== */
DROP USER IF EXISTS 'forum_app'@'%', 'forum_report'@'%';
DROP ROLE IF EXISTS 'forum_app_role', 'forum_report_role';


/* =====================================================================
   1. ROLES
   Grants go on roles, not straight onto users. If we add a second app
   server later, it just gets the same role.
   ===================================================================== */
CREATE ROLE 'forum_app_role', 'forum_report_role';

-- ---- forum_app_role ------------------------------------------------
-- Shared Core owns app_user. The forum only reads it.
GRANT SELECT ON mason_forum.app_user TO 'forum_app_role';

-- Category list is admin-managed. The app reads it. Admin changes go
-- through a separate admin account, not the public-facing app.
GRANT SELECT ON mason_forum.category TO 'forum_app_role';

-- Posts and replies: create and edit only. NO DELETE.
-- The app removes content with status = 'removed' (soft delete), so a
-- hijacked query can't wipe threads.
GRANT SELECT, INSERT, UPDATE ON mason_forum.post  TO 'forum_app_role';
GRANT SELECT, INSERT, UPDATE ON mason_forum.reply TO 'forum_app_role';

-- Tags: users can create new employer/role tags and attach them.
GRANT SELECT, INSERT         ON mason_forum.tag      TO 'forum_app_role';
GRANT SELECT, INSERT, DELETE ON mason_forum.post_tag TO 'forum_app_role';

-- Votes: DELETE is needed so a user can take back an upvote.
GRANT SELECT, INSERT, DELETE ON mason_forum.post_vote  TO 'forum_app_role';
GRANT SELECT, INSERT, DELETE ON mason_forum.reply_vote TO 'forum_app_role';

-- Flags: create them, and UPDATE status when a moderator resolves one.
GRANT SELECT, INSERT, UPDATE ON mason_forum.flag TO 'forum_app_role';

-- Moderation log is APPEND-ONLY. No UPDATE, no DELETE.
-- Even with an injection bug, nobody can rewrite who removed what.
GRANT SELECT, INSERT ON mason_forum.moderation_action TO 'forum_app_role';

-- Per-user settings (hide history toggle, reputation).
GRANT SELECT, INSERT, UPDATE ON mason_forum.forum_user_settings TO 'forum_app_role';

-- The three views the API reads from.
GRANT SELECT ON mason_forum.v_unanswered_posts TO 'forum_app_role';
GRANT SELECT ON mason_forum.v_trending_posts   TO 'forum_app_role';
GRANT SELECT ON mason_forum.v_moderation_queue TO 'forum_app_role';

-- ---- forum_report_role ---------------------------------------------
-- Views only. No raw tables, so no emails and no flagger identities
-- beyond what the views already expose.
GRANT SELECT ON mason_forum.v_unanswered_posts TO 'forum_report_role';
GRANT SELECT ON mason_forum.v_trending_posts   TO 'forum_report_role';
GRANT SELECT ON mason_forum.v_moderation_queue TO 'forum_report_role';

-- Notice what is NOT granted to anyone here:
--   DROP, ALTER, CREATE, INDEX, GRANT OPTION, FILE, PROCESS, SUPER,
--   and anything on other databases (mysql.*, information about other
--   schemas). Those are the permissions an injection attack goes after.


/* =====================================================================
   2. USERS
   CHANGE THE PASSWORDS before running this anywhere except your own
   laptop, and keep the real ones out of git. Pass them to the app with
   an environment variable (e.g. SPRING_DATASOURCE_PASSWORD) instead of
   hard-coding them in application.properties.

   '%' means "any host". Inside docker-compose you can narrow it to the
   backend's container name, e.g. 'forum_app'@'backend'.
   ===================================================================== */
CREATE USER 'forum_app'@'%'
  IDENTIFIED BY 'CHANGE_ME_app_password'
  WITH MAX_USER_CONNECTIONS 20           -- one bad loop can't hog the server
  FAILED_LOGIN_ATTEMPTS 5 PASSWORD_LOCK_TIME 1;   -- lock 1 day after 5 bad tries

CREATE USER 'forum_report'@'%'
  IDENTIFIED BY 'CHANGE_ME_report_password'
  WITH MAX_USER_CONNECTIONS 5
  FAILED_LOGIN_ATTEMPTS 5 PASSWORD_LOCK_TIME 1;

-- Optional, once the server has TLS set up:
-- ALTER USER 'forum_app'@'%' REQUIRE SSL;

GRANT 'forum_app_role'    TO 'forum_app'@'%';
GRANT 'forum_report_role' TO 'forum_report'@'%';

-- Roles in MySQL are OFF at login unless set as default.
SET DEFAULT ROLE ALL TO 'forum_app'@'%', 'forum_report'@'%';


/* =====================================================================
   3. CHECK IT
   ===================================================================== */
SHOW GRANTS FOR 'forum_app'@'%'    USING 'forum_app_role';
SHOW GRANTS FOR 'forum_report'@'%' USING 'forum_report_role';
