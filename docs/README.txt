MODERATION QUEUE WEBPAGE — LOCAL JAVA DEMO

Requirements: JDK 11+ and VS Code Java extensions recommended.

Run:
1. Unzip this project.
2. Open the moderation_queue_web folder in VS Code.
3. Open src/main/java/ModerationWebServer.java and click Run above main().
4. Leave the server terminal running and open http://localhost:8080 in your browser.

If the Run button does not appear, use the VS Code terminal in the project folder:
  javac --add-modules jdk.httpserver -d out src/main/java/ModerationWebServer.java
  java --add-modules jdk.httpserver -cp "out;src/main/resources" ModerationWebServer

The semicolon classpath above is for Windows. On macOS/Linux, use:
  java --add-modules jdk.httpserver -cp "out:src/main/resources" ModerationWebServer

Features:
- Flagged posts queue
- Dismiss Flag and Remove Post buttons
- Required reason and confirmation before action
- Decision log with moderator label, action, reason, timestamp
- All Posts view and flag-for-review button
- Local API endpoints:
  GET /api/moderation/queue
  GET /api/moderation/log
  GET /api/posts
  POST /api/posts/{id}/flag
  POST /api/moderation/{id}/action

Important: This is a local in-memory prototype. Data resets when the server restarts. It does not have real authentication, database persistence, CSRF protection, or integration with your existing application. Do not expose it to the internet or use real user data. Before production, connect it to your real backend and enforce Career Services Staff/Admin authorization on the server.
