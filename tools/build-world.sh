#!/usr/bin/env bash
# Запускает сервер Fabric 1.20.1 с модом, строит карту-прохождение и сохраняет мир.
set -euo pipefail
ROOT=$(pwd)
rm -rf server && mkdir -p server/mods && cd server
curl -fsSL -o fabric-server.jar "https://meta.fabricmc.net/v2/versions/loader/1.20.1/0.15.11/1.0.1/server/jar"
curl -fsSL -o mods/fabric-api.jar "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.92.2%2B1.20.1/fabric-api-0.92.2%2B1.20.1.jar"
cp "$ROOT/build/libs/shinobi-1.0.0.jar" mods/
echo "eula=true" > eula.txt
cat > server.properties <<'PROPS'
level-name=Shinobi Adventure
level-type=minecraft\:flat
generate-structures=false
gamemode=survival
difficulty=normal
online-mode=false
spawn-protection=0
spawn-monsters=true
spawn-animals=false
view-distance=6
simulation-distance=6
PROPS
mkfifo in
exec 3<>in
java -Xmx2G -jar fabric-server.jar nogui < in > server.log 2>&1 &
PID=$!
for i in $(seq 1 240); do
  grep -q 'Done (' server.log && break
  kill -0 $PID 2>/dev/null || { cat server.log; exit 1; }
  sleep 2
done
grep -q 'Done (' server.log || { tail -80 server.log; exit 1; }
cmd() { echo "$1" >&3; sleep "${2:-2}"; }
cmd "gamerule doMobSpawning false"
cmd "gamerule spawnRadius 0"
cmd "gamerule mobGriefing false"
cmd "gamerule doInsomnia false"
cmd "gamerule doDaylightCycle false"
cmd "gamerule doWeatherCycle false"
cmd "time set 12800"
cmd "weather clear"
cmd "setworldspawn 0 -60 0"
cmd "forceload add -8 -24 464 24" 25
cmd "shinobi buildat 0 -61 0" 1
for i in $(seq 1 300); do
  grep -q 'Map built' server.log && break
  kill -0 $PID 2>/dev/null || { tail -80 server.log; exit 1; }
  sleep 2
done
grep -q 'Map built' server.log || { tail -120 server.log; exit 1; }
if grep -q 'Ошибка при строительстве\|Error building' server.log; then grep -n 'Ошибка при строительстве\|Error building' server.log; exit 1; fi
sleep 5
cmd "save-all flush" 20
cmd "stop" 1
wait $PID || true
rm -f "Shinobi Adventure/session.lock"
echo "world saved"
