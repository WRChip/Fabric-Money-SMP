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
- Control point event: KOTH-style capture rings with beacon beams, shown on the locator bar (which no longer shows players)
- Config and data stored as JSON next to the world

## Commands

- `/moneysmp balance [player]`
- `/moneysmp myteam`
- `/moneysmp teams`
- `/moneysmp tiers`
- `/moneysmp bid [amount]`
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
- `/moneysmp point loot|superloot` (prize from the container in your hand)
- `/moneysmp event control-point start|stop`

## Control points

Each point gets a beacon with a light grey beam and a 5-block particle ring. One player standing in the ring earns their team 5% every 30 seconds, so five players capture a point in two minutes. When several teams share a ring only the larger team gains, at the pace of its extra players. A quarter of the points (rounded) are picked as super points each event: darker netherite-coloured beam, four times slower to capture, and they drop the `superloot` prize instead of `loot`. A capture pays the team `controlPointPoints` and every member `controlPointMoney` (both in `config.json`), turns the beam the team colour and drops the prize in the ring. The locator bar shows only points: grey while unclaimed, team colour once captured, and it disappears when every point is taken. The platforms are restored when the event ends and a running event survives a restart.

## Building

```
./gradlew build
```

Output jar lands in `build/libs/`.
