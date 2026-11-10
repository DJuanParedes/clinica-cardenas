# ---------- 1) Compilación ----------
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

# ---------- 2) JRE mínimo con jlink (~60 MB en lugar de ~190 MB) ----------
FROM eclipse-temurin:21-jdk-alpine AS jre
RUN jlink \
      --add-modules java.base,java.compiler,java.desktop,java.instrument,java.logging,java.management,java.naming,java.net.http,java.prefs,java.rmi,java.scripting,java.security.jgss,java.security.sasl,java.sql,java.transaction.xa,java.xml,java.xml.crypto,jdk.charsets,jdk.crypto.ec,jdk.management,jdk.naming.dns,jdk.net,jdk.unsupported,jdk.zipfs,jdk.localedata \
      --include-locales=en,es \
      --strip-debug --no-man-pages --no-header-files --compress=zip-6 \
      --output /opt/jre

# ---------- 3) Imagen final ----------
FROM alpine:3.20
ENV JAVA_HOME=/opt/jre PATH="/opt/jre/bin:${PATH}" TZ=America/Lima
RUN apk add --no-cache tzdata wget && addgroup -S app && adduser -S app -G app \
 && mkdir -p /data/archivos && chown app:app /data/archivos
COPY --from=jre /opt/jre /opt/jre
WORKDIR /app
COPY --from=build /src/target/app.jar app.jar
USER app
EXPOSE 8080
# Ajustes para poca memoria: GC serial, heap relativo al límite del contenedor, arranque rápido.
ENV JAVA_OPTS="-XX:+UseSerialGC -XX:MaxRAMPercentage=70 -XX:TieredStopAtLevel=1 -Xss512k -XX:+ExitOnOutOfMemoryError -Duser.timezone=America/Lima"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
