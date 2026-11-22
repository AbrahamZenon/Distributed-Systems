@echo off
setlocal
if not defined JAVA_HOME (
    if exist "C:\Users\kn200\.jdks\openjdk-25.0.1" (
        set "JAVA_HOME=C:\Users\kn200\.jdks\openjdk-25.0.1"
    )
)
echo [*] Running All 7 Mandatory Demonstration Scenarios (DS04 Java)...
java -cp "target/classes;target/test-classes;lib/*" edu.ptit.ds04.demo.DemoAll
