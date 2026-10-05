#!/usr/bin/env bash
# End to end test for the Altar SMP legendaries. Logs two mineflayer bots in (Alice, Bob),
# drives them from the console with "say DO <bot> <action>" (smoke-bots/legend.js) and checks
# what the server and the bots saw. The dev save files are put back afterwards and the result
# lands in run/logs/smoke-legend.txt. Run from fabric/: bash smoke-legend.sh
#
# What it guards against:
#  - an altar that crafts without the money, takes the money without crafting, or takes
#    part of the items before finding something missing
#  - an altar that survives being used (they are single use)
#  - the crafting-table recipes for the parts not loading
#  - a legendary part being usable as its base item (a Warden's Heart smelting into a brick)
#  - each ability not firing from its key (right click, crouch + right click, swap hands)
#  - a cooldown that doesn't hold
#  - the swap-hands abilities moving the sword out of the main hand
#  - Crimson Bite's max health loss not wearing off (the original made it permanent)
#  - Vulcan's Crossbow not firing three arrows, or Vulcan's Wrath not leaving a crater
#  - ability damage (Holy Lance here) being softened by armour, as the originals' was
#  - the Wrath's crater sealing a player into magma
#  - the Wrath's crater staying for good, or burying whoever is in it when it fills back in
#  - Vulcan's arrows or Wrath being softened by armour, enchantments or Resistance (3 and 5
#    true hearts), or a volley of three stacking past 3 hearts
#  - aim that's too strict: a Vulcan arrow 0.75 off missing, a Holy Lance aimed at a player
#    landing on the ground behind them, a Scorching Blade missing the one just hit
#  - the wand not keeping what it killed, the disguise being visible to its wearer, the
#    wearer still visible to others, or blows on the disguise not reaching the wearer
#  - a Crazy Slots transformation that can be dropped as the weapon it turned into
#  - species not passing on a vampire's kill, PvP deaths not dropping a head in the victim's
#    team colour, or an altar taking a team's own heads
#  - the contagion ritual not converting everyone, or not breaking when hit enough
#  - the arc 3 abilities: Shadow Daggers, Shadow Leap hiding and surfacing, Wind Leap,
#    Windweaver Gust, the emerald set's worn effects, Emerald Vision, lightning immunity and
#    the ring, the leggings' fall immunity and slam, and the pickaxe's 3x3 and toggles
#  - mixins failing to apply
set -u
cd "$(dirname "$0")"
LOG=run/logs/latest.log
BOTLOG=run/logs/smoke-legend-bots.log
DIR=run/config/moneysmp
REPORT=run/logs/smoke-legend.txt
FILES="data config points unlockout altar legends"
rm -f "$LOG"
[ -d smoke-bots/node_modules ] || npm install --prefix smoke-bots --silent
for f in $FILES; do [ -f "$DIR/$f.json" ] && mv "$DIR/$f.json" "$DIR/$f.json.smoke-bak"; done
mkdir -p "$DIR"
# a ten second ritual that breaks in three hits, and Bloodlust that always draws blood
echo '{"legendary": {"contagionSeconds": 10, "contagionIntegrity": 3, "bleedChance": 1.0}}' > "$DIR/config.json"

wait_for() {
    for _ in $(seq 40); do
        grep -qF -- "$1" "$LOG" "$BOTLOG" 2>/dev/null && return
        sleep 1
    done
}

do_() {
    echo "say DO $*"
    sleep "${WAIT:-2}"
}

face() {
    echo "tp Alice 300 -60 300 facing 300 -59 306"
    echo "tp Bob 300 -60 306 facing 300 -59 300"
    sleep 1
}

fresh() {
    echo "clear Alice"
    echo "moneysmp legendary give Alice $1"
    sleep 1
}

feed() {
    while ! grep -q "Done (" "$LOG" 2>/dev/null; do sleep 2; done
    echo "forceload add 280 280 330 330"
    node smoke-bots/legend.js Alice Bob > "$BOTLOG" 2>&1 &
    local bots=$!
    for _ in $(seq 60); do
        [ "$(grep -c "joined the game" "$LOG")" -ge 2 ] && break
        sleep 1
    done
    sleep 3
    # the dev world's border (600, from smoke-altar) ends at 300, and superflat slimes kill
    # bots; both go back at the end. bring the bots over early so their chunks are in
    echo "worldborder set 2000"
    echo "difficulty peaceful"
    echo "spawnpoint Alice 300 -60 300"
    echo "spawnpoint Bob 300 -60 300"
    echo "tp Alice 300 -60 300"
    echo "tp Bob 300 -60 306"
    sleep 6
    echo "scoreboard objectives add smoke dummy"
    echo "kill @e[type=!player]"
    echo "fill 280 -61 280 330 -61 330 minecraft:grass_block"
    echo "fill 280 -60 280 330 -55 330 minecraft:air"
    echo "effect clear @a"
    echo "clear @a"
    echo "gamemode survival @a"
    echo "moneysmp legendary species Alice human"
    echo "moneysmp legendary species Bob human"
    echo "time set day"
    sleep 2

    # ── an altar takes items and money, all or nothing, once ──
    echo "tp Alice 310 -60 300"
    sleep 1
    echo "execute as Alice at Alice run moneysmp legendary altar hyperion_shard"
    sleep 2
    echo "execute if block 310 -60 300 minecraft:barrier run say SMOKE altar placed"
    echo "execute positioned 310 -60 300 if entity @e[type=text_display,tag=moneysmp_legend_altar,distance=..5] run say SMOKE altar recipe shown"
    echo "moneysmp set Alice 10"
    echo "give Alice minecraft:iron_block 4"
    echo "give Alice minecraft:stick 1"
    echo "give Alice minecraft:blaze_powder 1"
    sleep 1
    do_ Alice click 310 -60 300
    echo "execute if items entity Alice container.* minecraft:iron_block run say SMOKE items kept when short of money"
    echo "give Alice minecraft:iron_block 16"
    echo "moneysmp set Alice 100"
    sleep 1
    do_ Alice click 310 -60 300
    echo "execute if items entity Alice container.* minecraft:amethyst_shard[minecraft:custom_data~{moneysmp_legend:\"hyperion_shard\"}] run say SMOKE shard crafted"
    echo "execute unless items entity Alice container.* minecraft:iron_block run say SMOKE iron taken"
    echo "execute if block 310 -60 300 minecraft:air run say SMOKE altar used up"
    echo "moneysmp balance Alice"
    echo "recipe take Alice *"
    echo "recipe give Alice moneysmp:weapon_handle"
    echo "recipe give Alice moneysmp:vulkan_skull"
    echo "recipe give Alice moneysmp:illusion_core"

    # ── a part is only good at an altar ──
    echo "setblock 305 -50 310 minecraft:furnace"
    echo "setblock 307 -50 310 minecraft:furnace"
    echo "item replace block 305 -50 310 container.0 with minecraft:clay_ball[minecraft:custom_data={moneysmp_legend:\"wardens_heart\"}]"
    echo "item replace block 305 -50 310 container.1 with minecraft:coal"
    echo "item replace block 307 -50 310 container.0 with minecraft:clay_ball"
    echo "item replace block 307 -50 310 container.1 with minecraft:coal"

    # ── bone blade ──
    face
    fresh bone_blade
    do_ Alice use
    echo "execute if entity @a[name=Alice,nbt={active_effects:[{id:\"minecraft:speed\",amplifier:2b}]}] run say SMOKE skeletal leap"
    do_ Alice use
    face
    do_ Alice face Bob
    WAIT=3 do_ Alice sneakuse
    echo "attribute Bob minecraft:movement_speed modifier value get moneysmp:stun"
    sleep 5

    # ── frost scythe ──
    face
    fresh frost_scythe
    do_ Alice face Bob
    WAIT=3 do_ Alice use
    echo "execute if entity @a[name=Bob,nbt={active_effects:[{id:\"minecraft:slowness\",amplifier:2b}]}] run say SMOKE scythe slowed"

    # ── nightpiercer: crimson bite, and the sword stays put ──
    face
    fresh nightpiercer
    echo "tp Bob 300 -60 304"
    sleep 1
    do_ Alice face Bob
    do_ Alice swap
    echo "attribute Bob minecraft:max_health get"
    echo "execute if items entity Alice weapon.mainhand *[minecraft:custom_data~{moneysmp_legend:\"nightpiercer\"}] run say SMOKE swap kept the sword"
    sleep 11
    echo "attribute Bob minecraft:max_health get"

    # ── hyperion: holy lance, true damage through full protection netherite ──
    echo "gamerule natural_health_regeneration false"
    for slot in head:helmet chest:chestplate legs:leggings feet:boots; do
        echo "item replace entity Bob armor.${slot%%:*} with minecraft:netherite_${slot##*:}[minecraft:enchantments={protection:4}]"
    done
    echo "effect give Bob minecraft:resistance 30 3 true"
    echo "effect give Bob minecraft:fire_resistance 30 0 true"
    echo "effect give Bob minecraft:instant_health 1 5"
    face
    fresh hyperion
    # aimed at bob himself, not the ground: the look goes on to land far behind him
    do_ Alice face Bob
    WAIT=3 do_ Alice swap
    echo "execute store result score #lancehp smoke run data get entity Bob Health 10"
    echo "scoreboard players get #lancehp smoke"
    # resistance V shrugs off the sword, so only the arc's 3 true damage shows, landing on
    # the one just hit at sword's reach
    echo "effect give Bob minecraft:resistance 30 4 true"
    echo "effect give Bob minecraft:instant_health 1 5"
    echo "tp Bob 300 -60 302 facing 300 -59 300"
    sleep 1
    do_ Alice sneakswap
    do_ Alice face Bob
    do_ Alice attack Bob
    echo "execute store result score #bladehp smoke run data get entity Bob Health 10"
    echo "scoreboard players get #bladehp smoke"
    echo "clear Bob"
    echo "effect clear Bob"
    echo "gamerule natural_health_regeneration true"
    echo "execute if items entity Alice weapon.mainhand *[minecraft:custom_data~{moneysmp_legend:\"hyperion\"}] run say SMOKE hyperion stayed"

    # ── bloodlust ──
    echo "effect give Bob minecraft:instant_health 1 5"
    face
    fresh bloodlust
    do_ Alice face Bob
    echo "tp Bob 300 -60 302"
    sleep 1
    do_ Alice face Bob
    do_ Alice attack Bob
    wait_for "Bob is bleeding."
    sleep 3

    # ── vulcan's crossbow: scattershot, then the wrath ──
    echo "effect give Bob minecraft:instant_health 1 5"
    face
    fresh vulcans_crossbow
    echo "give Alice minecraft:arrow 64"
    do_ Alice look 300.5 -61 288.5
    WAIT=3 do_ Alice charge
    do_ Alice look 300.5 -61 288.5
    do_ Alice use
    echo "execute store result score #arrows smoke if entity @e[type=arrow,tag=moneysmp_vulcan]"
    echo "scoreboard players get #arrows smoke"
    echo "kill @e[type=arrow]"
    do_ Alice sneakuse
    sleep 1
    WAIT=3 do_ Alice charge
    do_ Alice look 300.5 -61 288.5
    WAIT=4 do_ Alice use
    echo "execute store result score #crater smoke run fill 290 -63 278 310 -59 298 minecraft:stone replace minecraft:magma_block"
    echo "scoreboard players get #crater smoke"
    echo "fill 280 -63 280 330 -59 330 minecraft:dirt replace minecraft:stone"
    echo "fill 280 -63 280 330 -59 330 minecraft:dirt replace minecraft:lava"
    echo "fill 280 -61 280 330 -61 330 minecraft:grass_block"
    echo "fill 280 -60 280 330 -55 330 minecraft:air"

    # ── vulcan's crossbow goes through armour: 3 hearts an arrow volley, 5 for the wrath ──
    echo "gamerule natural_health_regeneration false"
    face
    fresh vulcans_crossbow
    echo "give Alice minecraft:arrow 64"
    for slot in head chest legs feet; do
        piece=$(case $slot in head) echo helmet;; chest) echo chestplate;; legs) echo leggings;; feet) echo boots;; esac)
        echo "item replace entity Bob armor.$slot with minecraft:netherite_$piece[minecraft:enchantments={protection:4,blast_protection:4,projectile_protection:4}]"
    done
    echo "effect give Bob minecraft:resistance 120 3 true"
    echo "effect give Bob minecraft:fire_resistance 120 0 true"
    echo "effect give Bob minecraft:instant_health 1 5"
    sleep 1
    do_ Alice face Bob
    WAIT=3 do_ Alice charge
    do_ Alice face Bob
    WAIT=3 do_ Alice use
    echo "execute store result score #arrowhp smoke run data get entity Bob Health 10"
    echo "scoreboard players get #arrowhp smoke"
    echo "effect give Bob minecraft:instant_health 1 5"
    echo "kill @e[type=arrow]"
    # a near miss lands: 0.75 off at 18 blocks. 3 off still misses, side arrows and all
    echo "tp Bob 300 -60 318 facing 300 -59 300"
    sleep 1
    WAIT=3 do_ Alice charge
    do_ Alice look 301.25 -58.4 318.5
    WAIT=3 do_ Alice use
    echo "execute store result score #nearhp smoke run data get entity Bob Health 10"
    echo "scoreboard players get #nearhp smoke"
    echo "kill @e[type=arrow]"
    echo "effect give Bob minecraft:instant_health 1 5"
    sleep 1
    WAIT=3 do_ Alice charge
    do_ Alice look 303.5 -58.4 318.5
    WAIT=3 do_ Alice use
    echo "execute store result score #widehp smoke run data get entity Bob Health 10"
    echo "scoreboard players get #widehp smoke"
    echo "kill @e[type=arrow]"
    echo "tp Bob 300 -60 306 facing 300 -59 300"
    echo "effect give Bob minecraft:instant_health 1 5"
    # the wrath above is still cooling down
    sleep 15
    do_ Alice sneakuse
    sleep 1
    WAIT=3 do_ Alice charge
    do_ Alice face Bob
    # bob takes 4 true damage the moment it's fired, so the wrath lands inside that hit's
    # invulnerability, as it does mid-fight. it should still take its full 5 hearts on top
    echo "op Alice"
    WAIT=4 do_ Alice cmduse damage Bob 4 moneysmp:bleed
    echo "deop Alice"
    echo "execute store result score #wrathhp smoke run data get entity Bob Health 10"
    echo "scoreboard players get #wrathhp smoke"
    # 30 seconds on the crater fills back in, and bob, standing in it, is lifted onto the grass
    sleep 31
    echo "execute store result score #craterleft smoke run fill 290 -64 296 310 -57 316 minecraft:stone replace minecraft:magma_block"
    echo "scoreboard players get #craterleft smoke"
    echo "execute if block 300 -61 306 minecraft:grass_block run say SMOKE crater ground back"
    echo "execute if entity @a[name=Bob,x=299,y=-60,z=305,dx=2,dy=0.5,dz=2] run say SMOKE bob lifted out"
    # the sources are gone, but lava that ran out over the grass takes a few seconds to drain
    sleep 10
    echo "execute store result score #lavaleft smoke run fill 290 -64 296 310 -57 316 minecraft:stone replace minecraft:lava"
    echo "scoreboard players get #lavaleft smoke"
    echo "effect clear Bob"
    echo "clear Bob"
    echo "gamerule natural_health_regeneration true"
    echo "fill 280 -63 280 330 -59 330 minecraft:dirt replace minecraft:magma_block"
    echo "fill 280 -63 280 330 -59 330 minecraft:dirt replace minecraft:lava"
    echo "fill 280 -61 280 330 -61 330 minecraft:grass_block"
    echo "fill 280 -60 280 330 -55 330 minecraft:air"

    # ── shadow blade: daggers, the leap, speed while held ──
    echo "gamerule natural_health_regeneration false"
    face
    # bob is still burning from the crossbow and low on health
    echo "effect give Bob minecraft:fire_resistance 30 0 true"
    echo "effect give Bob minecraft:instant_health 1 5"
    fresh shadow_blade
    sleep 1
    echo "execute if entity @a[name=Alice,nbt={active_effects:[{id:\"minecraft:speed\",amplifier:1b}]}] run say SMOKE shadow blade speed"
    do_ Alice face Bob
    WAIT=3 do_ Alice swap
    echo "execute if entity @a[name=Bob,nbt={active_effects:[{id:\"minecraft:blindness\"}]}] run say SMOKE daggers blind"
    echo "execute if items entity Alice weapon.mainhand *[minecraft:custom_data~{moneysmp_legend:\"shadow_blade\"}] run say SMOKE shadow blade stayed"
    echo "effect clear Bob"
    face
    echo "say SMOKE leap start"
    WAIT=0.5 do_ Alice sneakuse
    do_ Bob sees Alice
    sleep 2
    do_ Bob sees Alice

    # ── windweaver: the leap, then a gust that throws an armour stand (bots move themselves,
    #    so a push on Bob only shows if mineflayer plays it back, and NoAI mobs don't budge) ──
    face
    fresh windweaver
    WAIT=1 do_ Alice swap
    echo "execute if entity @a[name=Alice,nbt={active_effects:[{id:\"minecraft:speed\",amplifier:3b}]}] run say SMOKE wind leap"
    sleep 3
    echo "tp Alice 300 -60 300"
    echo "summon minecraft:armor_stand 300.5 -60 302.5 {Tags:[\"smoke_gust\"]}"
    sleep 1
    do_ Alice sneakswap
    echo "execute as @e[tag=smoke_gust] at @s unless entity @s[x=300.5,y=-60,z=302.5,distance=..1] run say SMOKE gust threw the stand"
    echo "kill @e[tag=smoke_gust]"

    # ── emerald set: worn passives, the chestplate's ring, the leggings' slam ──
    face
    echo "clear Alice"
    for piece in helmet:head chestplate:chest leggings:legs boots:feet; do
        echo "moneysmp legendary give Alice emerald_${piece%%:*}"
        sleep 0.5
        echo "item replace entity Alice armor.${piece##*:} from entity Alice hotbar.0"
        echo "item replace entity Alice hotbar.0 with minecraft:air"
    done
    sleep 2
    for eff in water_breathing resistance fire_resistance; do
        echo "execute if entity @a[name=Alice,nbt={active_effects:[{id:\"minecraft:$eff\"}]}] run say SMOKE emerald gives $eff"
    done
    echo "execute if entity @a[name=Alice,nbt={active_effects:[{id:\"minecraft:speed\",amplifier:1b}]}] run say SMOKE emerald boots speed"
    WAIT=5 do_ Alice crouch 4
    echo "execute if entity @a[name=Bob,nbt={active_effects:[{id:\"minecraft:glowing\"}]}] run say SMOKE emerald vision"
    echo "effect give Alice minecraft:instant_health 1 5"
    echo "execute at Alice run summon minecraft:lightning_bolt ~ ~ ~"
    sleep 2
    echo "execute if entity @a[name=Alice,nbt={Health:20.0f}] run say SMOKE lightning spared the chestplate"
    echo "tp Bob 300 -60 302 facing 300 -59 300"
    sleep 1
    for _ in 1 2 3 4 5 6; do do_ Alice face Bob; do_ Alice attack Bob; done
    echo "effect give Bob minecraft:instant_health 1 5"
    sleep 1
    do_ Alice face Bob
    WAIT=3 do_ Alice attack Bob
    echo "execute store result score #ringhp smoke run data get entity Bob Health 10"
    echo "scoreboard players get #ringhp smoke"
    echo "effect give Bob minecraft:instant_health 1 5"
    echo "effect give Alice minecraft:instant_health 1 5"
    echo "tp Bob 302 -60 300"
    echo "tp Alice 300 -40 300"
    sleep 4
    echo "execute if entity @a[name=Alice,nbt={Health:20.0f}] run say SMOKE leggings took the fall"
    echo "execute unless entity @a[name=Bob,nbt={Health:20.0f}] run say SMOKE shockwave hit Bob"

    # ── emerald pickaxe: 3x3, then the toggles ──
    face
    fresh emerald_pickaxe
    echo "fill 299 -60 302 301 -58 302 minecraft:stone"
    echo "tp Bob 300 -60 306"
    sleep 1
    do_ Alice look 300.5 -59 302
    WAIT=3 do_ Alice dig 300 -59 302
    echo "execute store result score #wall smoke run fill 299 -60 302 301 -58 302 minecraft:dirt replace minecraft:stone"
    echo "scoreboard players get #wall smoke"
    echo "fill 299 -60 302 301 -58 302 minecraft:air"
    do_ Alice swap
    do_ Alice sneakswap
    echo "clear Alice"
    echo "kill @e[type=item]"
    echo "effect clear @a"
    echo "gamerule natural_health_regeneration true"

    # ── wand of illusion ──
    echo "effect give Bob minecraft:instant_health 1 5"
    face
    fresh wand_of_illusion
    # only the cow it kills and then its own shell, so counting cows means something
    echo "kill @e[type=cow]"
    echo "summon minecraft:cow 300 -60 302 {Health:1f,NoAI:1b}"
    sleep 1
    do_ Alice attack cow
    echo "execute if items entity Alice weapon.mainhand *[minecraft:custom_data~{moneysmp_mob:\"minecraft:cow\"}] run say SMOKE wand holds the cow"
    face
    do_ Alice use
    echo "execute if entity @e[type=cow,tag=moneysmp_fx] run say SMOKE disguise shell up"
    echo "attribute Alice minecraft:max_health get"
    do_ Bob sees Alice
    do_ Alice count cow
    echo "tp Bob 300 -60 302"
    sleep 1
    # peaceful heals a heart a second, which would undo the blow before it can be seen
    echo "gamerule natural_health_regeneration false"
    do_ Bob attack cow
    echo "execute unless entity @a[name=Alice,nbt={Health:10.0f}] run say SMOKE blow on the disguise reached Alice"
    echo "gamerule natural_health_regeneration true"
    WAIT=3 do_ Alice sneakuse
    echo "execute unless entity @e[type=cow,tag=moneysmp_fx] run say SMOKE disguise shell gone"
    do_ Bob sees Alice
    echo "attribute Alice minecraft:max_health get"

    # ── crazy slots ──
    face
    fresh crazy_slots
    do_ Alice use
    echo "execute unless items entity Alice weapon.mainhand *[minecraft:custom_data~{moneysmp_legend:\"crazy_slots\"}] if items entity Alice weapon.mainhand *[minecraft:custom_data] run say SMOKE slots rolled"
    do_ Alice drop
    echo "execute if entity @e[type=item,nbt={Item:{components:{\"minecraft:custom_data\":{moneysmp_legend:\"crazy_slots\"}}}}] run say SMOKE dropped transformation turned back"
    echo "kill @e[type=item]"

    # ── furnaces had time by now ──
    echo "execute if items block 305 -50 310 container.0 minecraft:clay_ball run say SMOKE warden heart would not smelt"
    echo "execute if items block 307 -50 310 container.2 minecraft:brick run say SMOKE plain clay smelted"

    # ── species: a vampire's kill turns, and drops a head in the victim's team colour ──
    echo "moneysmp team set Alice Red"
    echo "moneysmp team set Bob Blue"
    echo "moneysmp legendary species Bob vampire"
    face
    echo "clear Alice"
    echo "damage Alice 100 minecraft:player_attack by Bob"
    sleep 2
    # Alice respawns on the spot and may already have picked it back up
    echo "execute if entity @e[type=item,nbt={Item:{components:{\"minecraft:custom_data\":{moneysmp_head:\"Red\"}}}}] run say SMOKE red head dropped"
    echo "execute if items entity @a container.* minecraft:player_head[minecraft:custom_data~{moneysmp_head:\"Red\"}] run say SMOKE red head dropped"
    echo "kill @e[type=item]"
    sleep 3

    # ── an altar won't take the crafter's own team's heads ──
    echo "clear Alice"
    echo "tp Alice 330 -60 300"
    sleep 1
    echo "execute as Alice at Alice run moneysmp legendary altar windweaver"
    echo "moneysmp set Alice 1000"
    echo "give Alice minecraft:breeze_rod 160"
    echo "give Alice minecraft:heavy_core 1"
    echo "give Alice minecraft:diamond_block 160"
    echo "give Alice minecraft:player_head[minecraft:custom_data={moneysmp_head:\"Red\"}] 10"
    sleep 2
    do_ Alice click 330 -60 300
    echo "give Alice minecraft:player_head[minecraft:custom_data={moneysmp_head:\"Blue\"}] 10"
    sleep 1
    do_ Alice click 330 -60 300
    echo "execute if items entity Alice container.* minecraft:player_head[minecraft:custom_data~{moneysmp_head:\"Red\"}] run say SMOKE own heads kept"
    echo "execute unless items entity Alice container.* minecraft:player_head[minecraft:custom_data~{moneysmp_head:\"Blue\"}] run say SMOKE enemy heads taken"
    echo "clear Alice"

    # ── contagion: completes, then breaks ──
    echo "moneysmp legendary species Bob human"
    echo "tp Alice 320 -60 300"
    sleep 1
    echo "execute as Alice at Alice run moneysmp legendary altar contagion"
    echo "moneysmp set Alice 2000"
    echo "moneysmp legendary give Alice contagion_catalyst"
    sleep 2
    do_ Alice click 320 -60 300
    wait_for "100% COMPLETE"
    echo "moneysmp legendary contagion reset"
    echo "moneysmp legendary give Alice contagion_catalyst"
    echo "tp Bob 321 -60 302"
    sleep 1
    do_ Alice click 320 -60 300
    do_ Bob look 320.5 -59.5 300.5
    do_ Bob hit 320 -60 300
    do_ Bob hit 320 -60 300
    do_ Bob hit 320 -60 300
    wait_for "aborting conversion"
    echo "execute as Alice at Alice run moneysmp legendary altar remove"
    sleep 1
    echo "execute if block 320 -60 300 minecraft:air run say SMOKE contagion altar removed"
    echo "moneysmp legendary altar list"
    sleep 2

    echo "forceload remove all"
    echo "worldborder set 600"
    echo "difficulty easy"
    echo "stop"
    wait_for "All dimensions are saved"
    kill $bots 2>/dev/null
    grep -q "All dimensions are saved" "$LOG" || powershell -NoProfile -Command \
        "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object CommandLine -match 'devlaunchinjector' | ForEach-Object { Stop-Process -Id \$_.ProcessId -Force }"
}

feed | ./gradlew.bat runServer --offline --console=plain > /tmp/smoke-legend-gradle.log 2>&1

{
check() {
    if grep -qF -- "$1" "$LOG" "$BOTLOG"; then echo "PASS  $2"; else echo "FAIL  $2"; fi
}
check "SMOKE altar placed" "altar barrier placed where the admin stood"
check "SMOKE altar recipe shown" "altar shows its recipe"
check "This altar still needs: 16x Block of Iron, \$40" "altar names what's short, 5x recipe and money"
check "SMOKE items kept when short" "nothing taken when money is short"
check "Alice has crafted a Hyperion Shard" "craft announced"
check "SMOKE shard crafted" "shard handed over"
check "SMOKE iron taken" "items taken"
check "Alice's balance: \$ 50" "\$50 taken from the balance"
check "SMOKE altar used up" "altar gone after one use"
if [ "$(grep -c "Unlocked 1 recipe(s) for Alice" "$LOG")" -ge 3 ]; then echo "PASS  part recipes load"; else echo "FAIL  part recipes load"; fi
check "SMOKE skeletal leap" "Skeletal Leap gives Speed III"
check "You can't use Skeletal Leap for another" "cooldown holds"
check "moneysmp:stun on attribute" "Bone Cage stuns"
check "SMOKE scythe slowed" "Scythe Throw slows"
check "for entity Bob is 16" "Crimson Bite takes two hearts"
check "SMOKE swap kept the sword" "swap-hands ability keeps Nightpiercer in hand"
check "for entity Bob is 20" "Crimson Bite wears off"
check "#lancehp has 120 [smoke]" "Holy Lance, aimed at Bob, takes 4 true hearts through Protection IV netherite and Resistance"
check "#bladehp has 170 [smoke]" "Scorching Blade's arc lands on the one just hit"
check "SMOKE hyperion stayed" "swap-hands ability keeps Hyperion in hand"
check "Bob is bleeding." "Bloodlust makes a player bleed"
check "#arrows has 3 [smoke]" "Vulcan's Crossbow fires three arrows"
check "Your next shot is Vulcan's Wrath" "Vulcan's Wrath primes"
check "#arrowhp has 140 [smoke]" "Vulcan's arrows take 3 hearts through Protection IV netherite and Resistance"
check "#nearhp has 140 [smoke]" "Vulcan's arrows land a near miss (0.75 blocks off at 18)"
check "#widehp has 200 [smoke]" "Vulcan's arrows still miss 3 blocks off"
check "#wrathhp has 60 [smoke]" "Vulcan's Wrath takes 5 hearts through the same, even right after another hit"
check "#craterleft has 0 [smoke]" "no magma left 30s after the Wrath"
check "#lavaleft has 0 [smoke]" "no lava left 30s after the Wrath"
check "SMOKE crater ground back" "the Wrath's crater fills back in"
check "SMOKE bob lifted out" "whoever stands in the crater is lifted out, not buried"
check "SMOKE shadow blade speed" "Shadow Blade gives Speed II while held"
check "SMOKE daggers blind" "Shadow Daggers blind"
check "SMOKE shadow blade stayed" "swap-hands ability keeps the Shadow Blade in hand"
if sed -n '/SMOKE leap start/,$p' "$BOTLOG" | grep "Bob SEES Alice" | head -2 | tr '
' ' ' | grep -q "hidden.*visible"; then
    echo "PASS  Shadow Leap hides, then surfaces"; else echo "FAIL  Shadow Leap hides, then surfaces"; fi
check "SMOKE wind leap" "Wind Leap gives Speed IV"
check "SMOKE gust threw the stand" "Windweaver Gust throws what's around"
for eff in water_breathing resistance fire_resistance; do check "SMOKE emerald gives $eff" "emerald set gives $eff"; done
check "SMOKE emerald boots speed" "emerald boots give Speed II"
check "SMOKE emerald vision" "Emerald Vision makes others glow"
check "SMOKE lightning spared the chestplate" "emerald chestplate ignores lightning"
check "#ringhp has 150 [smoke]" "7th hit calls the lightning ring (a punch, then 5 from the ring)"
check "SMOKE leggings took the fall" "emerald leggings take no fall damage"
check "SMOKE shockwave hit Bob" "the landing slams players nearby"
check "#wall has 0 [smoke]" "emerald pickaxe mines 3x3"
check "Mining 1x1" "pickaxe toggles to 1x1"
check "Now Fortune III" "pickaxe switches to Fortune III"
if grep -qE "#crater has [1-9][0-9]* \[smoke\]" "$LOG"; then echo "PASS  Vulcan's Wrath leaves a crater"; else echo "FAIL  Vulcan's Wrath leaves a crater"; fi
check "SMOKE wand holds the cow" "wand keeps what it killed"
check "SMOKE disguise shell up" "disguise shell spawned"
check "for entity Alice is 10" "disguise takes the form's health"
check "Bob SEES Alice hidden" "disguised player hidden from others"
check "Alice COUNT cow 0" "wearer can't see own shell"
check "SMOKE blow on the disguise reached Alice" "hit on the shell reaches the wearer"
check "SMOKE disguise shell gone" "undisguise removes the shell"
check "Bob SEES Alice visible" "undisguised player visible again"
check "SMOKE slots rolled" "Crazy Slots transforms"
check "SMOKE dropped transformation turned back" "dropped transformation reverts"
check "SMOKE warden heart would not smelt" "legendary part refused by the furnace"
check "SMOKE plain clay smelted" "furnace works for plain clay"
check "SMOKE red head dropped" "PvP death drops a head in the victim's team colour"
check "This altar still needs: 10x Enemy Team Head" "an altar won't count the crafter's own team's heads"
check "Alice has crafted Windweaver" "another team's heads craft"
check "SMOKE own heads kept" "own team's heads left in the inventory"
check "SMOKE enemy heads taken" "the other team's heads used up"
check "CONTAGION RITUAL TUNED TO" "ritual starts"
check "100% COMPLETE" "ritual completes"
check "Contagion Signal is at" "integrity warnings"
check "aborting conversion" "ritual breaks when hit"
check "SMOKE contagion altar removed" "altar removal"
if grep -qiE "mixin.*(error|fail)|Mixin apply failed|\[Server thread/ERROR\]|Exception" "$LOG"; then
    echo "FAIL  errors in log:"; grep -iE "mixin.*(error|fail)|Mixin apply failed|ERROR|Exception" "$LOG" | head
else
    echo "PASS  no mixin or server errors"
fi
grep -q "All dimensions are saved" "$LOG" || echo "WARN  server stalled in Saving worlds on stop and was killed"

python - "$DIR/legends.json" <<'EOF'
import json, sys
d = json.load(open(sys.argv[1]))
uid = {"Alice": None, "Bob": None}
names = json.load(open(sys.argv[1].replace("legends.json", "data.json")))["players"]
for k, v in names.items():
    if v.get("name") in uid: uid[v["name"]] = k
bad = 0
for name in uid:
    got = d["species"].get(uid[name], "human")
    ok = got == "vampire"
    bad |= not ok
    print(("PASS" if ok else "FAIL") + f"  {name} saved as {got} after the vampire ritual")
ok = d.get("contagionDone") is None and "ritual" not in d
print(("PASS" if ok else "FAIL") + "  no ritual left running or locked in after the reset and the break")
sys.exit(bad or not ok)
EOF
} | tee "$REPORT"

for f in $FILES; do
    rm -f "$DIR/$f.json"
    [ -f "$DIR/$f.json.smoke-bak" ] && mv "$DIR/$f.json.smoke-bak" "$DIR/$f.json"
done
grep -q FAIL "$REPORT" && exit 1 || exit 0
