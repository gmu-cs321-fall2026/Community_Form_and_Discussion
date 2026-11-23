# SQL Injection Guide for the Forum Backend

This is for anyone writing Java code that talks to `mason_forum`.

There are two walls between an attacker and our data:

1. **The Java code** uses parameterized queries, so user input is never run as SQL. This is the wall that actually stops injection.
2. **The database account** (`forum_app`) can't drop tables, delete posts, touch the moderation log, or read anything outside our schema. See `Code/database/subsystem5_forum_security.sql`. If wall 1 ever has a hole, wall 2 limits the damage.

You need both. Wall 2 alone still lets an attacker read every post and flag. Wall 1 alone means one sloppy query gives away everything.

## Rule 1: Never glue user input into a SQL string

Every value from a request (title, body, search text, category id, post id, tag) goes in as a `?` parameter.

**Bad.** A title like `x'); DROP TABLE post; --` becomes real SQL:

```java
String sql = "INSERT INTO post (author_id, category_id, title, body) VALUES ('"
           + authorId + "', " + categoryId + ", '" + title + "', '" + body + "')";
jdbcTemplate.update(sql);
```

**Good.** MySQL treats the title as text, no matter what's in it:

```java
jdbcTemplate.update(
    "INSERT INTO post (author_id, category_id, title, body) VALUES (?, ?, ?, ?)",
    authorId, categoryId, title, body);
```

Plain JDBC works the same way with `PreparedStatement` and `setString` / `setInt`. If you're on Spring Data JPA, `@Query` with `:name` or `?1` parameters is also safe. Building a JPQL string with `+` is not.

`security_tests.sql` test A3 proves this. It inserts that exact evil title with a parameter, and the post table is still there afterward.

## Rule 2: Things `?` can't protect, so use a whitelist

Parameters only work for **values**. They don't work for column names, sort direction, or table names. That matters for `GET /posts?sort=...`.

**Bad:**

```java
String sql = "SELECT ... FROM post WHERE status = 'visible' ORDER BY " + sort;
```

**Good.** Map the user's choice to a fixed string you wrote yourself:

```java
private static final Map<String, String> SORTS = Map.of(
    "recency",    "p.is_pinned DESC, p.last_activity_at DESC",
    "votes",      "p.is_pinned DESC, p.vote_score DESC",
    "unanswered", "p.created_at DESC");

String orderBy = SORTS.get(sort);
if (orderBy == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bad sort");
String sql = "SELECT ... FROM post p WHERE p.status = 'visible' ORDER BY " + orderBy
           + " LIMIT ? OFFSET ?";
```

The user picks the key. They never get to write the SQL.

## Rule 3: Specific cases in our API

| Endpoint / feature | How to do it safely |
|---|---|
| Paging (`page`, `size`) | Parse to `int`, clamp `size` to something like 1..50, pass as `LIMIT ? OFFSET ?`. |
| Full-text search | `WHERE MATCH(title, body) AGAINST (? IN NATURAL LANGUAGE MODE)`. The search text is a parameter. |
| `LIKE` search | `title LIKE CONCAT('%', ?, '%')`. Also escape `%` and `_` in the input, or a search for `%` matches everything. |
| Filter by several tags | Build one `?` per tag (`IN (?, ?, ?)`) and pass the tag list as parameters. Never `String.join` the tags into the SQL. |
| `{id}` in the URL | Check it's a valid UUID (`UUID.fromString`) before it gets near a query. Return 400 if it isn't. |
| Category filter | Parse to `int`. Return 400 if it doesn't parse. |

## Rule 4: Connection settings

- Connect as **`forum_app`**, never `root`.
- Don't put the password in `application.properties` in git. Use an environment variable:
  ```properties
  spring.datasource.url=jdbc:mysql://db:3306/mason_forum
  spring.datasource.username=forum_app
  spring.datasource.password=${FORUM_DB_PASSWORD}
  ```
  Then set `FORUM_DB_PASSWORD` in `docker-compose.yml` from a `.env` file, and add `.env` to `.gitignore`.
- Don't add `allowMultiQueries=true` to the JDBC URL. It's off by default. If it's on, one injected `;` can run a second statement.

## Rule 5: Don't leak SQL errors

If a query fails, log the details on the server and send the client a plain message like "Something went wrong." A raw `SQLException` message tells an attacker table names, column names, and which payload almost worked.

## Rule 6: Permissions still apply

Database permissions stop damage, but they don't check **who** is asking. Before you hit the database, the Java layer still has to check:

- Only Staff can pin (`is_pinned`).
- Only Staff and Admin can open the moderation queue or create `moderation_action` rows.
- Only a post's author can mark a reply as helpful.
- Users can't vote on their own posts.
- A user can't edit someone else's post.

The `forum_app` account is allowed to do all of those things, because the app needs them. The role check in Java is what decides whether this particular user gets to.

## Quick checklist for code review

- [ ] No `+` or `String.format` building SQL with request data
- [ ] Every sort or column choice goes through a whitelist map
- [ ] Ids and numbers parsed and validated before use
- [ ] Connects as `forum_app`, password from an env var
- [ ] SQL errors are logged, not returned to the client
- [ ] Role and ownership checked before writes
