FROM openjdk:8-jdk-alpine
WORKDIR /app
COPY target/yjoj-backend-user-service-1.0.0.jar app.jar
EXPOSE 8102
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
