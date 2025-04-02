@echo off
SET JAVA_HOME=C:\jdk17
echo Using Java from: %JAVA_HOME%
echo.
echo Running Gradle wrapper task at root level...
call gradlew.bat wrapper
echo.
echo Checking Gradle version...
call gradlew.bat --version
echo.
echo Listing available tasks...
call gradlew.bat tasks
@pause 