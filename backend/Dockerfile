FROM eclipse-temurin:17-jdk-jammy AS builder

WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew \
	&& ./gradlew dependencies --no-daemon

COPY src ./src
RUN ./gradlew bootJar --no-daemon \
	&& cp build/libs/backend-0.0.1-SNAPSHOT.jar build/app.jar

FROM eclipse-temurin:17-jre-jammy

RUN apt-get update \
	&& apt-get install --yes --no-install-recommends curl gosu \
	&& rm -rf /var/lib/apt/lists/* \
	&& groupadd --gid 10001 appuser \
	&& useradd --uid 10001 --gid appuser --create-home appuser \
	&& mkdir -p /var/lib/voc-actionops/uploads

WORKDIR /app

COPY --from=builder --chown=appuser:appuser /workspace/build/app.jar ./app.jar
COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh

RUN chmod +x /usr/local/bin/docker-entrypoint.sh

EXPOSE 8080

ENTRYPOINT ["docker-entrypoint.sh"]

CMD ["java", "-jar", "app.jar"]
