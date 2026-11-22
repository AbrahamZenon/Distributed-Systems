#!/usr/bin/env bash
set -e
echo "[*] Starting DS04 Java Collaboration Server on ws://0.0.0.0:8765..."
java -cp "target/classes:lib/*" edu.ptit.ds04.server.CollaborationServer
