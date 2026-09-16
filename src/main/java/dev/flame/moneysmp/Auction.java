package dev.flame.moneysmp;

import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

// walks tiers from F up to S, auctioning every player who still has no team.
// ticked once a second by MoneySMP while running
final class Auction {
    private static final int PAUSE = 5;

    private final MoneySMP plugin;
    boolean running;
    UUID player;
    String tier;
    double bid;
    UUID bidder;
    private int secondsLeft;
    private int pause;

    Auction(MoneySMP plugin) {
        this.plugin = plugin;
    }

    private Data data() {
        return plugin.data;
    }

    private void broadcast(String msg) {
        plugin.server.getPlayerList().broadcastSystemMessage(Fmt.c(msg), false);
    }

    // null when the auction can begin, otherwise the reason it cannot
    String check() {
        if (running) return "&cAn auction is already running.";
        int[] counts = new int[Tiers.NAMES.size()];
        int teamed = 0;
        boolean pending = false;
        for (Data.PlayerData pd : data().players.values()) {
            if (pd.tier == null) continue;
            counts[Tiers.NAMES.indexOf(pd.tier)]++;
            if (pd.team != null) teamed++;
            else pending = true;
        }
        int size = -1;
        StringBuilder summary = new StringBuilder();
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] == 0) continue;
            if (size == -1) size = counts[i];
            else if (counts[i] != size) size = -2;
            summary.append("  ").append(Tiers.color(Tiers.NAMES.get(i))).append(Tiers.NAMES.get(i)).append("&7:").append(counts[i]);
        }
        if (size == -1) return "&cNo players have a tier yet. Use &f/moneysmp tier set <player> <tier>&c.";
        if (size == -2) return "&cEvery tier in use must have the same number of players (unused tiers are skipped).&r" + summary;
        if (size > data().teamCount) return "&cEach tier has &f" + size + " &cplayers but there are only &f" + data().teamCount + " &cteams. Raise &f/moneysmp teamcount&c.";
        if (teamed == 0) return "&cNo team has a player yet. Use &f/moneysmp randomteams <tier> &cfirst.";
        if (!pending) return "&cEveryone with a tier already has a team.";
        for (String t : Teams.active(data().teamCount)) {
            double total = 0;
            int members = 0;
            for (Data.PlayerData pd : data().players.values()) {
                if (!t.equals(pd.team)) continue;
                total += pd.money;
                members++;
            }
            if (members == 0) return "&cTeam " + Teams.color(t) + t + " &chas no players, so it could never bid. Use &f/moneysmp randomteams <tier>&c.";
            double need = reserve(t, null);
            if (total < need) return "&cTeam " + Teams.color(t) + t + " &conly has &e$" + Fmt.money(total) + " &cbut needs &e$" + Fmt.money(need) + " &cto cover the minimums of the tiers it still lacks.";
        }
        return null;
    }

    // minimums a team must still be able to pay: one per tier it lacks that still has
    // teamless players, skipping the tier on the block right now
    private double reserve(String team, String skip) {
        double sum = 0;
        for (String t : Tiers.NAMES) {
            if (t.equals(skip) || data().teamMember(team, t) != null) continue;
            for (Data.PlayerData pd : data().players.values()) {
                if (pd.team == null && t.equals(pd.tier)) {
                    sum += plugin.config.minimum(t);
                    break;
                }
            }
        }
        return sum;
    }

    void start() {
        running = true;
        broadcast("");
        broadcast(Fmt.PREFIX + " &6&l⚒  THE AUCTION HAS BEGUN  ⚒");
        broadcast("  &7Bid with &f/bid <amount>&7. Only players on a team can bid, and a team holds one player per tier.");
        broadcast("");
        next();
    }

    void stop() {
        running = false;
        player = null;
        bidder = null;
        broadcast(Fmt.PREFIX + " &cThe auction was stopped.");
    }

    private void next() {
        List<UUID> pool = new ArrayList<>();
        tier = null;
        for (int i = Tiers.NAMES.size() - 1; i >= 0 && pool.isEmpty(); i--) {
            for (Map.Entry<UUID, Data.PlayerData> e : data().players.entrySet()) {
                Data.PlayerData pd = e.getValue();
                if (pd.team == null && Tiers.NAMES.get(i).equals(pd.tier)) pool.add(e.getKey());
            }
            if (!pool.isEmpty()) tier = Tiers.NAMES.get(i);
        }
        if (pool.isEmpty()) {
            running = false;
            player = null;
            broadcast("");
            broadcast(Fmt.PREFIX + " &a&lAuction complete! &7Every tiered player has a team. See &f/moneysmp teams&7.");
            broadcast("");
            return;
        }
        player = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        bidder = null;
        bid = plugin.config.minimum(tier);
        secondsLeft = plugin.config.bidSeconds;
        String name = data().get(player).name;
        broadcast("");
        broadcast(Fmt.PREFIX + " &6&lNOW UP: " + Tiers.prefix(tier) + "&f&l" + name + "  &8(" + pool.size() + " left in tier " + Tiers.color(tier) + tier + "&8)");
        broadcast("  &7Starting at &e$" + Fmt.money(bid) + "&7, at least &e$" + Fmt.money(plugin.config.minBidIncrease) + " &7per raise, &f" + secondsLeft + "s &7on the clock.");
        broadcast("");
    }

    void bid(ServerPlayer p, double amt) {
        if (!running || player == null) {
            p.sendSystemMessage(Fmt.p("&cThere is no auction running."));
            return;
        }
        Data.PlayerData me = data().get(p);
        if (me.team == null) {
            p.sendSystemMessage(Fmt.p("&cYou need to be on a team to bid."));
            return;
        }
        Data.PlayerData held = data().teamMember(me.team, tier);
        if (held != null) {
            p.sendSystemMessage(Fmt.p("&cYour team already has a tier " + Tiers.color(tier) + tier + " &cplayer: &f" + held.name));
            return;
        }
        if (p.getUUID().equals(bidder)) {
            p.sendSystemMessage(Fmt.p("&cYou already hold the highest bid."));
            return;
        }
        double floor = bidder == null ? bid : bid + plugin.config.minBidIncrease;
        if (amt < floor) {
            p.sendSystemMessage(Fmt.p("&cYou must bid at least &e$" + Fmt.money(floor) + "&c."));
            return;
        }
        if (amt > me.money) {
            p.sendSystemMessage(Fmt.p("&cInsufficient funds! &7You have &e$" + Fmt.money(me.money) + "&7."));
            return;
        }
        double keep = reserve(me.team, tier);
        if (amt > me.money - keep) {
            p.sendSystemMessage(Fmt.p("&cYou must keep &e$" + Fmt.money(keep) + " &cfor the minimums of the tiers your team still needs. Max bid: &e$" + Fmt.money(me.money - keep)));
            return;
        }
        bid = amt;
        bidder = p.getUUID();
        secondsLeft = plugin.config.bidSeconds;
        broadcast(Fmt.PREFIX + " " + Teams.color(me.team) + "&l" + p.getScoreboardName() + " &7bids &e&l$" + Fmt.money(amt) + " &7for &f" + data().get(player).name + " &8(" + Teams.color(me.team) + me.team + "&8)");
    }

    void tick() {
        if (!running) return;
        if (player == null) {
            if (--pause <= 0) next();
            return;
        }
        secondsLeft--;
        if (secondsLeft > 0) {
            if (secondsLeft <= 3 || secondsLeft == 5 || secondsLeft == 10) {
                broadcast(Fmt.PREFIX + " &7" + (bidder == null ? "No bids yet" : "Going once at &e$" + Fmt.money(bid)) + "&7... &f" + secondsLeft + "s");
            }
            return;
        }
        sell();
    }

    private void sell() {
        Data.PlayerData sold = data().get(player);
        String name = sold.name;
        if (bidder == null) {
            List<String> open = new ArrayList<>();
            for (String t : Teams.active(data().teamCount)) if (data().teamMember(t, tier) == null) open.add(t);
            if (open.isEmpty()) {
                running = false;
                player = null;
                broadcast(Fmt.PREFIX + " &cNo team has room for a tier " + tier + " player. Auction stopped; fix teams and run &f/moneysmp auction &cagain.");
                return;
            }
            sold.team = open.get(ThreadLocalRandom.current().nextInt(open.size()));
            // the team still pays the minimum, richest member first
            List<Map.Entry<UUID, Data.PlayerData>> members = new ArrayList<>();
            for (Map.Entry<UUID, Data.PlayerData> e : data().players.entrySet()) if (sold.team.equals(e.getValue().team)) members.add(e);
            members.sort((a, b) -> Double.compare(b.getValue().money, a.getValue().money));
            double left = bid;
            for (int i = 0; i < members.size() && left > 0; i++) {
                Data.PlayerData m = members.get(i).getValue();
                double part = i == members.size() - 1 ? left : Math.min(left, Math.max(0, m.money));
                if (part <= 0) continue;
                m.money -= part;
                left -= part;
                data().log("BID", m.name, name, part, "Auction minimum, no bids (tier " + tier + ")");
                if (plugin.server.getPlayerList().getPlayer(members.get(i).getKey()) != null) {
                    plugin.notify(members.get(i).getKey(), "&c&l- $" + Fmt.money(part) + "  &7" + name + " joined at minimum  &8|  &a$ &e" + Fmt.money(m.money), 6);
                }
            }
            broadcast(Fmt.PREFIX + " &7No bids for &f" + name + "&7. Placed on team " + Teams.color(sold.team) + "&l" + sold.team + " &7for the minimum &e$" + Fmt.money(bid) + "&7.");
        } else {
            Data.PlayerData winner = data().get(bidder);
            winner.money -= bid;
            sold.team = winner.team;
            data().log("BID", winner.name, name, bid, "Auction win (tier " + tier + ")");
            broadcast(Fmt.PREFIX + " &a&lSOLD! &f" + name + " &7joins team " + Teams.color(sold.team) + "&l" + sold.team + " &7for &e$" + Fmt.money(bid) + " &8(" + winner.name + ")");
            if (plugin.server.getPlayerList().getPlayer(bidder) != null) {
                plugin.notify(bidder, "&c&l- $" + Fmt.money(bid) + "  &7Won " + name + "  &8|  &a$ &e" + Fmt.money(winner.money), 6);
            }
        }
        ServerPlayer p = plugin.server.getPlayerList().getPlayer(player);
        if (p != null) {
            plugin.sync(p);
            p.sendSystemMessage(Fmt.p("&7You are now on team " + Teams.color(sold.team) + "&l" + sold.team));
        }
        player = null;
        bidder = null;
        pause = PAUSE;
    }
}
