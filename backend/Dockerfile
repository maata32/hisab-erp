# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
RUN apk add --no-cache maven
COPY pom.xml .
COPY shared/pom.xml shared/pom.xml
COPY tenant/pom.xml tenant/pom.xml
COPY identity/pom.xml identity/pom.xml
COPY audit/pom.xml audit/pom.xml
COPY bootstrap/pom.xml bootstrap/pom.xml
RUN mvn -B -ntp -pl bootstrap -am dependency:go-offline -DskipTests || true
COPY shared/src shared/src
COPY tenant/src tenant/src
COPY identity/src identity/src
COPY audit/src audit/src
COPY bootstrap/src bootstrap/src
RUN mvn -B -ntp -pl bootstrap -am package -DskipTests
RUN cp bootstrap/target/bootstrap-*.jar /workspace/app.jar

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl tzdata gcompat && \
    cp /usr/share/zoneinfo/Africa/Nouakchott /etc/localtime && \
    echo "Africa/Nouakchott" > /etc/timezone && \
    addgroup -S minierp && adduser -S -G minierp minierp
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
USER minierp
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health/liveness || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
