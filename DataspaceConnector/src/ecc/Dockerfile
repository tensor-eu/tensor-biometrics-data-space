# First stage: Install maven dependencies
FROM maven:3.8.5-openjdk-11-slim AS builder

# Install CA certificates and openssl to allow Maven SSL validation
RUN apt-get update && apt-get install -y ca-certificates openssl && update-ca-certificates

ENV GH_PACKAGE_REPO_USERNAME="Enter Github Username here"
ENV GH_PACKAGE_REPO_PASSWORD="Enter Github PAT here"

ENV MAVEN_OPTS="-Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true"

COPY src /home/app/src
COPY pom.xml /home/app
COPY settings.xml /home/app
WORKDIR /home/app
RUN mvn -s /home/app/settings.xml -f /home/app/pom.xml clean package

# Second stage: Build the application jar
FROM eclipse-temurin:11-jre-alpine

RUN apk add --no-cache wget openssl curl cosign

RUN mkdir -p /home/nobody/data/sd && mkdir -p /home/nobody/data/log/ecc && mkdir -p /.sigstore

WORKDIR /home/nobody

COPY --from=builder /home/app/target/dependency-jars /home/nobody/app/dependency-jars
COPY --from=builder /home/app/target/application.jar /home/nobody/app/application.jar

RUN chown -R nobody:nogroup /home/nobody && chown -R nobody:nogroup /.sigstore 

USER 65534

# Run the jar file
ENTRYPOINT java -jar /home/nobody/app/application.jar

#Healthy Status
HEALTHCHECK --interval=5s --retries=12 --timeout=10s CMD curl --fail -k https://localhost:8449/about/version || exit 1
