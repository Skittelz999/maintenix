FROM eclipse-temurin:21-jdk-noble AS builder
WORKDIR /build

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw
COPY src/ src/
# Run the full Testcontainers suite outside the image build (see README).
RUN ./mvnw --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:21-jre-noble AS runtime
WORKDIR /app

RUN groupadd --gid 10001 maintenix \
    && useradd --uid 10001 --gid maintenix --no-create-home --shell /usr/sbin/nologin maintenix
COPY --from=builder /build/target/*.jar /app/app.jar

ENV SPRING_PROFILES_ACTIVE=prod
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
