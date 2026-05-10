FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/mediation-service-1.0.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]