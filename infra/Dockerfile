FROM eclipse-temurin:25-jdk AS build

WORKDIR /app

COPY gradlew /app/gradlew
COPY gradle/wrapper/ /app/gradle/wrapper/
COPY build.gradle settings.gradle /app/
COPY libs /app/libs
COPY src /app/src

RUN chmod +x /app/gradlew \
    && /app/gradlew --no-daemon spotlessApply \
    && /app/gradlew --no-daemon clean build copyRuntimeDependencies \
    && jar=$(ls /app/build/libs/*.jar | grep -v "plain.jar" | head -n 1) \
    && if [ -z "$jar" ]; then jar=$(ls /app/build/libs/*.jar | head -n 1); fi \
    && cp "$jar" /app/hyprox.jar

FROM eclipse-temurin:25-jre

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends openssl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/hyprox.jar /app/hyprox.jar
COPY --from=build /app/build/runtime /app/runtime
COPY infra/entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

ENTRYPOINT ["/app/entrypoint.sh"]
