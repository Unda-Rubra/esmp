#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERVER="$ROOT/.run/dev"
PAPER_SHA="0de30efb024bc8b83c9c7d507d11802897ad8056b6110ec09fe1a91d126ccb54"
PAPER_URL="https://fill-data.papermc.io/v1/objects/$PAPER_SHA/paper-26.2-121.jar"

if [[ "${ESMP_ACCEPT_EULA:-}" != "true" ]]; then
    printf '%s\n' 'Read https://aka.ms/MinecraftEULA, then set ESMP_ACCEPT_EULA=true to run this local development server.' >&2
    exit 1
fi

"$ROOT/gradlew" -p "$ROOT" :esmp-plugin:build --no-daemon
mkdir -p "$SERVER/plugins"

checksum() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | cut -d ' ' -f 1
    else
        shasum -a 256 "$1" | cut -d ' ' -f 1
    fi
}

if [[ ! -f "$SERVER/paper.jar" ]]; then
    curl --fail --location --retry 2 "$PAPER_URL" --output "$SERVER/paper.jar.download"
    if [[ "$(checksum "$SERVER/paper.jar.download")" != "$PAPER_SHA" ]]; then
        printf '%s\n' 'Paper checksum mismatch; refusing to launch.' >&2
        exit 1
    fi
    mv "$SERVER/paper.jar.download" "$SERVER/paper.jar"
fi
if [[ "$(checksum "$SERVER/paper.jar")" != "$PAPER_SHA" ]]; then
    printf '%s\n' 'Existing Paper jar does not match pinned build 121; refusing to launch.' >&2
    exit 1
fi

cp "$ROOT/esmp-plugin/build/libs/esmp-0.1.0-SNAPSHOT.jar" "$SERVER/plugins/esmp.jar"
printf '%s\n' 'eula=true' > "$SERVER/eula.txt"
if [[ ! -f "$SERVER/server.properties" ]]; then
    cat > "$SERVER/server.properties" <<'PROPERTIES'
server-ip=127.0.0.1
server-port=25579
online-mode=true
max-players=4
view-distance=2
simulation-distance=2
level-name=world
level-type=minecraft:flat
generator-settings={"biome":"minecraft:plains","layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}]}
generate-structures=false
pause-when-empty-seconds=-1
enable-query=false
enable-rcon=false
motd=ESMP local development
PROPERTIES
fi

JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
cd "$SERVER"
exec "$JAVA" --enable-native-access=ALL-UNNAMED -Xms512M -Xmx1536M -jar paper.jar --nogui
