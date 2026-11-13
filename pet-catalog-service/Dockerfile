FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
# Shared Maven download cache (BuildKit): each library is downloaded once and reused by every service image,
# and an interrupted download does not discard what was already fetched.
RUN --mount=type=cache,id=pet-planet-m2,target=/root/.m2,sharing=locked mvn -B package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app --no-create-home app
COPY --from=build /workspace/target/pet-catalog-service-0.0.1-SNAPSHOT.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
