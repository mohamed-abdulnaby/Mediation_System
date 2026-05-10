FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/billing-service-1.0.jar app.jar
RUN mkdir -p /app/input
EXPOSE 1099
ENTRYPOINT ["java", "-cp", "app.jar", "billing.BillingRmiService"]