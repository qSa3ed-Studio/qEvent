#!/usr/bin/env bash
# Creates (or refreshes) a local Paper test server for qEvent verification.
#
# Usage: tools/testserver/setup.sh <minecraft-version> <server-port> <rcon-port>
#
# The server runs in offline mode with a flat world so it boots quickly; the plugin JAR is
# copied from target/. This harness is for development only and is never shipped.
set -euo pipefail

VERSION="${1:?usage: setup.sh <minecraft-version> <server-port> <rcon-port>}"
SERVER_PORT="${2:?usage: setup.sh <minecraft-version> <server-port> <rcon-port>}"
RCON_PORT="${3:?usage: setup.sh <minecraft-version> <server-port> <rcon-port>}"
RCON_PASSWORD="qevent-test"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SERVER_DIR="${ROOT}/.testservers/${VERSION}"
PLUGIN_VERSION="$(sed -n 's/^[[:space:]]*<version>\(.*\)<\/version>[[:space:]]*$/\1/p' "${ROOT}/pom.xml" | head -1)"
JAR_SOURCE="${ROOT}/target/qEvent-${PLUGIN_VERSION}.jar"
PAPER_JAR="${ROOT}/.testservers/jars/paper-${VERSION}.jar"

if [[ ! -f "${JAR_SOURCE}" ]]; then
  echo "Build the plugin first: mvn -B package" >&2
  exit 1
fi
if [[ ! -f "${PAPER_JAR}" ]]; then
  echo "Missing ${PAPER_JAR}. Download the Paper ${VERSION} server JAR into .testservers/jars/." >&2
  exit 1
fi

mkdir -p "${SERVER_DIR}/plugins"
cp -f "${PAPER_JAR}" "${SERVER_DIR}/paper.jar"
cp -f "${JAR_SOURCE}" "${SERVER_DIR}/plugins/qEvent-${PLUGIN_VERSION}.jar"
echo "eula=true" > "${SERVER_DIR}/eula.txt"

cat > "${SERVER_DIR}/server.properties" <<EOF
online-mode=false
server-port=${SERVER_PORT}
enable-rcon=true
rcon.port=${RCON_PORT}
rcon.password=${RCON_PASSWORD}
broadcast-rcon-to-ops=true
level-type=minecraft:flat
generator-settings=
level-name=world
spawn-protection=0
max-players=10
view-distance=4
simulation-distance=4
allow-nether=false
allow-flight=true
enable-command-block=false
sync-chunk-writes=false
motd=qEvent test server
EOF

echo "Prepared ${SERVER_DIR} (server port ${SERVER_PORT}, rcon ${RCON_PORT})"
