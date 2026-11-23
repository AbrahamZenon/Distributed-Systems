@echo off
setlocal
cd /d "%~dp0\.."

echo [*] Executing DS04 Performance Benchmark Suite (Author: Nguyen Duy Phuc - B23DCVT341)...
call mvnw.cmd compile exec:java -Dexec.mainClass="edu.ptit.ds04.benchmark.BenchmarkRunner" -Dexec.args="%*"
