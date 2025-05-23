@echo off

@REM define port
set PORT=8080

@REM Absolute or relative path to your JAR file
set JAR_PATH="target/spotcolor-1.0.jar"

@REM Optional: Java options (e.g., heap size)
set JAVA_OPTS=-Xms256m -Xmx512m

@REM Run your Spring Boot JAR
java %JAVA_OPTS% -jar %JAR_PATH% --server.port=%PORT%
