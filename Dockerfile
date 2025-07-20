FROM eclipse-temurin:11-jdk-focal

RUN apt-get update && apt-get install -y \
    curl \
    jq \
 && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Copy jar with dependencies
COPY target/CorrelationDetective-1.0-jar-with-dependencies.jar /app/cd.jar

# Copy the entrypoin script and make it executable
COPY run.sh /app/run.sh

RUN chmod +x run.sh

# Create a directory to store input data
RUN mkdir /app/data

ENTRYPOINT ["./run.sh"]
