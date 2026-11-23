```
community-forum-project/
├── forum-backend/
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/
│       └── main/
│           ├── java/com/forum/
│           │   ├── ForumBackendApplication.java
│           │   ├── controller/
│           │   │   └── ForumController.java      
│           │   ├── service/
│           │   │   └── PostService.java          
│           │   ├── model/
│           │   │   ├── AppUser.java              
│           │   │   ├── Post.java
│           │   │   ├── Reply.java
│           │   │   ├── ForumUserSettings.java
│           │   │   ├── Tag.java
│           │   │   ├── ModerationAction.java
│           │   │   └── Flag.java
│           │   └── repository/
│           │       ├── PostRepository.java
│           │       ├── ForumUserSettingsRepository.java
│           │       ├── ModerationActionRepository.java
│           │       ├── TagRepository.java
│           │       ├── UserRepository.java
│           │       └── FlagRepository.java
│           └── resources/
│               └── application.properties        
├── forum-frontend/
│   ├── postTest.html
│   ├── profile.html
│   ├── moderation.html
│    └── testPost.js
└── forum-database/
   └── subsystem5_forum_mysql.sql

Running Docker
docker compose up --build
http://localhost:3000/postTest.html

To run with test data
run the command 
Get-Content subsystem5_forum_tests_2.sql | docker exec -i mason-forum-db mysql -uroot -pcommunity_forum mason_forum
