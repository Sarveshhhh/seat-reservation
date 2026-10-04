# Stage 1: Build JAR using Maven
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

# Stage 2: Minimal Java 21 Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
EXPOSE 8080
COPY --from=builder /app/target/*.jar app.jar

ENV PORT=8080
ENV DB_POOL_SIZE=20

ENTRYPOINT ["java", "-jar", "app.jar"]
