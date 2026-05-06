FROM eclipse-temurin:17-jdk-alpine as builder

WORKDIR /app
COPY pom.xml .
COPY victor-common/pom.xml victor-common/
COPY victor-domain/pom.xml victor-domain/
COPY victor-bucketing/pom.xml victor-bucketing/
COPY victor-infrastructure/pom.xml victor-infrastructure/
COPY victor-service/pom.xml victor-service/
COPY victor-sdk/pom.xml victor-sdk/
COPY victor-web/pom.xml victor-web/

# Download dependencies
RUN apk add --no-cache maven && mvn dependency:go-offline -B

# Copy source code
COPY victor-common/ victor-common/
COPY victor-domain/ victor-domain/
COPY victor-bucketing/ victor-bucketing/
COPY victor-infrastructure/ victor-infrastructure/
COPY victor-service/ victor-service/
COPY victor-sdk/ victor-sdk/
COPY victor-web/ victor-web/

# Build application
RUN mvn clean package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app
RUN addgroup -S victor && adduser -S victor -G victor
USER victor:victor

COPY --from=builder /app/victor-web/target/victor-web-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]