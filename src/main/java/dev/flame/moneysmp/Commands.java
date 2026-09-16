package dev.flame.moneysmp;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static com.mojang.brigadier.arguments.StringArgumentType.word;

final class Commands {
    private static final String LINE = "&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━";
    private static final List<String> ADMIN_SUBS = List.of("give", "take", "set", "reset", "fine", "transaction",
        "teambal", "teammax", "teamcount", "team", "randomteams", "tier", "auction", "post-auction");
    // reshaping teams or tiers under a live auction would leave it selling players that
    // moved or bidding for teams whose leader changed
    private static final Set<String> LOCKED_DURING_AUCTION = Set.of("reset", "randomteams", "tier", "team", "post-auction");

    private final MoneySMP plugin;

    private Commands(MoneySMP plugin) {
        this.plugin = plugin;
    }

    static void register(MoneySMP plugin, CommandDispatcher<CommandSourceStack> d) {
        Commands c = new Commands(plugin);

        SuggestionProvider<CommandSourceStack> players = (ctx, b) -> {
            Set<String> names = new TreeSet<>(c.data().byName.keySet());
            for (ServerPlayer p : c.onlinePlayers()) names.add(p.getScoreboardName());
            return SharedSuggestionProvider.suggest(names, b);
        };
        SuggestionProvider<CommandSourceStack> teams = (ctx, b) ->
            SharedSuggestionProvider.suggest(Teams.active(c.data().teamCount, c.data().disabledTeams), b);
        SuggestionProvider<CommandSourceStack> allTeams = (ctx, b) ->
            SharedSuggestionProvider.suggest(Teams.NAMES, b);
        SuggestionProvider<CommandSourceStack> tiers = (ctx, b) ->
            SharedSuggestionProvider.suggest(Tiers.NAMES, b);
        SuggestionProvider<CommandSourceStack> times = (ctx, b) ->
            SharedSuggestionProvider.suggest(List.of("30m", "1h", "6h", "1d", "7d"), b);
        SuggestionProvider<CommandSourceStack> counts = (ctx, b) -> {
            List<String> nums = new ArrayList<>();
            for (int i = 1; i <= Teams.NAMES.size(); i++) nums.add(String.valueOf(i));
            return SharedSuggestionProvider.suggest(nums, b);
        };
        Predicate<CommandSourceStack> admin = src -> Permissions.check(src, "moneysmp.admin", PermissionLevel.GAMEMASTERS);

        // every node runs the same handler with whatever was typed so far, so usage
        // messages and errors match the greedy fallback exactly
        LiteralArgumentBuilder<CommandSourceStack> root = literal("moneysmp")
            .executes(ctx -> c.moneysmp(ctx.getSource(), new String[0]))
            .then(literal("balance").executes(c.exec("balance"))
                .then(argument("player", word()).suggests(players).executes(c.exec("balance", "player"))))
            .then(literal("myteam").executes(c.exec("myteam")))
            .then(literal("teams").executes(c.exec("teams")))
            .then(literal("tiers").executes(c.exec("tiers")))
            .then(literal("bid").executes(c.exec("bid"))
                .then(argument("amount", word()).executes(c.exec("bid", "amount"))))
            .then(literal("reset").requires(admin).executes(c.exec("reset")))
            .then(literal("teambal").requires(admin).executes(c.exec("teambal")))
            .then(literal("randomteams").requires(admin).executes(c.exec("randomteams"))
                .then(argument("tier", word()).suggests(tiers).executes(c.exec("randomteams", "tier"))))
            .then(literal("auction").requires(admin).executes(c.exec("auction"))
                .then(literal("stop").executes(c.exec("auction stop"))))
            .then(literal("post-auction").requires(admin).executes(c.exec("post-auction")))
            .then(literal("tier").requires(admin).executes(c.exec("tier"))
                .then(literal("set").executes(c.exec("tier set"))
                    .then(argument("player", word()).suggests(players).executes(c.exec("tier set", "player"))
                        .then(argument("tier", word()).suggests(tiers).executes(c.exec("tier set", "player", "tier")))))
                .then(literal("clear").executes(c.exec("tier clear"))
                    .then(argument("player", word()).suggests(players).executes(c.exec("tier clear", "player")))))
            .then(literal("teammax").requires(admin).executes(c.exec("teammax"))
                .then(argument("number", word()).executes(c.exec("teammax", "number"))))
            .then(literal("teamcount").requires(admin).executes(c.exec("teamcount"))
                .then(argument("count", word()).suggests(counts).executes(c.exec("teamcount", "count"))))
            .then(literal("transaction").requires(admin).executes(c.exec("transaction"))
                .then(argument("time", word()).suggests(times).executes(c.exec("transaction", "time"))
                    .then(argument("page", word()).executes(c.exec("transaction", "time", "page")))))
            .then(literal("fine").requires(admin).executes(c.exec("fine"))
                .then(argument("player", word()).suggests(players).executes(c.exec("fine", "player"))
                    .then(argument("amount", word()).executes(c.exec("fine", "player", "amount"))
                        .then(argument("reason", greedyString()).executes(c.exec("fine", "player", "amount", "reason"))))))
            .then(literal("team").requires(admin).executes(c.exec("team"))
                .then(literal("set").executes(c.exec("team set"))
                    .then(argument("player", word()).suggests(players).executes(c.exec("team set", "player"))
                        .then(argument("team", word()).suggests(teams).executes(c.exec("team set", "player", "team")))))
                .then(literal("enable").executes(c.exec("team enable"))
                    .then(argument("team", word()).suggests(allTeams).executes(c.exec("team enable", "team"))))
                .then(literal("disable").executes(c.exec("team disable"))
                    .then(argument("team", word()).suggests(allTeams).executes(c.exec("team disable", "team"))))
                .then(literal("leader").executes(c.exec("team leader"))
                    .then(argument("player", word()).suggests(players).executes(c.exec("team leader", "player")))));

        for (String sub : new String[]{"give", "take", "set"}) {
            root.then(literal(sub).requires(admin).executes(c.exec(sub))
                .then(argument("player", word()).suggests(players).executes(c.exec(sub, "player"))
                    .then(argument("amount", word()).executes(c.exec(sub, "player", "amount")))));
        }

        // anything the tree doesn't know still lands in the handler ("Unknown subcommand", "No permission")
        root.then(argument("args", greedyString())
            .executes(ctx -> c.moneysmp(ctx.getSource(), StringArgumentType.getString(ctx, "args").trim().split("\s+"))));
        d.register(root);

        d.register(literal("pay")
            .then(argument("player", word()).suggests(players)
                .then(argument("amount", DoubleArgumentType.doubleArg())
                    .executes(ctx -> c.pay(ctx.getSource(),
                        StringArgumentType.getString(ctx, "player"),
                        DoubleArgumentType.getDouble(ctx, "amount"))))));

        d.register(literal("bid")
            .then(argument("amount", DoubleArgumentType.doubleArg())
                .executes(ctx -> c.bid(ctx.getSource(), DoubleArgumentType.getDouble(ctx, "amount")))));

        for (String name : new String[]{"balance", "bal"}) {
            d.register(literal(name)
                .executes(ctx -> c.balance(ctx.getSource(), null))
                .then(argument("player", word()).suggests(players)
                    .executes(ctx -> c.balance(ctx.getSource(), StringArgumentType.getString(ctx, "player")))));
        }
    }

    // literal words (space separated) followed by the named argument values, in order
    private Command<CommandSourceStack> exec(String literals, String... argNames) {
        return ctx -> {
            List<String> args = new ArrayList<>(List.of(literals.split(" ")));
            for (String n : argNames) {
                String v = StringArgumentType.getString(ctx, n);
                if (n.equals("reason")) args.addAll(List.of(v.trim().split("\s+")));
                else args.add(v);
            }
            return moneysmp(ctx.getSource(), args.toArray(new String[0]));
        };
    }

    private Data data() {
        return plugin.data;
    }

    private static void send(CommandSourceStack s, String msg) {
        s.sendSystemMessage(Fmt.c(msg));
    }

    private static void send(ServerPlayer p, String msg) {
        p.sendSystemMessage(Fmt.c(msg));
    }

    private void broadcast(String msg) {
        plugin.server.getPlayerList().broadcastSystemMessage(Fmt.c(msg), false);
    }

    private ServerPlayer online(UUID uid) {
        return plugin.server.getPlayerList().getPlayer(uid);
    }

    private List<ServerPlayer> onlinePlayers() {
        return plugin.server.getPlayerList().getPlayers();
    }

    private static Double num(String s) {
        try {
            double v = Double.parseDouble(s);
            return Double.isFinite(v) ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ── /pay ─────────────────────────────────────────────────────

    private int pay(CommandSourceStack s, String name, double amt) {
        ServerPlayer p = s.getPlayer();
        if (p == null) {
            send(s, Fmt.PREFIX + " &cPlayers only.");
            return 0;
        }
        if (amt <= 0) {
            send(s, Fmt.PREFIX + " &cAmount must be above 0.");
            return 0;
        }
        UUID target = data().lookup(name);
        if (target == null) {
            send(s, Fmt.PREFIX + " &cPlayer &f" + name + " &cnot found.");
            return 0;
        }
        if (target.equals(p.getUUID())) {
            send(s, Fmt.PREFIX + " &cYou cannot pay yourself.");
            return 0;
        }
        Data.PlayerData me = data().get(p);
        double locked = plugin.auction.committed(p.getUUID());
        if (me.money - locked < amt) {
            send(s, Fmt.PREFIX + " &cInsufficient funds! &7You have &e$" + Fmt.money(me.money) + "&7."
                + (locked > 0 ? " &e$" + Fmt.money(locked) + " &7of it is committed to your auction bid." : ""));
            return 0;
        }
        me.money -= amt;
        Data.PlayerData t = data().get(target);
        t.money += amt;
        data().log("PAY", p.getScoreboardName(), name, amt, "Player payment");
        send(s, Fmt.PREFIX + " &aYou paid &e$" + Fmt.money(amt) + " &ato &f" + name + "&a. Your balance: &e$" + Fmt.money(me.money));

        ServerPlayer tp = online(target);
        if (tp != null) {
            plugin.notify(target, "&a&l+ $" + Fmt.money(amt) + "  &7from &f" + p.getScoreboardName() + "  &8|  &a$ &e" + Fmt.money(t.money), 6);
            send(tp, Fmt.PREFIX + " &e" + p.getScoreboardName() + " &apaid you &e$" + Fmt.money(amt) + "&a! Balance: &e$" + Fmt.money(t.money));
        }
        return 1;
    }

    // ── /balance, /bal, /moneysmp balance ────────────────────────

    private int balance(CommandSourceStack s, String name) {
        if (name == null) {
            ServerPlayer p = s.getPlayer();
            if (p == null) {
                send(s, Fmt.PREFIX + " &cUsage: &f/balance <player>");
                return 0;
            }
            send(s, Fmt.PREFIX + " &7Your balance: &a&l$&e " + Fmt.money(data().money(p.getUUID())));
            return 1;
        }
        UUID uid = data().lookup(name);
        if (uid == null) {
            send(s, Fmt.PREFIX + " &cPlayer &f" + name + " &chas never joined.");
            return 0;
        }
        send(s, Fmt.PREFIX + " &e" + name + "&7's balance: &a&l$&e " + Fmt.money(data().money(uid)));
        return 1;
    }

    // ── /moneysmp ────────────────────────────────────────────────

    private int moneysmp(CommandSourceStack s, String[] args) {
        boolean admin = Permissions.check(s, "moneysmp.admin", PermissionLevel.GAMEMASTERS);

        if (args.length == 0 || args[0].isEmpty()) {
            help(s, admin);
            return 1;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "balance" -> balance(s, args.length > 1 ? args[1] : null);
            case "myteam" -> myteam(s);
            case "teams" -> teams(s);
            case "tiers" -> tiers(s);
            case "bid" -> {
                Double amt = args.length > 1 ? num(args[1]) : null;
                if (amt == null) send(s, Fmt.PREFIX + " &cUsage: &f/bid <amount>");
                else bid(s, amt);
            }
            default -> {
                if (!admin) {
                    if (ADMIN_SUBS.contains(sub)) {
                        send(s, Fmt.PREFIX + " &cNo permission.");
                    } else {
                        send(s, Fmt.PREFIX + " &cUnknown subcommand. Run &f/moneysmp &7for help.");
                    }
                    return 0;
                }
                if (plugin.auction.running && LOCKED_DURING_AUCTION.contains(sub)) {
                    send(s, Fmt.PREFIX + " &cAn auction is running. Stop it first with &f/moneysmp auction stop&c.");
                    return 0;
                }
                switch (sub) {
                    case "give" -> adjust(s, args, "give");
                    case "take" -> adjust(s, args, "take");
                    case "set" -> adjust(s, args, "set");
                    case "reset" -> reset(s);
                    case "fine" -> fine(s, args);
                    case "transaction" -> transaction(s, args);
                    case "teambal" -> teambal(s);
                    case "teammax" -> teammax(s, args);
                    case "teamcount" -> teamcount(s, args);
                    case "team" -> team(s, args);
                    case "randomteams" -> {
                        if (args.length > 1) randomteams(s, args[1]);
                        else randomteams(s);
                    }
                    case "tier" -> tier(s, args);
                    case "auction" -> auction(s, args);
                    case "post-auction" -> postAuction(s);
                    default -> send(s, Fmt.PREFIX + " &cUnknown subcommand. Run &f/moneysmp &7for help.");
                }
            }
        }
        return 1;
    }

    private void help(CommandSourceStack s, boolean admin) {
        send(s, "");
        send(s, LINE);
        send(s, "  " + Fmt.PREFIX + " &aCommand List");
        send(s, LINE);
        send(s, "  &f/moneysmp balance &8[player]");
        send(s, "  &f/moneysmp myteam");
        send(s, "  &f/moneysmp teams");
        send(s, "  &f/moneysmp tiers");
        send(s, "  &f/pay &e<player> <amount>");
        send(s, "  &f/bid &e<amount>  &8(during an auction)");
        if (admin) {
            send(s, "");
            send(s, "  &7&lAdmin Commands:");
            send(s, "  &f/moneysmp give &e<player> <amount>");
            send(s, "  &f/moneysmp take &e<player> <amount>");
            send(s, "  &f/moneysmp set &e<player> <amount>");
            send(s, "  &f/moneysmp reset");
            send(s, "  &f/moneysmp fine &e<player> <amount> <reason>");
            send(s, "  &f/moneysmp teambal");
            send(s, "  &f/moneysmp teammax &e<number>");
            send(s, "  &f/moneysmp teamcount &e<1-" + Teams.NAMES.size() + ">");
            send(s, "  &f/moneysmp randomteams &8[tier]");
            send(s, "  &f/moneysmp team set &e<player> <team>");
            send(s, "  &f/moneysmp team enable &e<team>");
            send(s, "  &f/moneysmp team disable &e<team>");
            send(s, "  &f/moneysmp team leader &e<player>");
            send(s, "  &f/moneysmp tier set &e<player> <S-F>");
            send(s, "  &f/moneysmp tier clear &e<player>");
            send(s, "  &f/moneysmp auction &8[stop]");
            send(s, "  &f/moneysmp post-auction");
            send(s, "  &f/moneysmp transaction &e<time> [page]  &8(e.g. 1h 30m 7d)");
            send(s, "");
            send(s, "  &7&lTeams &8(count: " + data().teamCount + "):");
            send(s, "  &c1 Red  &92 Blue  &53 Purple");
            send(s, "  &a4 Green  &f5 White  &66 Gold");
        }
        send(s, LINE);
        send(s, "");
    }

    private void myteam(CommandSourceStack s) {
        ServerPlayer p = s.getPlayer();
        if (p == null) {
            send(s, Fmt.PREFIX + " &cPlayers only.");
            return;
        }
        String t = data().team(p.getUUID());
        if (t == null) {
            send(s, Fmt.PREFIX + " &7You are not on a team.");
            return;
        }
        boolean leader = data().isLeader(p.getUUID(), t);
        send(s, Fmt.PREFIX + " &7Your team: " + Teams.color(t) + "&l" + t
            + (leader ? " &6(you're the leader — only you can bid)" : ""));
    }

    private void teams(CommandSourceStack s) {
        send(s, "");
        send(s, Fmt.PREFIX + " &7Active Teams  &8|  &7Count: &f" + data().teamCount);
        send(s, "");
        for (String t : Teams.active(data().teamCount, data().disabledTeams)) {
            String col = Teams.color(t);
            StringBuilder members = new StringBuilder();
            int count = 0;
            for (ServerPlayer p : onlinePlayers()) {
                if (!t.equals(data().team(p.getUUID()))) continue;
                count++;
                if (members.length() > 0) members.append("&7, ");
                boolean leader = data().isLeader(p.getUUID(), t);
                members.append(col).append(p.getScoreboardName()).append(leader ? "&6★" : "");
            }
            if (count > 0) {
                send(s, "  " + col + "&l" + t + " &8(" + count + ")  &8»  " + members);
            } else {
                send(s, "  " + col + "&l" + t + " &8(0)  &8»  &7Empty");
            }
        }
        if (!data().disabledTeams.isEmpty()) {
            send(s, "");
            StringBuilder names = new StringBuilder();
            for (String t : data().disabledTeams) names.append(Teams.color(t)).append(t).append("&7, ");
            names.setLength(names.length() - 2);
            send(s, "  &7Disabled: " + names);
        }
        send(s, "");
    }

    // give / take / set share the same shape
    private void adjust(CommandSourceStack s, String[] args, String mode) {
        if (args.length < 3) {
            send(s, Fmt.PREFIX + " &cUsage: &f/moneysmp " + mode + " <player> <amount>");
            return;
        }
        String name = args[1];
        Double amt = num(args[2]);
        if (amt == null) {
            send(s, Fmt.PREFIX + " &cInvalid amount.");
            return;
        }
        if (mode.equals("set") ? amt < 0 : amt <= 0) {
            send(s, Fmt.PREFIX + (mode.equals("set") ? " &cCannot set negative balance." : " &cAmount must be above 0."));
            return;
        }
        UUID uid = data().resolve(name);
        if (uid == null) {
            send(s, Fmt.PREFIX + " &cPlayer &f" + name + " &cnot found.");
            return;
        }
        Data.PlayerData pd = data().get(uid);
        ServerPlayer target = online(uid);
        String a = Fmt.money(amt);
        String who = s.getTextName();
        switch (mode) {
            case "give" -> {
                pd.money += amt;
                data().log("GIVE", who, name, amt, "Admin give");
                send(s, Fmt.PREFIX + " &aGave &e$" + a + " &ato &f" + name + "&a. Balance: &e$" + Fmt.money(pd.money));
                if (target != null) send(target, Fmt.PREFIX + " &aYou received &e$" + a + "&a! Balance: &e$" + Fmt.money(pd.money));
            }
            case "take" -> {
                pd.money -= amt;
                data().log("TAKE", who, name, amt, "Admin take");
                send(s, Fmt.PREFIX + " &cTook &e$" + a + " &cfrom &f" + name + "&c. Balance: &e$" + Fmt.money(pd.money));
                if (target != null) send(target, Fmt.PREFIX + " &c$" + a + " &cdeducted. Balance: &e$" + Fmt.money(pd.money));
            }
            case "set" -> {
                pd.money = amt;
                data().log("SET", who, name, amt, "Admin set balance");
                send(s, Fmt.PREFIX + " &aSet &f" + name + "&a's balance to &e$" + a);
                if (target != null) send(target, Fmt.PREFIX + " &7Your balance was set to &e$" + a);
            }
        }
    }

    private void reset(CommandSourceStack s) {
        int count = 0;
        for (Data.PlayerData pd : data().players.values()) {
            pd.money = 100;
            pd.team = null;
            count++;
        }
        data().teamLeaders.clear();
        for (ServerPlayer p : onlinePlayers()) plugin.sync(p);
        data().log("RESET", s.getTextName(), "ALL PLAYERS", 100, "Mass balance and teams reset");
        send(s, "");
        send(s, Fmt.PREFIX + " &a&lFull Reset! &7Reset &e" + count + " &7players (including offline) to &a&l$100 &7and cleared all teams.");
        send(s, "");
        broadcast(Fmt.PREFIX + " &aAll balances reset to &l$100 &aand all teams have been cleared!");
    }

    private void fine(CommandSourceStack s, String[] args) {
        if (args.length < 4) {
            send(s, Fmt.PREFIX + " &cUsage: &f/moneysmp fine <player> <amount> <reason>");
            return;
        }
        String name = args[1];
        Double amt = num(args[2]);
        if (amt == null) {
            send(s, Fmt.PREFIX + " &cInvalid amount.");
            return;
        }
        String reason = String.join(" ", List.of(args).subList(3, args.length));
        if (amt <= 0) {
            send(s, Fmt.PREFIX + " &cFine must be above 0.");
            return;
        }
        UUID uid = data().resolve(name);
        if (uid == null) {
            send(s, Fmt.PREFIX + " &cPlayer &f" + name + " &cnot found.");
            return;
        }
        Data.PlayerData pd = data().get(uid);
        pd.money -= amt;
        data().log("FINE", s.getTextName(), name, amt, reason);
        broadcast("");
        broadcast(Fmt.PREFIX + " &c&l⚠ FINE ISSUED ⚠");
        broadcast("  &7Player: &f" + name);
        broadcast("  &7Amount: &c-$" + Fmt.money(amt));
        broadcast("  &7Reason: &f" + reason);
        broadcast("  &7New Balance: &e$" + Fmt.money(pd.money));
        broadcast("");
        ServerPlayer target = online(uid);
        if (target != null) {
            plugin.notify(uid, "&c&l- $" + Fmt.money(amt) + "  &7Fine: &f" + reason + "  &8|  &a$ &e" + Fmt.money(pd.money), 8);
            send(target, Fmt.PREFIX + " &c&lYou were fined &e$" + Fmt.money(amt) + "&c! Reason: &f" + reason);
        }
    }

    private void transaction(CommandSourceStack s, String[] args) {
        if (args.length < 2) {
            send(s, Fmt.PREFIX + " &cUsage: &f/moneysmp transaction <time> [page]  &7e.g. &f30m 2h 1d");
            return;
        }
        String raw = args[1];
        long secs = Fmt.parseTime(raw);
        if (secs <= 0) {
            send(s, Fmt.PREFIX + " &cInvalid time. &7Use: &f30s &7/ &f10m &7/ &f2h &7/ &f1d");
            return;
        }
        if (data().transactions.isEmpty()) {
            send(s, Fmt.PREFIX + " &7No transactions recorded yet.");
            return;
        }
        int page = 1;
        try {
            if (args.length > 2) page = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            send(s, Fmt.PREFIX + " &cInvalid page.");
            return;
        }
        long now = System.currentTimeMillis();
        long oldest = secs >= now / 1000 ? Long.MIN_VALUE : now - (secs + 1) * 1000 + 1;
        int first = data().firstTransaction(oldest);
        List<Data.Tx> txs = data().transactions;
        int total = txs.size() - first;
        int pages = Math.max(1, (int) ((total + 49L) / 50));
        if (page < 1 || page > pages) {
            send(s, Fmt.PREFIX + " &cPage must be between 1 and " + pages + ".");
            return;
        }
        send(s, "");
        send(s, LINE);
        send(s, "  " + Fmt.PREFIX + " &aTransaction Log  &8|  &7Last &f" + raw);
        send(s, LINE);
        send(s, "  &8Type legend: &aPAY &6KILL &cFINE &bGIVE &4TAKE &5SET &8RESET");
        send(s, LINE);

        int found = 0;
        int start = txs.size() - 1 - (page - 1) * 50;
        for (int i = start; i >= first && found < 50; i--) {
            Data.Tx tx = txs.get(i);
            long ago = (now - tx.stamp()) / 1000;
            String col = switch (tx.type()) {
                case "PAY" -> "&a";
                case "KILL" -> "&6";
                case "FINE" -> "&c";
                case "GIVE" -> "&b";
                case "TAKE" -> "&4";
                case "SET" -> "&5";
                case "RESET", "AUCTION_RESET", "AUCTION_SPLIT" -> "&8";
                default -> "&7";
            };
            send(s, "  " + col + "&l[" + tx.type() + "]  &8" + Fmt.timeAgo(ago) + " ago  &8|  &e$" + Fmt.money(tx.amount()));
            send(s, "    &7From: &f" + tx.from() + "  &8➜  &7To: &f" + tx.to());
            if (!tx.note().isEmpty()) send(s, "    &7Note: &f" + tx.note());
            found++;
        }

        send(s, LINE);
        if (found == 0) {
            send(s, "  &7No transactions found in the last &f" + raw + "&7.");
        } else {
            send(s, "  &7Showing &f" + found + " &7of &f" + total + " &7transaction(s). Page &f" + page + "/" + pages);
            if (page < pages) send(s, "  &7Next: &f/moneysmp transaction " + raw + " " + (page + 1));
        }
        send(s, LINE);
        send(s, "");
    }

    private void teambal(CommandSourceStack s) {
        send(s, "");
        send(s, Fmt.PREFIX + " &a&l⚔ Team Balances ⚔");
        send(s, "");
        for (String t : Teams.active(data().teamCount, data().disabledTeams)) {
            String col = Teams.color(t);
            double total = 0;
            int count = 0;
            send(s, "  " + col + "&l" + t + "&8:");
            for (Map.Entry<UUID, Data.PlayerData> e : data().players.entrySet()) {
                Data.PlayerData pd = e.getValue();
                if (!t.equals(pd.team)) continue;
                total += pd.money;
                count++;
                String tag = online(e.getKey()) != null ? "&a(online)" : "&8(offline)";
                String leadTag = data().isLeader(e.getKey(), t) ? " &6★leader" : "";
                send(s, "    " + col + pd.name + " " + tag + leadTag + "  &8»  &e$" + Fmt.money(pd.money));
            }
            if (count == 0) {
                send(s, "    &8No players assigned.");
            } else {
                send(s, "  &7Total: &a&l$" + Fmt.money(total) + "  &8|  &7Members: &f" + count);
            }
            send(s, "");
        }
    }

    private void teammax(CommandSourceStack s, String[] args) {
        if (args.length < 2) {
            send(s, Fmt.PREFIX + " &cUsage: &f/moneysmp teammax <number>");
            return;
        }
        Double n = num(args[1]);
        if (n == null) {
            send(s, Fmt.PREFIX + " &cInvalid number.");
            return;
        }
        if (n <= 0) {
            send(s, Fmt.PREFIX + " &cMust be at least 1.");
            return;
        }
        data().teamMax = n.intValue();
        send(s, Fmt.PREFIX + " &aMax per team: &e&l" + data().teamMax + "  &8|  &7Capacity: &f" + (data().teamMax * data().teamCount) + " &8(" + data().teamCount + " teams)");
    }

    private void teamcount(CommandSourceStack s, String[] args) {
        if (args.length < 2) {
            send(s, Fmt.PREFIX + " &cUsage: &f/moneysmp teamcount <1-" + Teams.NAMES.size() + ">");
            return;
        }
        Double n = num(args[1]);
        if (n == null) {
            send(s, Fmt.PREFIX + " &cInvalid number.");
            return;
        }
        if (n < 1) {
            send(s, Fmt.PREFIX + " &cMinimum is &e1&c.");
            return;
        }
        if (n > Teams.NAMES.size()) {
            send(s, Fmt.PREFIX + " &cMaximum is &e" + Teams.NAMES.size() + "&c.");
            return;
        }
        data().teamCount = n.intValue();
        data().teamCountSet = true;
        send(s, "");
        send(s, Fmt.PREFIX + " &aTeam count: &e&l" + data().teamCount + "&a. Active teams:");
        int i = 1;
        for (String t : Teams.active(data().teamCount, data().disabledTeams)) {
            send(s, "    " + Teams.color(t) + "&l" + (i++) + ". " + t);
        }
        send(s, "");
    }

    private void team(CommandSourceStack s, String[] args) {
        if (args.length < 2) {
            send(s, Fmt.PREFIX + " &cUsage: &f/moneysmp team <set|enable|disable|leader> ...");
            return;
        }
        switch (args[1].toLowerCase()) {
            case "set" -> teamSet(s, args);
            case "enable" -> teamToggle(s, args, true);
            case "disable" -> teamToggle(s, args, false);
            case "leader" -> teamLeader(s, args);
            default -> send(s, Fmt.PREFIX + " &cUsage: &f/moneysmp team <set|enable|disable|leader> ...");
        }
    }

    // manual override for who bids on a team's behalf, in case the automatic
    // first-assigned pick isn't who you want
    private void teamLeader(CommandSourceStack s, String[] args) {
        if (args.length < 3) {
            send(s, Fmt.PREFIX + " &cUsage: &f/moneysmp team leader <player>");
            return;
        }
        String name = args[2];
        UUID uid = data().resolve(name);
        if (uid == null) {
            send(s, Fmt.PREFIX + " &cPlayer &f" + name + " &cnot found.");
            return;
        }
        Data.PlayerData pd = data().get(uid);
        if (pd.team == null) {
            send(s, Fmt.PREFIX + " &f" + pd.name + " &cisn't on a team.");
            return;
        }
        data().teamLeaders.put(pd.team, uid);
        send(s, Fmt.PREFIX + " &aSet &f" + pd.name + " &aas the leader of team " + Teams.color(pd.team) + "&l" + pd.team + "&a.");
        ServerPlayer target = online(uid);
        if (target != null) {
            send(target, Fmt.PREFIX + " &7You are now the leader of team " + Teams.color(pd.team) + "&l" + pd.team + "&7. You're the only one who can bid for it.");
        }
    }

    private void teamToggle(CommandSourceStack s, String[] args, boolean enable) {
        if (args.length < 3) {
            send(s, Fmt.PREFIX + " &cUsage: &f/moneysmp team " + (enable ? "enable" : "disable") + " <team>");
            return;
        }
        String team = Teams.normalise(args[2]);
        if (!Teams.NAMES.contains(team)) {
            send(s, Fmt.PREFIX + " &cInvalid team &f" + args[2] + "&c. &7Teams: &cRed &9Blue &5Purple &aGreen &fWhite &6Gold");
            return;
        }
        if (enable) {
            if (!data().disabledTeams.remove(team)) {
                send(s, Fmt.PREFIX + " &7Team " + Teams.color(team) + team + " &7is already enabled.");
                return;
            }
            send(s, Fmt.PREFIX + " &aEnabled team " + Teams.color(team) + "&l" + team + "&a.");
            return;
        }
        if (!data().disabledTeams.add(team)) {
            send(s, Fmt.PREFIX + " &7Team " + Teams.color(team) + team + " &7is already disabled.");
            return;
        }
        List<UUID> cleared = new ArrayList<>();
        for (Map.Entry<UUID, Data.PlayerData> e : data().players.entrySet()) {
            if (team.equals(e.getValue().team)) cleared.add(e.getKey());
        }
        data().clearLeader(team);
        for (UUID uid : cleared) {
            data().get(uid).team = null;
            ServerPlayer p = online(uid);
            if (p != null) {
                plugin.sync(p);
                send(p, Fmt.PREFIX + " &7Team " + Teams.color(team) + team + " &7was disabled; you were removed from it.");
            }
        }
        send(s, Fmt.PREFIX + " &cDisabled team " + Teams.color(team) + "&l" + team + "&c."
            + (cleared.isEmpty() ? "" : " &7Removed &f" + cleared.size() + " &7player(s) from it."));
    }

    private void teamSet(CommandSourceStack s, String[] args) {
        if (args.length < 3) {
            send(s, Fmt.PREFIX + " &cUsage: &f/moneysmp team set <player> <team>");
            return;
        }
        if (args.length < 4) {
            send(s, Fmt.PREFIX + " &cUsage: &f/moneysmp team set <player> <team>");
            send(s, "  &7Teams: &cRed &9Blue &5Purple &aGreen &fWhite &6Gold");
            return;
        }
        String name = args[2];
        String newTeam = Teams.normalise(args[3]);
        List<String> active = Teams.active(data().teamCount, data().disabledTeams);
        if (!active.contains(newTeam)) {
            send(s, Fmt.PREFIX + " &cInvalid team &f" + newTeam + "&c. Active teams:");
            for (String t : active) send(s, "  " + Teams.color(t) + "&l" + t);
            return;
        }
        UUID uid = data().resolve(name);
        if (uid == null) {
            send(s, Fmt.PREFIX + " &cPlayer &f" + name + " &cnot found.");
            return;
        }
        Data.PlayerData pd = data().get(uid);
        if (pd.tier != null) {
            Data.PlayerData held = data().teamMember(newTeam, pd.tier);
            if (held != null && held != pd) {
                send(s, Fmt.PREFIX + " &cTeam " + Teams.color(newTeam) + newTeam + " &calready has a tier " + Tiers.color(pd.tier) + pd.tier + " &cplayer: &f" + held.name);
                return;
            }
        }
        data().assignTeam(uid, newTeam);
        ServerPlayer target = online(uid);
        if (target != null) {
            plugin.sync(target);
            send(target, Fmt.PREFIX + " &7You have been moved to team " + Teams.color(newTeam) + "&l" + newTeam);
        }
        send(s, Fmt.PREFIX + " &aAssigned &f" + name + " &ato team " + Teams.color(newTeam) + "&l" + newTeam + "&a.");
    }

    // assigns every known player, offline included, then disables any team that ended up empty
    private void randomteams(CommandSourceStack s) {
        if (data().teamMax == null) {
            send(s, Fmt.PREFIX + " &cRun &f/moneysmp teammax <n> &cfirst.");
            return;
        }
        int max = data().teamMax;
        List<String> active = new ArrayList<>(Teams.active(data().teamCount, data().disabledTeams));
        if (active.isEmpty()) {
            send(s, Fmt.PREFIX + " &cNo teams are enabled. Use &f/moneysmp team enable <team>&c.");
            return;
        }
        List<UUID> pool = new ArrayList<>(data().players.keySet());
        int cap = max * active.size();
        if (pool.size() > cap) {
            send(s, Fmt.PREFIX + " &cToo many players! Capacity: &f" + cap + " &8(" + active.size() + " x " + max + ")&7. Players: &f" + pool.size());
            return;
        }
        for (Data.PlayerData pd : data().players.values()) {
            if (active.contains(pd.team)) pd.team = null;
        }
        for (String t : active) data().clearLeader(t);

        int[] counts = new int[active.size()];
        Collections.shuffle(pool);
        for (UUID uid : pool) {
            List<Integer> open = new ArrayList<>();
            for (int i = 0; i < active.size(); i++) if (counts[i] < max) open.add(i);
            int ti = open.get(ThreadLocalRandom.current().nextInt(open.size()));
            counts[ti]++;
            data().assignTeam(uid, active.get(ti));
        }

        broadcast("");
        broadcast(Fmt.PREFIX + " &a&l⚔  TEAMS HAVE BEEN RANDOMISED  ⚔");
        broadcast("");
        for (UUID uid : pool) {
            Data.PlayerData pd = data().get(uid);
            String col = Teams.color(pd.team);
            ServerPlayer p = online(uid);
            String tag = p != null ? "&a(online)" : "&8(offline)";
            broadcast("  " + col + "&l" + pd.name + " " + tag + "  &8»  " + col + pd.team);
            if (p != null) {
                plugin.sync(p);
                send(p, Fmt.PREFIX + " &7You are on team " + col + "&l" + pd.team);
            }
        }
        broadcast("");

        List<String> emptied = new ArrayList<>();
        for (int i = 0; i < active.size(); i++) if (counts[i] == 0) emptied.add(active.get(i));
        if (!emptied.isEmpty()) {
            data().disabledTeams.addAll(emptied);
            StringBuilder names = new StringBuilder();
            for (String t : emptied) names.append(Teams.color(t)).append(t).append("&7, ");
            names.setLength(names.length() - 2);
            broadcast(Fmt.PREFIX + " &7Disabled empty team(s): " + names);
        }
    }

    // every player of one tier onto a different team each, offline included.
    // their old teams are dropped first so nobody ends up doubled
    private void randomteams(CommandSourceStack s, String rawTier) {
        String tier = Tiers.normalise(rawTier);
        if (tier == null) {
            send(s, Fmt.PREFIX + " &cInvalid tier. &7Use one of: &fS A B C D E F");
            return;
        }
        List<UUID> pool = new ArrayList<>();
        for (Map.Entry<UUID, Data.PlayerData> e : data().players.entrySet()) if (tier.equals(e.getValue().tier)) pool.add(e.getKey());
        if (pool.isEmpty()) {
            send(s, Fmt.PREFIX + " &cNobody is in tier " + Tiers.color(tier) + tier + "&c.");
            return;
        }
        List<String> active = new ArrayList<>(Teams.active(data().teamCount, data().disabledTeams));
        if (pool.size() > active.size()) {
            send(s, Fmt.PREFIX + " &cTier " + Tiers.color(tier) + tier + " &chas &f" + pool.size() + " &cplayers but there are only &f" + active.size() + " &cteams.");
            return;
        }
        Collections.shuffle(pool);
        Collections.shuffle(active);
        for (int i = 0; i < pool.size(); i++) data().assignTeam(pool.get(i), active.get(i));

        broadcast("");
        broadcast(Fmt.PREFIX + " &a&l⚔  TIER " + Tiers.color(tier) + tier + " &a&lTEAMS ASSIGNED  ⚔");
        broadcast("");
        for (UUID uid : pool) {
            Data.PlayerData pd = data().get(uid);
            String col = Teams.color(pd.team);
            broadcast("  " + col + "&l" + pd.name + "  &8»  " + col + pd.team);
            ServerPlayer p = online(uid);
            if (p != null) {
                plugin.sync(p);
                send(p, Fmt.PREFIX + " &7You are on team " + col + "&l" + pd.team);
            }
        }
        broadcast("");
    }

    private void tiers(CommandSourceStack s) {
        send(s, "");
        send(s, Fmt.PREFIX + " &7Tiers");
        send(s, "");
        for (String tier : Tiers.NAMES) {
            String col = Tiers.color(tier);
            StringBuilder members = new StringBuilder();
            int count = 0;
            for (Data.PlayerData pd : data().players.values()) {
                if (!tier.equals(pd.tier)) continue;
                count++;
                if (members.length() > 0) members.append("&7, ");
                members.append(pd.team == null ? "&f" : Teams.color(pd.team)).append(pd.name);
            }
            send(s, "  " + col + "&l" + tier + " &8(" + count + ")  &8»  " + (count == 0 ? "&7Empty" : members));
        }
        send(s, "");
    }

    private void tier(CommandSourceStack s, String[] args) {
        String usage = Fmt.PREFIX + " &cUsage: &f/moneysmp tier set <player> <S-F> &7or &f/moneysmp tier clear <player>";
        if (args.length < 3) {
            send(s, usage);
            return;
        }
        String name = args[2];
        String tier = null;
        if (args[1].equalsIgnoreCase("set")) {
            if (args.length < 4 || (tier = Tiers.normalise(args[3])) == null) {
                send(s, Fmt.PREFIX + " &cInvalid tier. &7Use one of: &fS A B C D E F");
                return;
            }
        } else if (!args[1].equalsIgnoreCase("clear")) {
            send(s, usage);
            return;
        }
        UUID uid = data().resolve(name);
        if (uid == null) {
            send(s, Fmt.PREFIX + " &cPlayer &f" + name + " &cnot found.");
            return;
        }
        Data.PlayerData pd = data().get(uid);
        if (tier != null && pd.team != null) {
            Data.PlayerData held = data().teamMember(pd.team, tier);
            if (held != null && held != pd) {
                send(s, Fmt.PREFIX + " &cTeam " + Teams.color(pd.team) + pd.team + " &calready has a tier " + Tiers.color(tier) + tier + " &cplayer: &f" + held.name);
                return;
            }
        }
        pd.tier = tier;
        ServerPlayer target = online(uid);
        if (target != null) plugin.sync(target);
        if (tier == null) {
            send(s, Fmt.PREFIX + " &aCleared &f" + pd.name + "&a's tier.");
            if (target != null) send(target, Fmt.PREFIX + " &7Your tier was cleared.");
        } else {
            send(s, Fmt.PREFIX + " &aSet &f" + pd.name + " &ato tier " + Tiers.color(tier) + "&l" + tier + "&a.");
            if (target != null) send(target, Fmt.PREFIX + " &7You are now tier " + Tiers.color(tier) + "&l" + tier);
        }
    }

    private void auction(CommandSourceStack s, String[] args) {
        if (args.length > 1 && args[1].equalsIgnoreCase("stop")) {
            if (!plugin.auction.running) send(s, Fmt.PREFIX + " &cNo auction is running.");
            else plugin.auction.stop();
            return;
        }
        String why = plugin.auction.check();
        if (why != null) {
            send(s, Fmt.PREFIX + " " + why);
            return;
        }
        plugin.auction.start();
    }

    // skips a live bidding auction and jumps straight to its end state: only leaders keep
    // money, then teams split evenly. requires teams already set up as an auction would leave them
    private void postAuction(CommandSourceStack s) {
        String why = plugin.auction.postAuctionCheck();
        if (why != null) {
            send(s, Fmt.PREFIX + " " + why);
            return;
        }
        plugin.auction.postAuction();
    }

    private int bid(CommandSourceStack s, double amt) {
        ServerPlayer p = s.getPlayer();
        if (p == null) {
            send(s, Fmt.PREFIX + " &cPlayers only.");
            return 0;
        }
        plugin.auction.bid(p, amt);
        return 1;
    }
}
