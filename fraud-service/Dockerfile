FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/fraud-service-1.0.jar app.jar
RUN mkdir -p /app/input
EXPOSE 1099
# Fat JAR via maven-shade-plugin — Main-Class: fraud.FraudRmiService
ENTRYPOINT ["java", "-jar", "app.jar"]