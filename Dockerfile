# ============================================
# Roman Numeral Service - Multi-stage Dockerfile
# ============================================
# Base image: Eclipse Temurin (AdoptOpenJDK successor)
# Java 21 LTS with Alpine for minimal size
# ============================================

# ============================================
# Stage 1: Build
# ============================================
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy Maven files first for better layer caching
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .

# Make mvnw executable
RUN chmod +x mvnw

# Download dependencies (cached if pom.xml unchanged)
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src src

# Build the application (skip tests - they run in CI)
RUN ./mvnw package -DskipTests -B

# ============================================
# Stage 2: Runtime
# ============================================
FROM eclipse-temurin:21-jre-alpine AS runtime

# Security: Run as non-root user
RUN addgroup -g 1000 appgroup && \
    adduser -u 1000 -G appgroup -D appuser

WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /app/target/*.jar app.jar

# Change ownership to non-root user
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Expose ports
# 8080: Application
# 8081: Actuator/Management
EXPOSE 8080 8081

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8081/actuator/health || exit 1

# JVM options for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

# ============================================
# Labels (OCI standard)
# ============================================
LABEL org.opencontainers.image.title="Roman Numeral Service"
LABEL org.opencontainers.image.description="REST API for converting integers to Roman numerals"
LABEL org.opencontainers.image.version="1.0.0"
LABEL org.opencontainers.image.vendor="Adobe AEM Engineering Assessment"
LABEL org.opencontainers.image.source="https://github.com/janakiraman06/roman-numeral-service"

