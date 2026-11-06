FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target/dependency

FROM eclipse-temurin:25-jre

WORKDIR /app
COPY --from=build /workspace/target/beit-mama-1.0.0.jar /app/app.jar
COPY --from=build /workspace/target/dependency/ /app/dependency/
COPY web/ /app/web/
RUN chown -R 10001:1000 /app

ENV BEITMAMA_HEADLESS=true
ENV BEITMAMA_PRODUCTION=true

USER 10001:1000
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java -Dbeitmama.port=${PORT:-8080} -cp '/app/app.jar:/app/dependency/*' com.beitmama.Main"]
