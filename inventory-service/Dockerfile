# Build context is the REPOSITORY ROOT (docker-compose: context ".", dockerfile "inventory-service/Dockerfile"), so the
# root reactor POM and the shared platform-commons module are available (R-INF-02).
FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY . .
# Shared Maven download cache (BuildKit): each library is downloaded once and reused by every service image,
# and an interrupted download does not discard what was already fetched.
RUN --mount=type=cache,id=pet-planet-m2,target=/root/.m2,sharing=locked \
    mvn -B -pl inventory-service -am package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app --no-create-home app
COPY --from=build /workspace/inventory-service/target/inventory-service-0.0.1-SNAPSHOT.jar app.jar
USER app
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
