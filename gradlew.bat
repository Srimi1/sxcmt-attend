@echo off
set DIR=%~dp0
if defined JAVA_HOME (set JAVA_EXEC=%JAVA_HOME%\bin\java.exe) else (set JAVA_EXEC=java.exe)
"%JAVA_EXEC%" -classpath "%DIR%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
