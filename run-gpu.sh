#!/usr/bin/env bash
#
# Runs the GPU (fragment shader) client. macOS needs -XstartOnFirstThread for
# GLFW; on Linux/Windows drop that flag.
#
#   ./run-gpu.sh                 interactive, natural terrain, 60 ticks/s
#   ./run-gpu.sh --flat          interactive, the old flat world
#   ./run-gpu.sh --tps 120       simulation rate, independent of the frame rate
#   ./run-gpu.sh --speed 1.5     movement multiplier
#   ./run-gpu.sh --selftest 120  render offscreen, write gpu-frame.png, exit
#
set -euo pipefail
cd "$(dirname "$0")"

mvn -q -DskipTests compile dependency:build-classpath -Dmdep.outputFile=target/classpath.txt

CLASSPATH="target/classes:$(cat target/classpath.txt)"

JVM_FLAGS=(--enable-native-access=ALL-UNNAMED)
if [[ "$(uname -s)" == "Darwin" ]]; then
    JVM_FLAGS+=(-XstartOnFirstThread)
fi

exec java "${JVM_FLAGS[@]}" -cp "$CLASSPATH" com.craftclassic.client.gpu.GpuMain "$@"
