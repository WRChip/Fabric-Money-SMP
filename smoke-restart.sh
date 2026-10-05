#!/usr/bin/env bash
# Does the altar event survive a restart? Boots the dev server, starts the event, stops the
# server, boots it again and checks everything came back. Run from fabric/: bash smoke-restart.sh
set -u
cd "$(dirname "$0")"
LOG=run/logs/latest.log
JSON=run/config/moneysmp/altar.json
rm -f "$LOG" "$JSON"

wait_done() {
    while ! grep -q "Done (" "$LOG" 2>/dev/null; do sleep 2; done
}

first() {
    wait_done
    echo "worldborder set 600"
    sleep 2
    echo "moneysmp event altar start"
    sleep 6
    echo "altar list shards"
    sleep 2
    echo "stop"
}

second() {
    wait_done
    sleep 6
    local y
    y=$(grep -A3 '"altar"' "$JSON" | grep '"y"' | tr -dc 0-9-)
    echo "altar list shards"
    echo "forceload query 0 0"
    echo "execute if block 0 $y 0 minecraft:barrier run say SMOKE barrier survived"
    echo "execute if entity @e[type=item_display,tag=moneysmp_altar_mace] run say SMOKE mace display survived"
    echo "execute if entity @e[type=text_display,tag=moneysmp_altar] run say SMOKE text survived"
    sleep 2
    echo "moneysmp event altar stop"
    sleep 3
    echo "stop"
}

first | ./gradlew.bat runServer --offline --console=plain > /tmp/smoke-restart-1.log 2>&1
cp "$LOG" /tmp/smoke-restart-run1.log
grep -q '"event"' "$JSON" && echo "PASS  altar.json holds the event after the first shutdown" || echo "FAIL  altar.json lost the event"
rm -f "$LOG"
second | ./gradlew.bat runServer --offline --console=plain > /tmp/smoke-restart-2.log 2>&1

fail=0
check() {
    if grep -q "$1" "$LOG"; then echo "PASS  $2"; else echo "FAIL  $2"; fail=1; fi
}
check "resuming altar event: 0 of 8 fragments claimed" "event resumed on boot"
check "is marked for force loading" "altar chunks still forced"
check "SMOKE barrier survived" "barrier still there"
check "SMOKE mace display survived" "mace display still there"
check "SMOKE text survived" "recipe text still there"
before=$(grep "still at its ring" /tmp/smoke-restart-run1.log | sed 's/^.*(Minecraft) //')
after=$(grep "still at its ring" "$LOG" | sed 's/^.*(Minecraft) //')
if [ -n "$before" ] && [ "$before" = "$after" ]; then echo "PASS  same eight spots before and after"; else echo "FAIL  spots differ"; fail=1; fi
check "altar event was stopped" "event stops cleanly after the restart"
if grep -qiE "mixin.*(error|fail)|Mixin apply failed|\[Server thread/ERROR\]" "$LOG"; then echo "FAIL  errors in log"; grep -iE "\[Server thread/ERROR\]" "$LOG" | head; fail=1; else echo "PASS  no errors after the restart"; fi
echo; echo "$after"
exit $fail
