# Build stage: Maven + JDK 11 (gripss targets Java 11). Only the jar is kept from this stage.
FROM maven:3.9-eclipse-temurin-11 AS build

ENV GRIPSS_VER="2.4"

WORKDIR /home/app/hmftools
# The parent POM, the common module and gripss
COPY pom.xml pom.xml
COPY hmf-common hmf-common
COPY gripss gripss

# Install the parent POM and the common module, then package gripss
RUN mvn clean install -N && \
    mvn -f hmf-common/pom.xml clean install && \
    mvn -f gripss/pom.xml clean package && \
    cp gripss/target/gripss-${GRIPSS_VER}-jar-with-dependencies.jar /gripss.jar


# Runtime stage: Java 11 runtime only, on a supported Ubuntu (24.04)
FROM eclipse-temurin:11-jre-noble

ENV OPT=/opt/wtsi-cgp
ENV JAVA=$OPT/java
ENV CLASSPATH=${JAVA}/gripss.jar

# procps (free) and sysstat (iostat) are used by the pipelines' monitoring script
RUN apt-get update && \
    apt-get upgrade -y && \
    apt-get install -y --no-install-recommends procps sysstat && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

COPY --from=build /gripss.jar ${JAVA}/gripss.jar

# Fail the build if the jar does not start or the user is missing (gripss exits 0 on -version)
RUN id ubuntu && java -jar ${JAVA}/gripss.jar -version

# The base image already has the non-root user ubuntu (uid 1000)
USER ubuntu
WORKDIR /home/ubuntu

# add license
COPY LICENSE .

CMD ["/bin/bash"]
