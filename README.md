# MoneySMP (Fabric)

Economy and teams mod for a Minecraft SMP server, built on Fabric.

- Minecraft 1.21.11
- Requires [Fabric API](https://modrinth.com/mod/fabric-api) and [fabric-permissions-api](https://github.com/lucko/fabric-permissions-api) (bundled)
- Java 21

## Features

- Player balances with pay, bid, and admin fine/adjust commands
- Team system with tiers (F through S), team balances, and a leader role
- Auto-auction that walks tiers from F up to S, assigning any player still without a team
- Transaction log with time-range filtering and pagination
- Control point event: KOTH-style capture rings with particle beams, shown on the locator bar (which no longer shows players)
- Unlockout event: a 5x5 goal board every team races on at once, drawn on a map item that redraws every second
- Altar event: eight core fragments captured like control points near the world border, carried at a cost, and fed to an altar at 0, 0 for a mace
- Legendaries: the Altar SMP arc 1 and 2 weapons, each crafted once at an admin-placed altar that takes items and money from your balance, plus the vampire / pale rot / human species and the contagion ritual
- Config and data stored as JSON next to the world

## Commands

- `/moneysmp balance [player]`
- `/moneysmp myteam`
- `/moneysmp teams`
- `/moneysmp tiers`
- `/moneysmp bid [amount]`
- `/unlockout [list|map]` (also `/moneysmp unlockout`)
- `/altar` (also `/altar list`, `/moneysmp altar`): where every fragment is and who carries it
- `/pay <player> <amount>`
- `/bid <amount>`

Admin (`moneysmp.admin`):

- `/moneysmp reset`
- `/moneysmp teambal`, `teambal balance` (members in debt go back to $0 and their teammates cover it, as evenly as their balances allow)
- `/moneysmp teambal debtbalance` (every team with someone in debt adds up its members' money and splits it evenly between them)
- `/moneysmp randomteams [tier]`
- `/moneysmp auction [stop]`
- `/moneysmp post-auction`
- `/moneysmp reload` (reread `config.json`; a file that fails to parse keeps the current settings)
- `/moneysmp tier set|clear <player> [tier]`
- `/moneysmp teammax <number>`
- `/moneysmp teamcount <count>`
- `/moneysmp transaction <time> [page]`
- `/moneysmp fine <team> <amount> [reason]` (split evenly across the team's members)
- `/moneysmp team set <player> <team>`, `team remove <player>`, `team enable|disable <team>`, `team leader <player>`
- `/moneysmp point <number>` (set a control point where you stand), `point remove <number>`, `point list`
- `/moneysmp point loot|superloot add|clear|list|mode <once|random>` (prize sets from the container in your hand)
- `/moneysmp event control-point start|stop`
- `/moneysmp event unlockout start [time]|stop`
- `/moneysmp timer <player>` (a carrier's logout grace, today's play and day reset), `timer skip <player>` (grace over, today's hour done)
- `/moneysmp event altar start|stop`, `event altar give <player> <fragment>` (a fresh copy into their hands), `event altar respawn <fragment>` (a fresh copy back at its ring)
- `/moneysmp legendary give <player> <item>`, `legendary altar <type>` (an altar where you stand), `legendary altar remove` (the nearest within 8 blocks), `legendary altar list`
- `/moneysmp legendary find [item]` (the emerald chestplate unless named): who is holding it, offline players included, from the saved player data. Loose items and containers in the world aren't searched
- `/moneysmp legendary species <player|all> <human|vampire|pale|plaguedoctor>`, `legendary contagion stop|reset`

## Control points

Each point gets a light grey particle beam into the sky and a 5-block particle ring; nothing in the world is changed. The capture zone is a cylinder from one block below the point to ten above it. One player standing in the ring earns their team `controlPointPercentPer30s` every 30 seconds, 5 by default, so five players capture a point in two minutes. When several teams share a ring only the larger team gains, at the pace of its extra players. A quarter of the points (rounded) are picked as super points each event: darker netherite-coloured beam, four times slower to capture, and they draw from the `superloot` sets instead of `loot`. Each pool holds any number of prize sets, added one container at a time. In `once` mode captures hand the sets out in order, each a single time per event (later captures drop nothing once they run out); in `random` mode every capture rolls any set. A capture pays the team `controlPointPoints` and splits `controlPointMoney`, or `controlPointSuperMoney` for a super point (in `config.json`), evenly between its members who are online, turns the beam and ring the team colour and drops the prize in the ring. The locator bar shows only points: grey while unclaimed, team colour once captured, and it disappears when every point is taken. A running event survives a restart.

The numbers above are defaults. `config.json` can change them with `controlPointPercentPer30s` (5), `controlPointRadius` (5), `controlPointHeight` (10), `controlPointSuperPercent` (25) and `controlPointSuperSlowdown` (4).

## Unlockout

Bingo-style board of 25 goals, one shared board per team (progress on a goal is the whole team's). Unlike lockout a goal never locks: the first team to finish it earns 10 points, the second 8, then 6, 4, 2 and nothing from the sixth team on. Finishing a full row, column or diagonal is worth 30, then 24, 18, 12, 6, 0 in the same way. The first team to clear the whole board gets +600 and ends the event; otherwise it ends when the time given to `start` (e.g. `2h`, `90m`) runs out, with warnings at 1h, 30m, 10m, 5m, 1m and 10s. Points belong to the event alone: every team starts at zero and nothing carries over to the next event or onto the control point tally. Nothing is paid while it runs. When the event ends, for any reason including `stop`, each team is paid $1 for each point it scored, split evenly between its members who are online at that moment; a team with nobody online gets nothing. A boss bar shows the standings and time left.

Everyone gets a locked map of their team's board on start, join and respawn (`/unlockout map` for another). Each cell shows the goal's label, a dot per team that has it (in the order they got it, so the points still on offer is 10 minus two per dot), and a bar with your team's progress; cells your team has finished are filled in your colour. `/unlockout` prints the board in chat with hover details and `/unlockout list` every goal with your team's progress.

The board:

| | | | | |
| --- | --- | --- | --- | --- |
| Kill 10 Wardens | Breed 2,500 animals | Swim 100,000 blocks | Sneak 100,000 blocks | Wear all four armor trims from brushing at once |
| Eat 38 different foods | Kill 50 piglin brutes | Unlock 40 unique ominous vaults | Die to the void in the Overworld, the Nether and the End | Compost 10,000 food items |
| Get Hero of the Village, then die to the Warden within 30s | Kill 50 players from opposing teams | Spy on 70 different mobs with a spyglass | Relic disc from trail ruins | Deal 1,000,000 damage |
| 4,096 red concrete | Have 30 unique mobs on one player's leashes at the same time | Give a dolphin a netherite block | Apply the silence trim to your entire armor | Kill 36 different hostile mob types |
| Sprint 250,000 blocks | 20 effects active at once | Have Haste II and conduit power at the same time | Take 200,000 damage | Rename a ghast, iron golem, elder guardian and wither Dinnerbone |

How each is measured: kills credit the killing player's team. The void goal needs an actual fall out of the world, once in each dimension (so under the bedrock in the Overworld and Nether); `/kill` doesn't count. Hero then Warden means dying to a Warden within 30 seconds of winning a raid (any raid) as that player. The dolphin goal counts when a dolphin picks up a netherite block a teammate threw. Sneak, sprint and swim distance come from the vanilla stats, summed across the team from the moment the event started. Damage dealt and taken count the raw amount of every hit that lands on or from a living entity, in health points (a heart is 2), killing blows included. Damage dealt is capped per hit at the victim's max health, so tricks like feeding a parrot a cookie (Float.MAX_VALUE) only count the parrot's 6; damage taken is not capped. Red concrete is the total in the team's inventories right now, and effects and worn armor are checked on each player once a second. Vaults are counted per vault, so teammates opening the same one count once. The silence trim is checked on the four worn armor pieces, any material. The brushing goal needs one teammate wearing the wayfinder, raiser, shaper and host trims at once, one per piece; those four are the only trims archaeology drops, all from trail ruins. Dinnerbone needs a name tag named exactly that, used on each of a ghast, an iron golem, an elder guardian and a wither. Leashes are checked once a second: one teammate has to be holding 30 different mob types on their own leashes at that moment, so mobs tied to fence posts or split between teammates don't add up. Boats can be leashed but are vehicles, not mobs, and hostile mobs can't be leashed in vanilla. The conduit goal needs one teammate under Conduit Power and Haste II at once. Foods are anything with a food component; composting counts every food item a player puts in by hand, only when the composter actually takes it (hoppers don't count). Breeding counts every breed, repeats included. Spyglass spying uses the same line-of-sight ray as the vanilla `looking_at` advancement check, at any distance up to 100 blocks. The goals are sized to be a grind even for an endgame team: expect several days, not hours, and several goals need the End. The event survives a restart.

## Altar

Eight fragments, an offering, one mace, no money. `event altar start` picks eight spots evenly around the world border, 45 degrees apart and 80% of the way out (a spot that lands in water is nudged in or out a little, so the spacing holds) and gives each a capture ring and beam in its own colour, then raises the altar on the surface at 0, 0: a stone pedestal two blocks wide and tall from the resource pack (an item display wearing a custom model, over a 2x2x2 of invisible barriers that give it a hitbox) with a mace turning slowly above it and the recipe floating over that. It needs a world border (`/worldborder set`) because the spots are placed relative to it; the default border is refused. Picking the spots loads, and may generate, their chunks, so the command can take a moment. The altar's chunks stay force-loaded for the whole event.

Each spot captures exactly like a control point (same `controlPointRadius`, `controlPointHeight`, `controlPointPercentPer30s` and contest rules) but pays nothing: when a team fills the bar the spot's fragment drops in the ring and its ring, beam and waypoint turn black. Fragments are echo shards named Ember, Frost, Storm, Stone, Tide, Void, Dawn and Dusk Fragment (the altar's recipe calls the set The Fragments of the World), one of each, unstackable, and stamped with the event they belong to, so ones left over from an earlier event are ordinary junk. On the ground a fragment glows, never despawns and survives fire, lava and explosions. If one is destroyed anyway (the void, `/kill`, anything) a fresh copy appears at its original spot and everyone is told; the old one, if it still exists somewhere, is junk.

Carrying a fragment, including inside a shulker box or bundle, costs you, checked every tick:

- everyone sees you glow;
- leather armour is taken off and put back in your inventory;
- it can't go in your ender chest, not even packed in a shulker box or bundle (neither can a mace, event or no event);
- everyone is told where you are each time you log on, and the server rolls the carriers: every `altarLocateOnlineMinutes` (15) the online ones with their live coordinates, every `altarLocateOfflineMinutes` (360) the offline ones with where they were last seen;
- for the first `altarLogoutMinutes` (30) after you first pick one up, logging out leaves every fragment you carry where you stood (a fresh copy appears there; the one you keep is junk) and everyone is told. You get a warning on pickup and an all-clear when the time is up. A server restart doesn't count. From the moment you pick one up a boss bar of your own shows how much of today's hour you still owe, filling as you play, and how long the logout grace has left;
- you have to be online at least `altarDailyPlayMinutes` (60) out of every `altarDayMinutes` (1440, a day), counted from the pickup. Miss it and every fragment you carry reappears at its original spot as an item, everyone is told, and the copy still in your inventory is junk that vanishes the next time you are on. Time the server is down doesn't count against you.

Every change of hands is announced, and `/altar` lists where each fragment is: still at its ring (with coordinates), carried by whom and whether they are online, in which container block at what coordinates, or lying on the ground where. Containers and loose fragments are found by scanning loaded chunks (unopened loot chests are skipped), so a chest in an unloaded chunk keeps its last known position. The same container and ground positions go out with the 15-minute roll call. The altar takes, all loose in one player's inventory:

- 8x The Fragments of the World
- 16x Diamond Block
- 1x Breeze Rod
- 16x Netherite Ingot
- 4x Enchanted Golden Apple

Right-click the pedestal with all of it to start the ritual (a click with less tells you what's short). Everything is consumed, a boss bar counts down `altarRitualMinutes` (30) with warnings at 15m, 5m, 1m and 10s, and at the end lightning strikes the altar, a plain mace drops out of the sky 30 blocks above it and the event ends, taking the pedestal and its displays with it. The mace never despawns. While the event runs the altar can't be mined and is rebuilt every second if anything is missing, displays included. `event altar stop` ends it early and cancels any ritual; what was consumed is gone. The event survives a restart.

## Legendaries

The weapons of Altar SMP arcs 1 to 3, rebuilt rather than ported from the [arc 1](https://modrinth.com/plugin/altar-smp-arc-1-plugin) and [arc 2](https://modrinth.com/plugin/altar-arc-2) plugins, with arc 3 going by the community [AltarSMP Season 1](https://www.spigotmc.org/resources/altarsmp-season-1.137751/) replica's lore. Arc 3's copper armour and pickaxe are emerald here. Each is crafted at its own altar, which an admin places with `/moneysmp legendary altar <type>` where they stand: a pedestal with the result turning over it and the recipe floating above. Right-click it with everything in your inventory; a click with less lists what is short, money included, and takes nothing. Money is an ingredient: every altar also takes its price from your balance (money committed to an auction bid doesn't count). An altar crafts once and is gone, and everyone is told who crafted what. Altars can't be broken and rebuild themselves if anything goes missing.

| Altar | Items | Money |
| --- | --- | --- |
| Bone Blade | 15 player heads, 30 wither skeleton skulls, 30 skeleton skulls, 320 copper blocks, 320 iron blocks, 320 bone blocks, Warden's Heart, Weapon Handle | $500 |
| Bloodlust | 320 redstone blocks, 240 gold blocks, 15 nether stars, 30 player heads, Warden's Heart, Weapon Handle | $500 |
| Frost Scythe | trident, 320 ice, 320 blue ice, 320 diamond blocks, 320 prismarine shards, 320 packed ice, Warden's Heart, Weapon Handle | $500 |
| Vulcan's Crossbow | 320 TNT, 320 blaze rods, 320 magma cream, 320 ancient debris, 15 player heads, Warden's Heart, Vulkan Skull | $500 |
| Wand of Illusion | 30 totems, 160 gold blocks, 320 amethyst blocks, 10 player heads, enchanted golden apple, Warden's Heart, Illusion Core | $750 |
| Hyperion Shard | 20 iron blocks, stick, blaze powder | $50 |
| Nightpiercer Shard | 20 redstone blocks, stick, magma cream | $50 |
| Pale Shard | 20 pale moss blocks, stick, resin clump | $50 |
| Hyperion / Nightpiercer / Pale Cannon | 25 of its shard | $300 |
| Crazy Slots | dragon egg (its crafter becomes the first vampire) | $1,000 |
| Contagion ritual | Contagion Catalyst (starts the ritual, see below) | $500 |
| Shadow Blade | 20 dragon's breath, 60 disc fragments, dragon head, 80 echo shards, 120 black candles, 10 player heads | $500 |
| Windweaver | 160 breeze rods, heavy core, 160 diamond blocks, 10 player heads | $500 |
| Emerald Helmet | 80 emerald blocks, netherite helmet, heart of the sea | $250 |
| Emerald Chestplate | 120 emerald blocks, netherite chestplate, 80 lightning rods | $250 |
| Emerald Leggings | 100 emerald blocks, netherite leggings, 80 wind charges | $250 |
| Emerald Boots | 60 emerald blocks, netherite boots, 80 blaze powder | $250 |
| Emerald Pickaxe | 80 emerald blocks, netherite pickaxe, 80 TNT | $250 |

The parts: a Warden's Heart drops from every warden, a team head (Red Head, Blue Head and so on, stacking like any item) from every player on a team killed by another player, which counts at altars for any team but its own, and the Weapon Handle (2 netherite ingots and a nether star, shapeless), Vulkan Skull (nether wart blocks around quartz, netherite scrap and a gold block) and Illusion Core (emerald blocks and eyes of ender around a heavy core) are crafting-table recipes. The Contagion Catalyst only comes from `legendary give`. The original has no way to make Pale Shards, so they get a shard altar like the other two. The original Shadow Blade wants the dragon egg, which Crazy Slots already takes, so it takes a dragon head; Windweaver's fabricator ritual and the copper set's trial events become plain altar recipes. Parts only work at altars: they can't be smelted or crafted as what they are underneath.

What they do. Cooldowns show as a boss bar, and trying early tells you how long is left. Every ability's damage is true damage: armour, enchantments, Resistance and shields don't lower it, so the numbers below (in health points, a heart is 2) land the same on netherite as on bare skin. Plain sword hits are vanilla.

- **Bone Blade**: right-click leaps where you look with Speed III (30s). Crouch + right-click throws a bone that roots whatever it hits for 5 seconds, bones circling it (60s).
- **Bloodlust** grows with its own player kills, counted on the sword. Hits on players can make them bleed (`bleedChance` 15%, `bleedDamagePerSecond` 1 for `bleedSeconds` 4). 1 kill: Speed II while held. 2: a line of blood to every player within 30 blocks every 5 seconds, drawn for you alone. 3: right-click for Blood Trail, 10 seconds unseen and untouchable (you can't hit either) leaving blood where you walk (60s). 4: Strength I while held. 5: crouch + right-click for Blood Hook, a 20-block chain that yanks the first thing it touches to you (30s).
- **Frost Scythe**: right-click throws the scythe, and what it hits takes `scytheThrowDamage` (6), Slowness III for 8s and freezes (30s). Crouch + right-click raises three blocks of ice over your head that follow you for two seconds, then fly where you look and burst for `scytheIceDamage` (5) each (45s).
- **Vulcan's Crossbow** fires three arrows instead of whatever is loaded: the middle one on fire. Each volley deals `vulcanArrowDamage` (6) to each target it hits, and an arrow passing within about a block of someone counts as a hit. Vulcan damage also ignores the half second of invulnerability after a hit, so it lands in full mid-fight. Crouch + right-click readies Vulcan's Wrath (45s): the next shot is a fireball doing `vulcanWrathDamage` (10) within 2 blocks that leaves a crater of magma and lava (bedrock, barriers, containers and altars are left alone, and anyone standing in it drops in rather than being sealed in magma). 30 seconds later the crater fills back in with whatever was there (a block placed in it since stays, and anyone in it is lifted out).
- **Wand of Illusion** keeps the last thing it killed. Right-click to become it: you vanish for everyone else and they see the mob (or, for a player, a mannequin with their skin and name) moving with you, and blows aimed at it land on you. You take its health (capped at 40, at least 2); fish suffocate out of water, and endermen, blazes and snow golems hurt in it. Right-click while disguised for the form's ability: Warden sonic boom for `wardenBoomDamage` (10) within 5 blocks, Enderman ender pearl, Elder Guardian Mining Fatigue III on nearby players, Ender Dragon dragon fireball (cooldowns in config). Crouch + right-click drops the disguise; dying, logging off or changing dimension drops it too.
- **Hyperion**: immune to fire while held, and its hits put hallowed flames on vampires (a point of damage and a burn each second, a 1 in 5 chance each second to go out). Swap hands (F) for Holy Lance, a beam of light on whoever you look at (give or take a block) or the block you look at, within 100 blocks, hitting everything within 3 for `holyLanceDamage` (8), 2 more against vampires and pale rots (60s). Crouch + F readies Scorching Blade: your next hit looses an arc of fire up to 5 blocks out, the one you hit included, for `scorchingBladeDamage` (3) (30s). A human's Hyperion kill cures its victim.
- **Nightpiercer**: Regeneration I at night while held. F for Crimson Bite: players up to 6 blocks in front lose two hearts for 10 seconds (vampires are immune) and you gain two per player bitten (30s). Crouch + F turns you into a cloud of bats flying where you look for 3 seconds, unseen (30s). The F abilities keep the sword in your main hand.
- **Pale Cannon** fires a moss shot for `paleShotDamage` (5). Crouch + right-click readies the vines (60s): the next shot is a blast doing `paleBigShotDamage` (10) within 2 blocks that infects players with pale rot for 10 minutes (milk cures it).
- **Shadow Blade**: Speed II while held, and a hit from behind has a 30% chance to pull its target in. Crouch + right-click melts you into shadow for a second and a half, unseen and untouchable, gliding where you look (walls still stop you); crouch + right-click again surfaces early (30s). F throws three Shadow Daggers for `shadowDaggerDamage` (3) each, with Slowness II and Blindness for 3 seconds (45s).
- **Windweaver**: F for Wind Leap, a leap where you look with Speed IV for 5 seconds and no fall damage on the landing (15s). Crouch + F starts Windweaver Gust: keep crouching to charge it (up to 5 seconds), let go and everything within 4 to 8 blocks is thrown away and up, taking up to `windGustDamage` (6) by charge (30s).
- **Emerald armour** (netherite underneath, Protection III): the helmet gives water breathing (with Respiration III and Aqua Affinity), and holding crouch for 3 seconds in it makes every other player within 100 blocks glow for 15 (30s). The chestplate gives Resistance I and lightning immunity, and every 7th hit you land on a player calls a ring of lightning around you for `lightningRingDamage` (5) to everyone within 6. The leggings take all fall damage, and a landing from 5 blocks or more slams everyone within 5 for 2 plus 1 per 5 blocks fallen. The boots give Fire Resistance and Speed II, Speed III on emerald blocks.
- **Emerald Pickaxe** (Efficiency V): mines 3x3 across the face you're looking at, pickaxe blocks only, each through a normal break (crouch to mine one block). F toggles 3x3 off and on, crouch + F switches Silk Touch for Fortune III and back; the lore shows which.
- **Crazy Slots**: right-click to become a random one of the ten weapons for 30 seconds, enchanted with Sharpness V, Fire Aspect II, Sweeping Edge III and Looting III (Quick Charge III on the crossbows) (60s). The transformation can't be moved out of its slot, and dropped, on death or when time runs out it is Crazy Slots again.

Legendaries are unbreakable, and dropped ones survive fire, lava and explosions.

**Species.** Everyone starts human. A vampire's kill makes its victim a vampire, a player who dies standing on moss or still infected by the pale cannon becomes a pale rot, and a human's Hyperion kill makes its victim human. The first vampire (whoever crafted Crazy Slots) and the plague doctor (`legendary species <player> plaguedoctor`) aren't changed by any of these. Vampires burn in open daylight in the Overworld; anywhere else they get Speed I, Strength I and Fire Resistance (`vampireFireResistance`). Pale rots hit players twice as hard from behind and get Weakness in the rain (`paleWeaknessInRain`). The tab list marks vampires ☾ and pale rots ✿, darker for the originals.

**Contagion ritual.** Right-click the contagion altar holding a Contagion Catalyst and $500 to start it. The ritual takes your species and runs for `contagionSeconds` (300) with a boss bar for everyone. Anyone can stop it by hitting the altar `contagionIntegrity` (40) times, and the catalyst is lost. When it finishes every player online becomes that species, and so does everyone who joins after, until an admin runs `legendary contagion reset`; until then it can't be run again.

All of these numbers live in `config.json` under `legendary`, and the prices under `legendary.cost`. Altars, species and a running ritual are kept in `legends.json` and survive a restart; cooldowns, disguises and stuns don't.

## Resource pack

The pack holds the legendaries' looks (models and textures from the MIT-licensed [AltarSMP Resource Pack](https://modrinth.com/resourcepack/altarsmp-resource-pack) by _Duckzy_, credited in `resourcepack/CREDITS.txt`, plus the emerald set, which is vanilla's diamond gear recoloured green), a shard for each fragment (eight textures, each its own shape for its name: a flame, an ice crystal, a lightning bolt, a cracked rock, a droplet, a rift, a rising sun and a crescent moon) and the altar pedestal (a three-piece block model: base, carved column, overhanging cap). `textures.py` redraws every texture. `./gradlew build` zips the pack into `build/libs/MoneySMP-pack-<version>.zip` next to the jar. Host that zip somewhere players can fetch it over plain HTTP and point the server at it in `server.properties`:

```
resource-pack=https://example.com/MoneySMP-pack-1.5.18.zip
resource-pack-sha1=<sha1 of the zip>
require-resource-pack=true
```

Without the pack a fragment renders as the missing-texture block, so keep the sha1 in step with the zip you upload (`sha1sum` prints it).

## Building

```
./gradlew build
```

Output jar lands in `build/libs/`.
