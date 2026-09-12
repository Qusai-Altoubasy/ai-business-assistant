# syntax=docker/dockerfile:1

FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn -B dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B package -Dmaven.test.skip=true

FROM eclipse-temurin:21-jre-jammy AS runtime

RUN groupadd --system --gid 10001 quarkus \
    && useradd --system \
        --uid 10001 \
        --gid 10001 \
        --home-dir /deployments \
        --shell /usr/sbin/nologin \
        --no-create-home \
        quarkus

WORKDIR /deployments
COPY --from=build --chown=quarkus:quarkus /workspace/target/quarkus-app/ ./

USER quarkus
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/deployments/quarkus-run.jar"]
