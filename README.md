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

## Building

```
./gradlew build
```

Output jar lands in `build/libs/`.
