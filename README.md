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
- Config and data stored as JSON next to the world

## Commands

- `/moneysmp balance [player]`
- `/moneysmp myteam`
- `/moneysmp teams`
- `/moneysmp tiers`
- `/moneysmp bid [amount]`
- `/unlockout [list|map]` (also `/moneysmp unlockout`)
- `/pay <player> <amount>`
- `/bid <amount>`

Admin (`moneysmp.admin`):

- `/moneysmp reset`
- `/moneysmp teambal`
- `/moneysmp randomteams [tier]`
- `/moneysmp auction [stop]`
- `/moneysmp post-auction`
- `/moneysmp reload` (reread `config.json`; a file that fails to parse keeps the current settings)
- `/moneysmp tier set|clear <player> [tier]`
- `/moneysmp teammax <number>`
- `/moneysmp teamcount <count>`
- `/moneysmp transaction <time> [page]`
- `/moneysmp fine <player> <amount> [reason]`
- `/moneysmp team set|enable|disable|leader <team>`
- `/moneysmp point <number>` (set a control point where you stand), `point remove <number>`, `point list`
- `/moneysmp point loot|superloot add|clear|list|mode <once|random>` (prize sets from the container in your hand)
- `/moneysmp event control-point start|stop`
- `/moneysmp event unlockout start [time]|stop`

## Control points

Each point gets a light grey particle beam into the sky and a 5-block particle ring; nothing in the world is changed. The capture zone is a cylinder from one block below the point to ten above it. One player standing in the ring earns their team `controlPointPercentPer30s` every 30 seconds, 5 by default, so five players capture a point in two minutes. When several teams share a ring only the larger team gains, at the pace of its extra players. A quarter of the points (rounded) are picked as super points each event: darker netherite-coloured beam, four times slower to capture, and they draw from the `superloot` sets instead of `loot`. Each pool holds any number of prize sets, added one container at a time. In `once` mode captures hand the sets out in order, each a single time per event (later captures drop nothing once they run out); in `random` mode every capture rolls any set. A capture pays the team `controlPointPoints` and every member `controlPointMoney`, or `controlPointSuperMoney` for a super point (in `config.json`), turns the beam and ring the team colour and drops the prize in the ring. The locator bar shows only points: grey while unclaimed, team colour once captured, and it disappears when every point is taken. A running event survives a restart.

The numbers above are defaults. `config.json` can change them with `controlPointPercentPer30s` (5), `controlPointRadius` (5), `controlPointHeight` (10), `controlPointSuperPercent` (25) and `controlPointSuperSlowdown` (4).

## Unlockout

Bingo-style board of 25 goals, one shared board per team (progress on a goal is the whole team's). Unlike lockout a goal never locks: the first team to finish it earns 10 points, the second 8, then 6, 4, 2 and nothing from the sixth team on. Finishing a full row, column or diagonal is worth 30, then 24, 18, 12, 6, 0 in the same way. The first team to clear the whole board gets +600 and ends the event; otherwise it ends when the time given to `start` (e.g. `2h`, `90m`) runs out, with warnings at 1h, 30m, 10m, 5m, 1m and 10s. Points belong to the event alone: every team starts at zero and nothing carries over to the next event or onto the control point tally. Every point earned (goal, line or full board) also pays every member of the team `unlockoutMoneyPerPoint` (config.json, default $10) in cash. A boss bar shows the standings and time left.

Everyone gets a locked map of their team's board on start, join and respawn (`/unlockout map` for another). Each cell shows the goal's label, a dot per team that has it (in the order they got it, so the points still on offer is 10 minus two per dot), and a bar with your team's progress; cells your team has finished are filled in your colour. `/unlockout` prints the board in chat with hover details and `/unlockout list` every goal with your team's progress.

The board:

| | | | | |
| --- | --- | --- | --- | --- |
| Kill a Wither, an Elder Guardian and a Warden | Breed 20 unique mobs | Swim 15,000 blocks | Sneak 10,000 blocks | Win a level-5 ominous raid |
| Eat 30 different foods | Kill an evoker, ravager, piglin brute and elder guardian | Unlock 10 unique ominous vaults | Die to the void | Compost 11 types of edible food |
| Get Hero of the Village, then die to the Warden within 30s | Kill a player from 3 different opposing teams | Spy on 40 different mobs with a spyglass | Relic disc from trail ruins | Deal 1,000,000 damage |
| 1,024 red concrete | Have 20 unique mobs on one player's leashes at the same time | Give a dolphin a netherite block | Apply the silence trim to your entire armor | Kill 28 different hostile mob types |
| Sprint 40,000 blocks | 15 effects active at once | Have Haste II and conduit power at the same time | Take 20,000 damage | Rename a ghast, iron golem, elder guardian and wither Dinnerbone |

How each is measured: kills credit the killing player's team. The void goal needs an actual fall out of the world; `/kill` doesn't count. Hero then Warden means dying to a Warden within 30 seconds of winning a raid (any raid) as that player. The dolphin goal counts when a dolphin picks up a netherite block a teammate threw. Sneak, sprint and swim distance come from the vanilla stats, summed across the team from the moment the event started. Damage dealt and taken count the raw amount of every hit that lands on or from a living entity, in health points (a heart is 2), killing blows included and not capped at the victim's health. That is deliberate: feeding a parrot a cookie deals Float.MAX_VALUE and finishes the 1,000,000 goal outright, and a reflected ghast fireball counts 1000. Red concrete is the total in the team's inventories right now, and effects and worn armor are checked on each player once a second. Vaults are counted per vault, so teammates opening the same one count once. The silence trim is checked on the four worn armor pieces, any material. Dinnerbone needs a name tag named exactly that, used on each of a ghast, an iron golem, an elder guardian and a wither. Leashes are checked once a second: one teammate has to be holding 20 different mob types on their own leashes at that moment, so mobs tied to fence posts or split between teammates don't add up. Boats can be leashed but are vehicles, not mobs, and hostile mobs can't be leashed in vanilla. The conduit goal needs one teammate under Conduit Power and Haste II at once. Foods are anything with a food component; composted foods the same, but only when the composter actually takes the item. Spyglass spying uses the same line-of-sight ray as the vanilla `looking_at` advancement check, at any distance up to 100 blocks. Nothing on the board needs the End, and the goals are sized so a team of three needs roughly a day (`event unlockout start 24h`). The event survives a restart.

## Building

```
./gradlew build
```

Output jar lands in `build/libs/`.
