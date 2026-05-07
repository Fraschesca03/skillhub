# STAGE BUILDER — Compile le JAR avec Maven
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# Copier les fichiers Maven wrapper + POM d'abord pour exploiter le cache Docker
# Si pom.xml ne change pas, Docker réutilise la couche des dépendances
COPY .mvn/ .mvn
COPY mvnw pom.xml ./

# Droits d'exécution + téléchargement des dépendances en offline
RUN chmod +x mvnw && \
    ./mvnw dependency:go-offline -B

# Copier le code source
COPY src ./src

# Build du JAR (sans tests, les tests tournent dans le CI)
RUN ./mvnw clean package -DskipTests -B

# STAGE RUNTIME — Image finale légère
FROM eclipse-temurin:17-jre-alpine AS runtime

WORKDIR /app

# Utilisateur non-root pour la sécurité
RUN addgroup -S spring && \
    adduser -S spring -G spring && \
    chown -R spring:spring /app

USER spring

# Copier le JAR depuis le stage builder
COPY --from=builder --chown=spring:spring /app/target/*.jar app.jar

# Port exposé (Spring Boot par défaut)
EXPOSE 8080

# Santé : endpoint actuator ou simple ping
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --spider -q http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]