#!/usr/bin/env bash
# Headless smoke test for the altar event. Boots the dev server (run/) with the freshly
# built jar, drives it over stdin and checks the log. Run from fabric/: bash smoke-altar.sh
set -u
cd "$(dirname "$0")"
LOG=run/logs/latest.log
JSON=run/config/moneysmp/altar.json
rm -f "$LOG" "$JSON"

feed() {
    while ! grep -q "Done (" "$LOG" 2>/dev/null; do sleep 2; done
    echo "worldborder set 600"
    echo "moneysmp team remove SmokeTester"
    echo "moneysmp timer skip SmokeTester"
    sleep 2
    echo "moneysmp event altar start"
    sleep 8
    # nobody is online, so everything below at 0,0 only works if the mod force-loaded the chunk
    echo "forceload query 0 0"
    echo "altar list shards"
    # leftovers from earlier runs in every chunk this test touches, or they skew the checks
    echo "forceload add 222 92"
    echo "forceload add 92 222"
    echo "kill @e[type=item]"
    local y round
    y=$(grep -A3 '"altar"' "$JSON" | grep '"y"' | tr -dc 0-9-)
    round=$(grep '"round"' "$JSON" | tr -dc 0-9)
    echo "say SMOKE altar base y=$y round=$round"
    echo "execute if block 0 $y 0 minecraft:barrier run say SMOKE altar built"
    echo "execute if entity @e[type=text_display,tag=moneysmp_altar] run say SMOKE recipe text up"
    echo "execute if entity @e[type=item_display,tag=moneysmp_altar_mace] run say SMOKE mace display up"
    echo "execute if entity @e[type=item_display,tag=moneysmp_altar,tag=!moneysmp_altar_mace] run say SMOKE pedestal display up"
    echo "data get entity @e[type=item_display,tag=moneysmp_altar,tag=!moneysmp_altar_mace,limit=1] item"
    echo "data get entity @e[type=text_display,tag=moneysmp_altar,limit=1] text"
    echo "summon minecraft:item 0 $((y + 6)) 0 {Item:{id:\"minecraft:echo_shard\",count:1,components:{\"minecraft:custom_data\":{moneysmp_round:${round}L,moneysmp_fragment:0}}}}"
    sleep 3
    echo "execute if entity @e[type=item,nbt={Glowing:1b,Invulnerable:1b,Age:-32768s}] run say SMOKE fragment protected"
    # kill the fragment: a fresh one should appear at Ember's ring (222, 92 with a 600 border)
    echo "kill @e[type=item,nbt={Item:{id:\"minecraft:echo_shard\"}}]"
    sleep 2
    echo "execute if entity @e[type=item,x=221,y=-64,z=91,dx=2,dy=400,dz=2,nbt={Item:{id:\"minecraft:echo_shard\"},Glowing:1b}] run say SMOKE fragment back at its spot"
    echo "moneysmp event altar respawn frost"
    echo "moneysmp event altar respawn nope"
    # a fragment stashed in a chest shows up with the chest's coordinates
    echo "setblock 3 $((y + 1)) 3 minecraft:chest{Items:[{Slot:0b,id:\"minecraft:echo_shard\",count:1,components:{\"minecraft:custom_data\":{moneysmp_round:${round}L,moneysmp_fragment:4,moneysmp_gen:0}}}]}"
    sleep 6
    echo "altar list shards"
    echo "setblock 3 $((y + 1)) 3 minecraft:air"
    echo "moneysmp event altar start"
    sleep 2
    # the mod lets go of the chunk on stop; hold it ourselves so the checks after can still see it
    echo "forceload add -16 -16 16 16"
    echo "moneysmp event altar stop"
    sleep 3
    echo "execute if block 0 $y 0 minecraft:air run say SMOKE altar removed"
    echo "execute unless entity @e[type=item,nbt={Glowing:1b}] run say SMOKE fragment unglowed"
    echo "execute unless entity @e[tag=moneysmp_altar] run say SMOKE displays removed"
    echo "data get entity @e[type=item,nbt={Glowing:1b},limit=1]"
    echo "kill @e[type=item]"
    echo "forceload remove all"
    sleep 2
    echo "stop"
}

feed | ./gradlew.bat runServer --offline --console=plain > /tmp/smoke-altar-gradle.log 2>&1

fail=0
check() {
    if grep -q "$1" "$LOG"; then echo "PASS  $2"; else echo "FAIL  $2"; fail=1; fi
}
check "ALTAR EVENT STARTED" "event start broadcast"
check "SmokeTester isn't on a team" "team remove reaches the handler"
check "SmokeTester isn't carrying a fragment" "timer skip reaches the handler"
check "still at its ring" "altar list shows the fragments"
check "is marked for force loading" "altar chunk force-loaded by the mod"
check "SMOKE altar built" "barrier placed at 0,0 surface"
check "SMOKE recipe text up" "recipe text display spawned"
check "SMOKE mace display up" "mace item display spawned"
check "SMOKE pedestal display up" "pedestal item display spawned"
check 'moneysmp:pedestal' "pedestal display wears the pack model"
check "Fragments of the World" "recipe text names the fragments"
check "SMOKE fragment protected" "summoned fragment is glowing, invulnerable, never despawns"
check "was destroyed and is back at its spot" "destroyed fragment announced"
check "SMOKE fragment back at its spot" "destroyed fragment respawned at its ring"
check "put the Frost fragment back at its spot" "admin respawn works"
check "Tide » in a chest at 3, " "fragment in a chest is listed with the chest's coordinates"
check "Frost » on the ground at 92, " "fragment on the ground is listed with its coordinates"
check "No such fragment" "admin respawn rejects a bad name"
check "already running" "second start refused"
check "altar event was stopped" "event stop broadcast"
check "SMOKE altar removed" "altar torn down on stop"
check "SMOKE fragment unglowed" "leftover fragment stops glowing on stop"
check "SMOKE displays removed" "displays removed on stop"
if grep -qiE "mixin.*(error|fail)|Mixin apply failed|\[Server thread/ERROR\]" "$LOG"; then echo "FAIL  errors in log:"; grep -iE "mixin.*(error|fail)|Mixin apply failed|\[Server thread/ERROR\]" "$LOG" | head; fail=1; else echo "PASS  no mixin or server errors"; fi
if grep -q '"event"' "$JSON" 2>/dev/null; then echo "FAIL  altar.json still holds an event"; fail=1; else echo "PASS  altar.json cleared after stop"; fi
echo; grep -E "MoneySMP|SMOKE|altar|force load" "$LOG" | sed 's/^.*\[Server thread\/INFO\] //' | cut -c1-200 | head -40
exit $fail
