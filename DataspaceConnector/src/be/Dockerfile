# First stage: Install maven dependencies
FROM maven:3.8.5-openjdk-11-slim AS builder

ENV GH_PACKAGE_REPO_USERNAME="Enter Github Username here"
ENV GH_PACKAGE_REPO_PASSWORD="Enter Github PAT here"

COPY src /home/app/src
COPY pom.xml /home/app
COPY settings.xml /home/app
WORKDIR /home/app
RUN mvn -s /home/app/settings.xml -f /home/app/pom.xml clean package

# Second stage: Build the application jar
FROM eclipse-temurin:11-jre-alpine

# Create non-privileged user directory, and make it as workable directory
RUN mkdir -p /home/nobody/app && mkdir -p /home/nobody/data/log/dataapp && mkdir -p /home/nobody/data/datalake && mkdir -p /.sigstore
RUN apk add --no-cache openssl curl cosign

WORKDIR /home/nobody

COPY --from=builder /home/app/target/dependency-jars /home/nobody/app/dependency-jars
COPY --from=builder /home/app/target/application.jar /home/nobody/app/application.jar

RUN chown -R nobody:nogroup /home/nobody && chown -R nobody:nogroup /.sigstore 

USER 65534

# Run the jar file 
ENTRYPOINT java -jar /home/nobody/app/application.jar

# Healthy Status
HEALTHCHECK --interval=5s --retries=12 --timeout=10s CMD curl --fail -k https://localhost:8183/about/version || exit 1