FROM openjdk:8-jdk-alpine
WORKDIR /app
COPY target/yjoj-backend-question-service-1.0.0.jar app.jar
EXPOSE 8103
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
