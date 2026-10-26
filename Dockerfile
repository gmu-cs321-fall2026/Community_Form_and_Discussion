
# Java 21 environment
FROM eclipse-temurin:21-jdk

# Working directory inside the container
WORKDIR /app

# Copy moderation server
COPY ["Code/Moderation Queue/ModerationWebServer.java", "ModerationWebServer.java"]

# Copy moderation webpage
COPY ["Code/Moderation Queue/index.html", "src/main/resources/static/index.html"]

# Copy forum code and automated tests
COPY ["Code/Post With Features and History/Post With Features and History/ForumPost.java", "ForumPost.java"]
COPY ["Code/Post With Features and History/Post With Features and History/TrendingRanker.java", "TrendingRanker.java"]
COPY ["tests/ForumPostSmokeTest.java", "ForumPostSmokeTest.java"]

# Compile Java source files
RUN mkdir -p classes && \
    javac --add-modules jdk.httpserver -d classes \
    ModerationWebServer.java ForumPost.java TrendingRanker.java ForumPostSmokeTest.java

# Container listens on port 8080
EXPOSE 8080

# Start the moderation server
CMD ["java", "--add-modules", "jdk.httpserver", "-cp", "classes", "ModerationWebServer"]
