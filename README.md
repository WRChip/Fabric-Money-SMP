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

Each point gets a light grey particle beam into the sky and a 5-block particle ring; nothing in the world is changed. The capture zone is a cylinder from one block below the point to ten above it. One player standing in the ring earns their team 5% every 30 seconds, so five players capture a point in two minutes. When several teams share a ring only the larger team gains, at the pace of its extra players. A quarter of the points (rounded) are picked as super points each event: darker netherite-coloured beam, four times slower to capture, and they draw from the `superloot` sets instead of `loot`. Each pool holds any number of prize sets, added one container at a time. In `once` mode captures hand the sets out in order, each a single time per event (later captures drop nothing once they run out); in `random` mode every capture rolls any set. A capture pays the team `controlPointPoints` and every member `controlPointMoney` (both in `config.json`), turns the beam and ring the team colour and drops the prize in the ring. The locator bar shows only points: grey while unclaimed, team colour once captured, and it disappears when every point is taken. A running event survives a restart.

## Unlockout

Bingo-style board of 25 goals, one shared board per team (progress on a goal is the whole team's). Unlike lockout a goal never locks: the first team to finish it earns 5 points, the second 4, then 3, 2, 1 and nothing from the sixth team on. Finishing a full row, column or diagonal is worth 15, then 12, 9, 6, 3, 0 in the same way. The first team to clear the whole board gets +200 and ends the event; otherwise it ends when the time given to `start` (e.g. `2h`, `90m`) runs out, with warnings at 1h, 30m, 10m, 5m, 1m and 10s. Points go on the same team tally as control points. A boss bar shows the standings and time left.

Everyone gets a locked map of their team's board on start, join and respawn (`/unlockout map` for another). Each cell shows the goal's label, a dot per team that has it (in the order they got it, so the points still on offer is 5 minus the dots), and a bar with your team's progress; cells your team has finished are filled in your colour. `/unlockout` prints the board in chat with hover details and `/unlockout list` every goal with your team's progress.

The board:

| | | | | |
| --- | --- | --- | --- | --- |
| Kill the Warden | Breed 10 unique mobs | Reach y=320 | Sneak 500 blocks | Win a level-5 ominous raid |
| Eat 15 different foods | Kill an evoker, ravager, piglin brute and elder guardian | Open an ominous vault | Summon and kill the Wither | Compost 7 types of edible food |
| Kill a Breeze with a wind charge | Kill a player from 2 different opposing teams | Spy on 25 different mobs with a spyglass | Relic disc from trail ruins | Deal 1,000,000 damage |
| 320 red concrete | Empty your hunger bar to zero | Hoglin → zoglin, then kill it | Craft a recovery compass | Kill 15 different hostile mob types |
| Sprint 1,000 blocks | 10 effects active at once | Active conduit | Take 5,000 damage | Rename a ghast Dinnerbone |

How each is measured: kills credit the killing player's team (the Breeze must die to a wind charge hit, the Wither must have been summoned within 50 blocks of a teammate, the zoglin must have converted from a hoglin during the event). Sneak and sprint distance come from the vanilla stats, summed across the team from the moment the event started. Damage dealt and taken count the raw amount of every hit that lands on or from a living entity, in health points (a heart is 2), killing blows included and not capped at the victim's health. That is deliberate: feeding a parrot a cookie deals Float.MAX_VALUE and finishes the 1,000,000 goal outright, and a reflected ghast fireball counts 1000. Red concrete is the total in the team's inventories right now. "Active conduit" means a teammate is under Conduit Power. Foods are anything with a food component; composted foods the same, but only when the composter actually takes the item. Spyglass spying uses the same line-of-sight ray as the vanilla `looking_at` advancement check, at any distance up to 100 blocks. The event survives a restart.

## Building

```
./gradlew build
```

Output jar lands in `build/libs/`.
