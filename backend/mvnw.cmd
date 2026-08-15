@ECHO OFF
SETLOCAL

SET "MAVEN_PROJECT_DIR=%~dp0"
SET "MAVEN_PROJECT_DIR=%MAVEN_PROJECT_DIR:~0,-1%"
SET "MAVEN_WRAPPER_JAR=%~dp0.mvn\wrapper\maven-wrapper.jar"
SET "MAVEN_WRAPPER_MAIN=org.apache.maven.wrapper.MavenWrapperMain"

java -Dmaven.multiModuleProjectDirectory="%MAVEN_PROJECT_DIR%" -cp "%MAVEN_WRAPPER_JAR%" %MAVEN_WRAPPER_MAIN% %*

ENDLOCAL
