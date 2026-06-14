ARG SERVICE_MODULE

FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

COPY pom.xml .
COPY monitoring-core/pom.xml monitoring-core/pom.xml
COPY monitoring-spring-boot-starter/pom.xml monitoring-spring-boot-starter/pom.xml
COPY monitoring-server/pom.xml monitoring-server/pom.xml

COPY demo/pom.xml demo/pom.xml
COPY demo/demo-inventory-service/pom.xml demo/demo-inventory-service/pom.xml
COPY demo/demo-order-service/pom.xml demo/demo-order-service/pom.xml
COPY demo/demo-payment-service/pom.xml demo/demo-payment-service/pom.xml
COPY demo/demo-user-service/pom.xml demo/demo-user-service/pom.xml
COPY demo/demo-notification-service/pom.xml demo/demo-notification-service/pom.xml
COPY demo/demo-shipping-service/pom.xml demo/demo-shipping-service/pom.xml
COPY demo/demo-product-service/pom.xml demo/demo-product-service/pom.xml
COPY demo/demo-analytics-service/pom.xml demo/demo-analytics-service/pom.xml

RUN mvn dependency:go-offline -pl monitoring-core,monitoring-spring-boot-starter -am -q 2>/dev/null || true

COPY monitoring-core/src monitoring-core/src
COPY monitoring-spring-boot-starter/src monitoring-spring-boot-starter/src

RUN mvn install -pl monitoring-core,monitoring-spring-boot-starter -am -DskipTests -q

COPY demo/demo-inventory-service/src demo/demo-inventory-service/src
COPY demo/demo-order-service/src demo/demo-order-service/src
COPY demo/demo-payment-service/src demo/demo-payment-service/src
COPY demo/demo-user-service/src demo/demo-user-service/src
COPY demo/demo-notification-service/src demo/demo-notification-service/src
COPY demo/demo-shipping-service/src demo/demo-shipping-service/src
COPY demo/demo-product-service/src demo/demo-product-service/src
COPY demo/demo-analytics-service/src demo/demo-analytics-service/src

ARG SERVICE_MODULE
RUN mvn clean package -f demo/pom.xml -pl ${SERVICE_MODULE} -am -DskipTests -q

FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

ARG SERVICE_MODULE
COPY --from=builder /build/demo/${SERVICE_MODULE}/target/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
