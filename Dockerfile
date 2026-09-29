# ---- build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

# ---- runtime stage ----
FROM eclipse-temurin:17-jre
WORKDIR /app

# The app locates its data via pom.xml + src/main/resources, so keep that layout.
COPY --from=build /app/target/no-sql-dbms-minor-java-1.0-SNAPSHOT.jar app.jar
COPY pom.xml .
COPY src/main/resources src/main/resources

ENV PORT=8080
EXPOSE 8080
CMD ["java", "-cp", "app.jar", "WebServer"]
