# Step 1: Build stage
FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline

COPY src ./src
RUN mvn clean package -DskipTests

# Step 2: Run stage
FROM eclipse-temurin:17-jdk

LABEL maintainer="Umesh Shinde <umesh3386>"
LABEL description="Unipay — Campus Cashless Payment Backend"
LABEL version="1.0"

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]