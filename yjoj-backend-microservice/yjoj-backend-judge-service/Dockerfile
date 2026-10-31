FROM openjdk:8-jdk-alpine
WORKDIR /app
COPY target/yjoj-backend-judge-service-1.0.0.jar app.jar
EXPOSE 8104
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
