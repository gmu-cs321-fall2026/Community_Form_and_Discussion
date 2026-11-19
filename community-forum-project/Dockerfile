FROM eclipse-temurin:21-jdk-alpine AS backend-build
WORKDIR /app

# Install Maven
RUN apk add --no-cache maven

COPY forum-backend/pom.xml ./forum-backend/
COPY forum-backend/src ./forum-backend/src


WORKDIR /app/forum-backend
RUN mvn clean package -DskipTests

# --- Stage 2 ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=backend-build /app/forum-backend/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]