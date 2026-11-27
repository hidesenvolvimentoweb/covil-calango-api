FROM maven:3.8.4-openjdk-17 AS build

WORKDIR /app

ARG DATASOURCE_URL
ARG DATASOURCE_USER
ARG DATASOURCE_PASSWORD

ENV SPRING_DATASOURCE_URL=${DATASOURCE_URL}
ENV SPRING_DATASOURCE_USERNAME=${DATASOURCE_USER}
ENV SPRING_DATASOURCE_PASSWORD=${DATASOURCE_PASSWORD}

COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests
# Nome fixo, independente da versão do pom.xml (a versão sobe a cada commit).
RUN cp target/covil-calango-api-*.jar target/app.jar

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/app.jar .

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]