# =====================================================
# STAGE 1: BUILD SPRING BOOT APPLICATION
# =====================================================

FROM eclipse-temurin:25-jdk AS build

WORKDIR /app

# Copy Maven wrapper and project configuration
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Fix Windows line endings and make Maven wrapper executable
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw

# Copy application source code
COPY src/ src/

# Build executable Spring Boot JAR
RUN ./mvnw -B clean package -DskipTests


# =====================================================
# STAGE 2: RUN SPRING BOOT APPLICATION
# =====================================================

FROM eclipse-temurin:25-jre

WORKDIR /app

# Copy generated JAR from build stage
COPY --from=build /app/target/*.jar app.jar

# Render application port
EXPOSE 8080

# Start Spring Boot application
ENTRYPOINT ["sh", "-c", "java -XX:MaxRAMPercentage=65.0 -Dserver.port=${PORT:-8080} -jar app.jar"]
