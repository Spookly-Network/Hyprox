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

ARG HYPROX_UID=10001
ARG HYPROX_GID=10001

RUN apt-get update \
    && apt-get install -y --no-install-recommends openssl ca-certificates gosu \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -g "${HYPROX_GID}" hyprox \
    && useradd -u "${HYPROX_UID}" -g "${HYPROX_GID}" -d /app -s /usr/sbin/nologin hyprox \
    && mkdir -p /config \
    && chown -R hyprox:hyprox /app /config

COPY --from=build --chown=hyprox:hyprox /app/hyprox.jar /app/hyprox.jar
COPY --from=build --chown=hyprox:hyprox /app/build/runtime /app/runtime
COPY --chown=hyprox:hyprox infra/entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

EXPOSE 20000

ENTRYPOINT ["/app/entrypoint.sh"]
