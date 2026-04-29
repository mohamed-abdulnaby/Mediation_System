FROM maven:3.9-eclipse- temurin-21 AS builder
WORKDIR /app
COPY pom. xml ./ COPY src/ ./src/ RUN mvn package -DskipTests
FROM tomcat:11.0- jre21
COPY --from=builder /app/ target/*.war /usr/ local/ tomcat/ webapps/ EXPOSE 80
CMD ["catalina. sh", "run"]
