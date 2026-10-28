FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
COPY lib ./lib
RUN mvn clean package -DskipTests || mvn compile

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /app/target/classes ./target/classes
COPY --from=builder /app/lib ./lib
COPY web ./web
COPY db ./db

ENV BEITMAMA_ADMIN_PASSWORD="AdminPassword2026@"
ENV PORT=8080
EXPOSE 8080

CMD ["sh", "-c", "java -Djava.awt.headless=true -Dbeitmama.port=${PORT} -cp target/classes:lib/* com.beitmama.Main"]
