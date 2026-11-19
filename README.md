```text
community-forum-project/
├── Dockerfile
├── forum-backend/
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/
│       │   │   └── com/
│       │   │       └── forum/
│       │   │           ├── ForumBackendApplication.java
│       │   │           ├── controller/
│       │   │           │   └─ ForumController.java             
│       │   │           ├── model/
│       │   │           │   ├── AppUser.java
│       │   │           │   ├── Flag.java
│       │   │           │   ├── ModerationAction.java           
│       │   │           │   ├── Post.java                       
│       │   │           │   └─ Reply.java
│       │   │           ├── repository/
│       │   │           │   ├── FlagRepository.java
│       │   │           │   ├── ModerationActionRepository.java 
│       │   │           │   ├── PostRepository.java             
│       │   │           │   └─ UserRepository.java
│       │   │           └── service/
│       │   │               └─ PostService.java                 
│       │   └── resources/
│       │       └── application.properties
│       └── test/
├── forum-frontend/
│   ├── postTest.html               
│   ├── profile.html                
│   ├── moderation.html             
│   └── testPost.js                 
└── forum-database/
    └── subsystem5_forum_mysql.sql
