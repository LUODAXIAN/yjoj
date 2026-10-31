FROM maven:3.5-jdk-8-alpine AS builder
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM openjdk:8-jdk-alpine
WORKDIR /app
COPY --from=builder /build/target/yjoj-backend-1.0.0.jar app.jar
EXPOSE 8121
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
