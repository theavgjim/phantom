#!/usr/bin/env bash
# Multiplayer compatibility test.
#
# Starts three local servers, then runs the in-game self-test client against them:
#   :25601  vanilla 1.21.1 (official server jar, no mods)        -> default rules, kick/reconnect
#   :25602  Fabric 1.21.1 + test "policy plugin" (selftest mod)  -> phantom:policy grants/denials,
#                                                                   malformed + oversized payloads
#   :25603  vanilla 1.20.4                                        -> different-version handling
# The client first runs the singleplayer checks, then joins / switches / reconnects across them.
#
# Notes
#  * Servers run in offline mode on localhost only (the dev client has no Microsoft account).
#  * Running a Minecraft server requires accepting the Minecraft EULA (https://aka.ms/MinecraftEULA).
#    The script refuses to start servers unless you pass --accept-eula, which records your acceptance
#    in each test server's eula.txt.
#  * Needs: Java 21, xvfb-run, python3, ~4 GB RAM. Report: MinecraftIntegration/run-selftest/phantom-selftest.json
#
# Usage: tools/compat-test.sh --accept-eula      (GRADLE_ARGS="..." for extra Gradle flags)
set -euo pipefail
if [[ "${1:-}" != "--accept-eula" ]]; then
  echo "Running the test servers requires accepting the Minecraft EULA: https://aka.ms/MinecraftEULA"
  echo "Read it, then run: $0 --accept-eula"
  exit 2
fi
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="${WORK:-$ROOT/build/compat}"
GRADLE_ARGS="${GRADLE_ARGS:-}"
POLICY='{"version":1,"allow":["ESP","Fly"],"deny":["Zoom"],"message":"Phantom test server rules"}'
mkdir -p "$WORK/jars"
PIDS=()
POLICY_DIR="$ROOT/MinecraftIntegration/run-selftest-server"
cleanup() {
  # Ask every server to save and stop: console "stop" for vanilla, a stop-file for the policy server.
  for d in vanilla old; do [ -f "$WORK/$d/console.txt" ] && echo stop >> "$WORK/$d/console.txt" || true; done
  [ -d "$POLICY_DIR" ] && touch "$POLICY_DIR/phantom-stop" || true
  sleep 8
  for p in "${PIDS[@]}"; do kill "$p" 2>/dev/null || true; done
}
trap cleanup EXIT

fetch_server() { # version -> path of verified server jar
  local v="$1" jar="$WORK/jars/server-$1.jar"
  python3 - "$v" "$jar" <<'PY'
import hashlib, json, os, sys, urllib.request
v, jar = sys.argv[1], sys.argv[2]
man = json.load(urllib.request.urlopen("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"))
meta = json.load(urllib.request.urlopen(next(x["url"] for x in man["versions"] if x["id"] == v)))
d = meta["downloads"]["server"]
if not (os.path.exists(jar) and hashlib.sha1(open(jar, "rb").read()).hexdigest() == d["sha1"]):
    data = urllib.request.urlopen(d["url"]).read()
    if hashlib.sha1(data).hexdigest() != d["sha1"]:
        sys.exit("SHA-1 mismatch for server " + v)
    open(jar, "wb").write(data)
print(jar)
PY
}

write_props() { # dir port motd
  mkdir -p "$1"
  cat > "$1/server.properties" <<PROPS
online-mode=false
server-ip=127.0.0.1
server-port=$2
motd=$3
level-type=minecraft\:flat
generate-structures=false
spawn-protection=0
view-distance=4
simulation-distance=4
max-players=4
enable-status=true
PROPS
  printf '# Accepted by the user via tools/compat-test.sh --accept-eula (https://aka.ms/MinecraftEULA)\neula=true\n' > "$1/eula.txt"
}

start_vanilla() { # name version port
  local dir="$WORK/$1" jar
  jar="$(fetch_server "$2")"
  write_props "$dir" "$3" "Phantom test $1 ($2)"
  : > "$dir/console.txt"
  (cd "$dir" && tail -n +1 -f console.txt | java -Xmx1G -jar "$jar" nogui > server.log 2>&1) &
  PIDS+=($!)
}

wait_ready() { # logfile name
  for _ in $(seq 1 240); do grep -q 'Done (' "$1" 2>/dev/null && { echo "  $2 ready"; return; }; sleep 1; done
  echo "  $2 did not start"; tail -20 "$1"; exit 1
}

echo "==> Starting test servers"
start_vanilla vanilla 1.21.1 25601
start_vanilla old 1.20.4 25603
rm -f "$POLICY_DIR/phantom-stop"
write_props "$POLICY_DIR" 25602 "Phantom test policy server"
(cd "$ROOT" && ORG_GRADLE_PROJECT_selftestPolicy="$POLICY" ./gradlew $GRADLE_ARGS -q :MinecraftIntegration:runSelftestServer \
  > "$WORK/policy-server.log" 2>&1) &
PIDS+=($!)
wait_ready "$WORK/vanilla/server.log" "vanilla 1.21.1 :25601"
wait_ready "$WORK/old/server.log" "vanilla 1.20.4 :25603"
wait_ready "$WORK/policy-server.log" "fabric+policy 1.21.1 :25602"

echo "==> Running the self-test client"
rm -rf "$ROOT/MinecraftIntegration/run-selftest/config" "$ROOT/MinecraftIntegration/run-selftest/screenshots" \
       "$ROOT/MinecraftIntegration/run-selftest/phantom-selftest.json"
SERVERS="-Dphantom.selftest.vanilla=127.0.0.1:25601 -Dphantom.selftest.policyServer=127.0.0.1:25602"
SERVERS="$SERVERS -Dphantom.selftest.oldServer=127.0.0.1:25603 -Dphantom.selftest.consoleFile=$WORK/vanilla/console.txt"
(cd "$ROOT" && ORG_GRADLE_PROJECT_selftestServers="$SERVERS" xvfb-run -a -s "-screen 0 1280x720x24" \
  ./gradlew $GRADLE_ARGS :MinecraftIntegration:runSelftest > "$WORK/client.log" 2>&1) || true

echo "==> Server-side view (kicks / errors / flags)"
grep -hE "PhantomTester (joined|left|lost)|Kicked|moved too quickly|moved wrongly|Exception" \
  "$WORK/vanilla/server.log" "$WORK/old/server.log" "$WORK/policy-server.log" | sed 's/^/  /' | head -40 || true

echo "==> Report"
python3 - "$ROOT/MinecraftIntegration/run-selftest/phantom-selftest.json" <<'PY'
import json, sys
r = json.load(open(sys.argv[1]))
for c in r["checks"]:
    print(f"  [{c['result']}] {c['check']} - {c['detail']}")
print(f"\n  {r['passed']} passed, {r['failed']} failed")
sys.exit(1 if r["failed"] else 0)
PY
