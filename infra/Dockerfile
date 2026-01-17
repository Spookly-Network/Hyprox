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

COPY --from=build /app/hyprox.jar /app/hyprox.jar
COPY --from=build /app/build/runtime /app/runtime

ENTRYPOINT ["java", "-cp", "/app/hyprox.jar:/app/runtime/*", "net.spookly.hyprox.HyproxMain"]
CMD ["--config", "/config/hyprox.yaml"]
