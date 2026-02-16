# Build stage
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /build

# Copy pom and download dependencies
COPY pom.xml .
RUN apk add --no-cache maven && \
    mvn dependency:go-offline -B

# Build JAR (no tests in image build)
COPY src ./src
RUN mvn package -DskipTests -B && \
    mv target/biasharahub-backend-*.jar target/app.jar

# Run stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -g 1000 app && adduser -u 1000 -G app -D app
USER app

COPY --from=builder /build/target/app.jar ./app.jar

# Env vars: set DB_URL, DB_USERNAME, DB_PASSWORD, REDIS_HOST, REDIS_PORT, etc. at runtime
ENV SERVER_PORT=5050

EXPOSE 5050

ENTRYPOINT ["java", "-jar", "app.jar"]
