# --- Build stage ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -q dependency:go-offline

COPY src/ src/
# application.properties that (chua secret dev) khong duoc bake vao image (xem .dockerignore).
# Dung file .example lam baseline (co gia tri mac dinh cho cac key khong phai secret nhu token expiration,
# VNPay URL) - cac key secret (jwt.secret, vnpay.tmn-code, vnpay.hash-secret, datasource...) duoc override
# boi env var luc runtime (xem docker-compose.yml), vi Spring uu tien env var hon file properties.
RUN cp src/main/resources/application.properties.example src/main/resources/application.properties
RUN ./mvnw -q package -DskipTests

# --- Run stage ---
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system --create-home appuser
COPY --from=build /app/target/*.jar app.jar
USER appuser

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
