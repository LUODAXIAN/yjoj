FROM openjdk:8-jdk-alpine
WORKDIR /app
COPY target/yjoj-backend-gateway-1.0.0.jar app.jar
EXPOSE 8101
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
