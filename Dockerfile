FROM eclipse-temurin:21.0.12.1_1-jdk-noble AS build
WORKDIR /workspace

COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies

COPY src ./src
RUN ./gradlew --no-daemon clean test bootJar
RUN mkdir -p /workspace/build/healthcheck \
    && javac -d /workspace/build/healthcheck src/main/java/ar/edu/efp30/api/ops/ReadinessProbe.java

FROM eclipse-temurin:21.0.12.1_1-jre-noble AS runtime
WORKDIR /app
ENV PORT=8080 \
    MEDIA_ROOT=/var/lib/efp30/media \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"
RUN mkdir -p /var/lib/efp30/media \
    && chown -R 10001:10001 /app /var/lib/efp30
COPY --from=build --chown=10001:10001 /workspace/build/libs/efp30-api.jar /app/efp30-api.jar
COPY --from=build --chown=10001:10001 /workspace/build/healthcheck /opt/efp30/healthcheck
USER 10001:10001
EXPOSE 8080
VOLUME ["/var/lib/efp30/media"]
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 CMD ["java", "-cp", "/opt/efp30/healthcheck", "ar.edu.efp30.api.ops.ReadinessProbe"]
ENTRYPOINT ["java", "-jar", "/app/efp30-api.jar"]

