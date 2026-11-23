/* =====================================================================
   Subsystem 5 Forum: security tests (MySQL 8)

   Setup, in order:
     1. subsystem5_forum_mysql.sql      (as root)
     2. subsystem5_forum_tests.sql      PART A only (as root, sample data)
     3. subsystem5_forum_security.sql   (as root)
     4. THIS FILE, logged in as forum_app, NOT root.
        Workbench: make a new connection with user forum_app.
        Command line: mysql -u forum_app -p mason_forum < this_file

   PART A should succeed. PART B is what an attacker would try after an
   injection bug. Every statement in PART B SHOULD FAIL. Run PART B one
   statement at a time with Ctrl+Enter.
   ===================================================================== */
USE mason_forum;


/* =====================================================================
   PART A: NORMAL APP WORK STILL WORKS
   ===================================================================== */

-- A1. Who am I? Expect forum_app, with forum_app_role active.
SELECT CURRENT_USER(), CURRENT_ROLE();

-- A2. Read posts
SELECT id, title FROM post WHERE status = 'visible' LIMIT 5;

-- A3. Parameterized insert. This is how the Java layer should do it.
-- The "title" below is a classic injection string. Because it is sent
-- as a PARAMETER, MySQL stores it as plain text and never runs it.
SET @author = (SELECT id FROM app_user WHERE gmu_email = 'alice@gmu.edu');
SET @cat    = (SELECT id FROM category WHERE name = 'General');
SET @evil   = 'x''); DROP TABLE post; -- ';

PREPARE ins FROM
  'INSERT INTO post (author_id, category_id, title, body) VALUES (?, ?, ?, ?)';
EXECUTE ins USING @author, @cat, @evil, @evil;
DEALLOCATE PREPARE ins;

-- Expect 1 row back with the weird title, AND the post table still exists.
SELECT title FROM post WHERE title = @evil;

-- A4. Parameterized search. Same idea for the search box.
SET @search = ''' OR ''1''=''1';
PREPARE srch FROM
  'SELECT id, title FROM post WHERE status = ''visible'' AND title LIKE CONCAT(''%'', ?, ''%'')';
EXECUTE srch USING @search;      -- expect 0 rows, not "every post"
DEALLOCATE PREPARE srch;

-- A5. Soft delete is allowed (this is how the app removes content)
UPDATE post SET status = 'removed' WHERE title = @evil;


/* =====================================================================
   PART B: ATTACKER MOVES. EACH ONE SHOULD FAIL.
   ===================================================================== */

-- B1. Drop a table                  -> 1142 DROP command denied
DROP TABLE post;

-- B2. Wipe all posts                -> 1142 DELETE command denied
DELETE FROM post;

-- B3. Rewrite the moderation log    -> 1142 UPDATE command denied
UPDATE moderation_action SET reason = 'nothing happened';

-- B4. Erase the moderation log      -> 1142 DELETE command denied
DELETE FROM moderation_action;

-- B5. Promote yourself to admin     -> 1142 UPDATE command denied (app_user is read-only)
UPDATE app_user SET role = 'ADMIN' WHERE gmu_email = 'alice@gmu.edu';

-- B6. Add a fake user               -> 1142 INSERT command denied
INSERT INTO app_user (name, gmu_email, role) VALUES ('Eve', 'eve@gmu.edu', 'ADMIN');

-- B7. Create a new category         -> 1142 INSERT command denied (admins only)
INSERT INTO category (name) VALUES ('Spam');

-- B8. Change the schema             -> 1142 ALTER command denied
ALTER TABLE post ADD COLUMN backdoor TEXT;

-- B9. Read MySQL's own user table (password hashes) -> 1142 SELECT command denied
SELECT user, authentication_string FROM mysql.user;

-- B10. Read files off the server    -> returns NULL, not the file (no FILE privilege)
-- This one doesn't raise an error. MySQL just hands back NULL.
SELECT LOAD_FILE('/etc/passwd');

-- B11. Write a file to the server   -> 1227 access denied (no FILE privilege)
SELECT 'x' INTO OUTFILE '/tmp/pwned.txt';

-- B12. Give yourself more rights    -> 1044 access denied
GRANT ALL ON mason_forum.* TO 'forum_app'@'%';

-- B13. Make a new account           -> 1227 access denied (needs CREATE USER)
CREATE USER 'eve'@'%' IDENTIFIED BY 'eve';
