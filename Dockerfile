FROM openjdk:22-jdk-slim

WORKDIR /app

COPY .mvn/ .mvn
COPY mvnw pom.xml ./

RUN ./mvnw dependency:resolve

COPY src ./src

RUN ./mvnw package -DskipTests

ARG JAR_FILE=target/chat-0.0.1-SNAPSHOT.jar

EXPOSE 8080


ENTRYPOINT ["java", "-jar", "target/chat-0.0.1-SNAPSHOT.jar"]