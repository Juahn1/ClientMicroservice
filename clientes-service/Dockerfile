# Contexto de build: la raíz del repo (necesita el POM padre).
#   docker build -f clientes-service/Dockerfile -t crm/clientes-service .

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY contracts/pom.xml contracts/
COPY clientes-service/pom.xml clientes-service/
RUN mvn -B -q -pl clientes-service -am dependency:go-offline
COPY contracts/src contracts/src
COPY clientes-service/src clientes-service/src
RUN mvn -B -q -pl clientes-service -am package -DskipTests

FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 clientes
WORKDIR /app
COPY --from=build /build/clientes-service/target/clientes-service-*.jar app.jar
USER clientes
EXPOSE 8082
ENV SPRING_PROFILES_ACTIVE=docker
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
