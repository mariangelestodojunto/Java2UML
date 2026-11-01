# ---------------------------------------------------------------------------
# Java2UML — imagen para la interfaz web (Render, Railway, Koyeb, Fly.io…)
#   docker build -t java2uml .
#   docker run -p 8080:8080 java2uml      →  http://localhost:8080
# ---------------------------------------------------------------------------

# 1) Compilación con Maven (las dependencias se cachean en una capa aparte)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

# 2) Ejecución con un JRE liviano y usuario sin privilegios
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /app/target/java2uml.jar /app/java2uml.jar
USER app
# Render/Railway/Koyeb definen PORT; si no existe se usa 8080
ENV PORT=8080
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Djava.awt.headless=true"
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s CMD wget -qO- "http://127.0.0.1:${PORT}/salud" || exit 1
CMD ["java", "-jar", "/app/java2uml.jar", "--web"]
