# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
RUN apk add --no-cache maven
COPY pom.xml .
COPY shared/pom.xml shared/pom.xml
COPY tenant/pom.xml tenant/pom.xml
COPY identity/pom.xml identity/pom.xml
COPY audit/pom.xml audit/pom.xml
COPY uom/pom.xml uom/pom.xml
COPY catalog/pom.xml catalog/pom.xml
COPY pricing/pom.xml pricing/pom.xml
COPY inventory/pom.xml inventory/pom.xml
COPY notifications/pom.xml notifications/pom.xml
COPY pos/pom.xml pos/pom.xml
COPY customer/pom.xml customer/pom.xml
COPY document/pom.xml document/pom.xml
COPY sales/pom.xml sales/pom.xml
COPY delivery/pom.xml delivery/pom.xml
COPY payment/pom.xml payment/pom.xml
COPY lot-expiry/pom.xml lot-expiry/pom.xml
COPY expense/pom.xml expense/pom.xml
COPY treasury/pom.xml treasury/pom.xml
COPY reporting/pom.xml reporting/pom.xml
COPY purchase/pom.xml purchase/pom.xml
COPY bootstrap/pom.xml bootstrap/pom.xml
RUN mvn -B -ntp -pl bootstrap -am dependency:go-offline -DskipTests || true
COPY shared/src shared/src
COPY tenant/src tenant/src
COPY identity/src identity/src
COPY audit/src audit/src
COPY uom/src uom/src
COPY catalog/src catalog/src
COPY pricing/src pricing/src
COPY inventory/src inventory/src
COPY notifications/src notifications/src
COPY pos/src pos/src
COPY customer/src customer/src
COPY document/src document/src
COPY sales/src sales/src
COPY delivery/src delivery/src
COPY payment/src payment/src
COPY lot-expiry/src lot-expiry/src
COPY expense/src expense/src
COPY treasury/src treasury/src
COPY reporting/src reporting/src
COPY purchase/src purchase/src
COPY bootstrap/src bootstrap/src
RUN mvn -B -ntp -pl bootstrap -am package -DskipTests
RUN cp bootstrap/target/bootstrap-*.jar /workspace/app.jar

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl tzdata gcompat argon2-libs && \
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
