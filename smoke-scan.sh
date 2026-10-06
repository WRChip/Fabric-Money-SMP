#!/usr/bin/env bash
# End to end test for /moneysmp legendary find. Four bots join: Alice wears the emerald
# chestplate, Bob has it in his ender chest, Carol in a shulker box, Dave only a plain netherite
# one. Bob and Carol are kicked so they're offline, then the command has to name Alice, Bob and
# Carol (and say which are offline) and leave Dave out. The dev save files and playerdata are put
# back afterwards and the result lands in run/logs/smoke-scan.txt. Run from fabric/: bash smoke-scan.sh
set -u
cd "$(dirname "$0")"
LOG=run/logs/latest.log
BOTLOG=run/logs/smoke-scan-bots.log
DIR=run/config/moneysmp
PD=run/world/playerdata
REPORT=run/logs/smoke-scan.txt
rm -f "$LOG"
[ -d smoke-bots/node_modules ] || npm install --prefix smoke-bots --silent
[ -f "$DIR/data.json" ] && mv "$DIR/data.json" "$DIR/data.json.smoke-bak"
rm -rf "$PD.smoke-bak"
cp -r "$PD" "$PD.smoke-bak"
mkdir -p "$DIR"

CHEST='minecraft:netherite_chestplate[minecraft:custom_data={moneysmp_legend:"emerald_chestplate"}]'
INNER='{id:"minecraft:netherite_chestplate",count:1,components:{"minecraft:custom_data":{moneysmp_legend:"emerald_chestplate"}}}'

wait_for() {
    for _ in $(seq 40); do
        grep -qF -- "$1" "$LOG" "$BOTLOG" 2>/dev/null && return
        sleep 1
    done
}

feed() {
    while ! grep -q "Done (" "$LOG" 2>/dev/null; do sleep 2; done
    node smoke-bots/legend.js Alice Bob Carol Dave > "$BOTLOG" 2>&1 &
    local bots=$!
    for _ in $(seq 60); do
        [ "$(grep -c "joined the game" "$LOG")" -ge 4 ] && break
        sleep 1
    done
    sleep 3
    # the dev world's border (600) ends at 300 and the bots spawn past it
    echo "worldborder set 2000"
    echo "difficulty peaceful"
    echo "tp @a 0 -60 0"
    sleep 4
    echo "clear @a"
    echo "item replace entity Alice armor.chest with $CHEST"
    echo "item replace entity Bob enderchest.5 with $CHEST"
    echo "give Carol minecraft:shulker_box[minecraft:container=[{slot:3,item:$INNER}]]"
    echo "give Dave minecraft:netherite_chestplate"
    sleep 2
    echo "kick Bob gone"
    echo "kick Carol gone"
    sleep 3
    echo "moneysmp legendary find"
    sleep 2
    echo "moneysmp legendary find windweaver"
    sleep 2
    echo "moneysmp legendary find nonsense"
    sleep 1
    echo "clear @a"
    echo "worldborder set 600"
    echo "difficulty easy"
    echo "stop"
    wait_for "All dimensions are saved"
    kill $bots 2>/dev/null
    grep -q "All dimensions are saved" "$LOG" || powershell -NoProfile -Command \
        "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object CommandLine -match 'devlaunchinjector' | ForEach-Object { Stop-Process -Id \$_.ProcessId -Force }"
}

feed | ./gradlew.bat runServer --offline --console=plain > /tmp/smoke-scan-gradle.log 2>&1

{
check() {
    if grep -qF -- "$1" "$LOG"; then echo "PASS  $2"; else echo "FAIL  $2"; fi
}
check "Alice » worn (online)" "worn chestplate found on an online player"
check "Bob » ender chest (offline)" "ender chest found on an offline player"
check "Carol » inventory, inside a container (offline)" "chestplate inside a shulker found on an offline player"
if grep -qF "Dave »" "$LOG"; then echo "FAIL  plain netherite chestplate left out"; else echo "PASS  plain netherite chestplate left out"; fi
check "Nobody has the Windweaver" "an item nobody holds says so"
check "No legendary called nonsense" "unknown item refused"
if grep -qiE "mixin.*(error|fail)|Mixin apply failed|\[Server thread/ERROR\]|Exception" "$LOG"; then
    echo "FAIL  errors in log:"; grep -iE "mixin.*(error|fail)|Mixin apply failed|ERROR|Exception" "$LOG" | head
else
    echo "PASS  no mixin or server errors"
fi
} | tee "$REPORT"

rm -f "$DIR/data.json"
[ -f "$DIR/data.json.smoke-bak" ] && mv "$DIR/data.json.smoke-bak" "$DIR/data.json"
rm -rf "$PD"
mv "$PD.smoke-bak" "$PD"
grep -q FAIL "$REPORT" && exit 1 || exit 0
