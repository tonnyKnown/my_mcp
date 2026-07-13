ARG BASE_IMAGE=eclipse-temurin:21-jre
FROM ${BASE_IMAGE}

WORKDIR /app

ARG JAR_FILE=target/mysql-plugin-1.0.0.jar
COPY ${JAR_FILE} app.jar

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
