@echo off
set JAVA_HOME=C:\Program Files\Java\jdk-25
set PATH=%JAVA_HOME%\bin;%PATH%
set OPENROUTER_API_KEY=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
mvn spring-boot:run
