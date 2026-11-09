```text
community-forum-project/
├── Dockerfile
├── forum-backend/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/
│       │   │   └── com/
│       │   │       └── forum/
│       │   │           ├── ForumApplication.java
│       │   │           ├── controller/
│       │   │           │   └─ ForumController.java
│       │   │           ├── model/
│       │   │           │   ├── Flag.java
│       │   │           │   ├── Post.java
│       │   │           │   └─ Reply.java
│       │   │           └── repository/
│       │   │               ├── FlagRepository.java
│       │   │               └─ PostRepository.java
│       │   └── resources/
│       │       └── application.properties
│       └── test/
├── forum-frontend/
│   ├── postTest.html
│   ├── profile.html
│   └── testPost.js
└── forum-base/
    └── subsystem5_forum_mysql.sql
