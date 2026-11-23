#!/usr/bin/env bash
set -e
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )/.." >/dev/null 2>&1 && pwd )"
cd "$DIR"

echo "[*] Running DS04 Fault Injection & Recovery Runner (Author: Nguyen Duy Phuc - B23DCVT341)..."
export JAVA_HOME=${JAVA_HOME:-/Library/Java/JavaVirtualMachines/jdk-24.jdk/Contents/Home}
export PATH="$JAVA_HOME/bin:$PATH"

java -cp "target/classes:lib/*:~/.m2/repository/org/json/json/20240303/json-20240303.jar:~/.m2/repository/org/java-websocket/Java-WebSocket/1.5.7/Java-WebSocket-1.5.7.jar:~/.m2/repository/org/slf4j/slf4j-api/2.0.12/slf4j-api-2.0.12.jar:~/.m2/repository/org/slf4j/slf4j-simple/2.0.12/slf4j-simple-2.0.12.jar" edu.ptit.ds04.demo.FaultInjectionRunner "$@"
