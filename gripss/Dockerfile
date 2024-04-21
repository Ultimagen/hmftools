# Base image with Maven and JDK
FROM maven:3.8.4-jdk-11

ENV GRIPSS_VER="2.3.5"
ENV OPT /opt/wtsi-cgp
ENV JAVA $OPT/java
ENV CLASSPATH ${JAVA}/gripss.jar

# Create application directory
RUN mkdir -p ${JAVA}

# Copy project files into the Docker image
# Copy the parent POM
COPY pom.xml /home/app/hmftools/pom.xml
# Copy the common module
COPY hmf-common /home/app/hmftools/hmf-common
# Copy gripss module
COPY gripss /home/app/hmftools/gripss

# Navigate to the gripss directory to run Maven commands
WORKDIR /home/app/hmftools/gripss

# Install the required dependencies - parent POM and common module
WORKDIR /home/app/hmftools
RUN mvn clean install -N

WORKDIR /home/app/hmftools/hmf-common
RUN mvn clean install

# Then, navigate to the gripss directory to run Maven commands for gripss
WORKDIR /home/app/hmftools/gripss
RUN mvn clean package && \
    cp target/gripss-${GRIPSS_VER}-jar-with-dependencies.jar ${JAVA}/gripss.jar

# Setup user
RUN adduser --disabled-password --gecos '' ubuntu && \
    mkdir -p /home/ubuntu

USER ubuntu
WORKDIR /home/ubuntu

# add license
COPY LICENSE .

CMD ["/bin/bash"]