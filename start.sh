#!/bin/bash

# define port
PORT=8080

# Absolute or relative path to your JAR file
JAR_PATH="target/spotcolor-1.0.jar"

# Optional: Java options (e.g., heap size)
JAVA_OPTS="-Xms256m -Xmx512m"

# Run your Spring Boot JAR
java $JAVA_OPTS -jar "$JAR_PATH" --server.port=$PORT
