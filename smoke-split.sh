#!/usr/bin/env bash
# Event payouts are split between a team's online members, not paid to every member. Logs
# five mineflayer bots in (Red x3, Blue, Green), with RedOff and BlueOff on the roster but
# never online, then runs a control point capture and an unlockout. The dev save files are
# put back afterwards. Run from fabric/: bash smoke-split.sh
set -u
cd "$(dirname "$0")"
LOG=run/logs/latest.log
DIR=run/config/moneysmp
REPORT=run/logs/smoke-split.txt
FILES="data config points unlockout altar"
rm -f "$LOG"
[ -d smoke-bots/node_modules ] || npm install --prefix smoke-bots --silent
for f in $FILES; do [ -f "$DIR/$f.json" ] && mv "$DIR/$f.json" "$DIR/$f.json.smoke-bak"; done
mkdir -p "$DIR"
# one player captures a point in a single tick
echo '{"controlPointMoney": 90, "controlPointPercentPer30s": 6000}' > "$DIR/config.json"

EFFECTS="speed slowness mining_fatigue strength jump_boost regeneration resistance fire_resistance water_breathing invisibility
night_vision health_boost absorption glowing luck unluck slow_falling dolphins_grace weakness darkness"

effects() {
    for e in $EFFECTS; do echo "effect give $1 minecraft:$e infinite 0 true"; done
}

# console commands queue up whenever the server hitches and then all run in one tick, so
# anything order-sensitive waits for the log instead of sleeping
wait_for() {
    for _ in $(seq 30); do
        grep -qF -- "$1" "$LOG" && return
        sleep 1
    done
}

feed() {
    while ! grep -q "Done (" "$LOG" 2>/dev/null; do sleep 2; done
    node smoke-bots/bots.js RedA RedB RedC BlueA GreenA > run/logs/smoke-bots.log 2>&1 &
    local bots=$!
    for _ in $(seq 60); do
        [ "$(grep -c "joined the game" "$LOG")" -ge 5 ] && break
        sleep 1
    done
    # the bots' player data stays in the dev world, effects and concrete from last run included
    echo "effect clear @a"
    echo "clear @a"
    for p in RedA RedB RedC RedOff; do echo "moneysmp team set $p Red"; done
    for p in BlueA BlueOff; do echo "moneysmp team set $p Blue"; done
    echo "moneysmp team set GreenA Green"
    for p in RedA RedB RedC RedOff BlueA BlueOff GreenA; do echo "moneysmp set $p 0"; done
    sleep 2

    # $90 over the three Red players online, nothing for RedOff
    echo "tp RedA 200 -60 200"
    sleep 2
    echo "execute as RedA at RedA run moneysmp point 1"
    echo "moneysmp event control-point start"
    wait_for "All control points have been captured!"

    # Red: effects, haste II + conduit and the concrete, all first = 30 pts over 3 online.
    # Blue: haste II + conduit second = 8 pts, BlueA alone. Green: effects second = 8 pts,
    # then GreenA leaves so nobody on Green is online when it ends
    echo "moneysmp event unlockout start 1h"
    wait_for "UNLOCKOUT STARTED"
    effects RedA
    echo "effect give RedA minecraft:haste infinite 1 true"
    echo "effect give RedA minecraft:conduit_power infinite 0 true"
    echo "give RedB minecraft:red_concrete 2048"
    echo "give RedC minecraft:red_concrete 2048"
    wait_for "Red completed 20 effects"
    wait_for "Red completed Have Haste II"
    wait_for "Red completed 4,096 red concrete"
    echo "effect give BlueA minecraft:haste infinite 1 true"
    echo "effect give BlueA minecraft:conduit_power infinite 0 true"
    effects GreenA
    wait_for "Blue completed Have Haste II"
    wait_for "Green completed 20 effects"
    echo "kick GreenA"
    wait_for "GreenA left the game"
    echo "moneysmp event unlockout stop"
    echo "moneysmp transaction 1h"
    sleep 3
    echo "stop"
    # with players having been on, the dev server can sit in "Saving worlds" forever (vanilla
    # doesn't). data.json is already written by then, so just kill it
    wait_for "All dimensions are saved"
    kill $bots 2>/dev/null
    grep -q "All dimensions are saved" "$LOG" || powershell -NoProfile -Command \
        "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object CommandLine -match 'devlaunchinjector' | ForEach-Object { Stop-Process -Id \$_.ProcessId -Force }"
}

feed | ./gradlew.bat runServer --offline --console=plain > /tmp/smoke-split.log 2>&1

{
check() {
    if grep -qF -- "$1" "$LOG"; then echo "PASS  $2"; else echo "FAIL  $2"; fi
}
check "Red captured control point #1!" "Red captured the point"
check "+\$90 (\$30 each, 3 online)" "capture split three ways"
check "split between" "unlockout start explains the split"
check "Red  30 pts  (3/25 goals)  |  +\$30 (\$10 each, 3 online)" "Red unlockout score split three ways"
check "Blue  8 pts  (1/25 goals)  |  +\$8 (\$8 each, 1 online)" "Blue paid to its one online player"
check "Green  8 pts  (1/25 goals)  |  nobody online, unpaid" "Green had nobody online"
if grep -qE "\[Server thread/ERROR\]|Exception" "$LOG"; then echo "FAIL  errors in log"; grep -E "ERROR|Exception" "$LOG" | head; else echo "PASS  no errors"; fi
grep -q "All dimensions are saved" "$LOG" || echo "WARN  server stalled in Saving worlds on stop and was killed"

python - "$DIR/data.json" <<'EOF'
import json, sys
players = {p["name"]: p for p in json.load(open(sys.argv[1]))["players"].values()}
want = {"RedA": 40, "RedB": 40, "RedC": 40, "RedOff": 0, "BlueA": 8, "BlueOff": 0, "GreenA": 0}
bad = 0
for name, money in want.items():
    got = players[name]["money"]
    ok = abs(got - money) < 1e-6
    bad |= not ok
    print(("PASS" if ok else "FAIL") + f"  {name} saved at {got:g}, expected {money}")
sys.exit(bad)
EOF
} | tee "$REPORT"

for f in $FILES; do
    rm -f "$DIR/$f.json"
    [ -f "$DIR/$f.json.smoke-bak" ] && mv "$DIR/$f.json.smoke-bak" "$DIR/$f.json"
done
grep -q FAIL "$REPORT" && exit 1 || exit 0
