FROM eclipse-temurin:21-jre

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

ARG JAR_FILE=target/foodsharing-einab-tabelle-*.jar
COPY ${JAR_FILE} app.jar

ENV SPRING_PROFILES_ACTIVE=prod
ENV APP_VERSION_FILE=/app/version.txt

EXPOSE 8080

COPY deploy/version.txt /app/version.txt

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
