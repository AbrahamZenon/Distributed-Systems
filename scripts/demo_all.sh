#!/usr/bin/env bash
set -e
echo "[*] Running All 7 Mandatory Demonstration Scenarios (DS04 Java)..."
java -cp "target/classes:target/test-classes:lib/*" edu.ptit.ds04.demo.DemoAll
