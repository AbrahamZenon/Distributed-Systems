@echo off
setlocal
cd /d "%~dp0\.."
if not defined JAVA_HOME (
    if exist "C:\Users\kn200\.jdks\openjdk-25.0.1" (
        set "JAVA_HOME=C:\Users\kn200\.jdks\openjdk-25.0.1"
    )
)
echo [*] Executing DS04 JUnit 5 Test Suite...
call mvnw.cmd test
