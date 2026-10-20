#!/usr/bin/env bash
#
# Runs the ClassicCraft server (20 TPS, port 8256).
# Clients join with: ./run-gpu.sh --server 127.0.0.1 --name <name>
#
set -euo pipefail
cd "$(dirname "$0")"

mvn -q -DskipTests compile dependency:build-classpath -Dmdep.outputFile=target/classpath.txt

exec java -cp "target/classes:$(cat target/classpath.txt)" com.craftclassic.server.Main
