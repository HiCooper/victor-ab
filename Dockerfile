FROM eclipse-temurin:17-jdk-alpine as builder

WORKDIR /app
COPY pom.xml .
COPY victor-common/pom.xml victor-common/
COPY victor-domain/pom.xml victor-domain/
COPY victor-service/pom.xml victor-service/
COPY victor-sdk/pom.xml victor-sdk/
COPY victor-starter/pom.xml victor-starter/

# Download dependencies
RUN apk add --no-cache maven && mvn dependency:go-offline -B

# Copy source code
COPY victor-common/ victor-common/
COPY victor-domain/ victor-domain/
COPY victor-service/ victor-service/
COPY victor-sdk/ victor-sdk/
COPY victor-starter/ victor-starter/

# Build application
RUN mvn clean package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app
RUN addgroup -S victor && adduser -S victor -G victor
RUN mkdir -p /app/logs && chown victor:victor /app/logs
USER victor:victor

COPY --from=builder /app/victor-starter/target/victor-starter-*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]