@echo off
setlocal
cd /d "%~dp0\.."

echo [*] Running DS04 Fault Injection ^& Recovery Runner (Author: Nguyen Duy Phuc - B23DCVT341)...
call mvnw.cmd compile exec:java -Dexec.mainClass="edu.ptit.ds04.demo.FaultInjectionRunner" -Dexec.args="%*"
