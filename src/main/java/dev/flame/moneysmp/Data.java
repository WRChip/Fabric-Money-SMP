package dev.flame.moneysmp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class Data {
    record Tx(long stamp, String type, String from, String to, double amount, String note) {}

    static final class PlayerData {
        String name;
        double money;
        String team;
        String tier;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    final Map<UUID, PlayerData> players = new HashMap<>();
    final Map<String, UUID> byName = new HashMap<>();
    final List<Tx> transactions = new ArrayList<>();
    // all teams unless an admin ran /moneysmp teamcount; only then is it persisted
    int teamCount = Teams.NAMES.size();
    boolean teamCountSet;
    Integer teamMax;
    final Set<String> disabledTeams = new HashSet<>();
    // one designated bidder per team: whoever was assigned to it first. only they can bid,
    // and everyone else on a team or tier gets zeroed out when the auction starts
    final Map<String, UUID> teamLeaders = new HashMap<>();

    private final Path file;
    private final MinecraftServer server;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> new Thread(r, "MoneySMP-save"));
    private CompletableFuture<Void> pending = CompletableFuture.completedFuture(null);

    private record Snapshot(int teamCount, boolean teamCountSet, Integer teamMax, Set<String> disabledTeams,
                             Map<String, UUID> teamLeaders, Map<UUID, PlayerData> players, List<Tx> transactions) {}

    Data(Path dir, MinecraftServer server) {
        this.file = dir.resolve("data.json");
        this.server = server;
    }

    void load() {
        players.clear();
        byName.clear();
        transactions.clear();
        disabledTeams.clear();
        teamLeaders.clear();
        if (!Files.exists(file)) return;
        JsonObject y;
        try {
            y = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            MoneySMP.LOG.error("could not read data.json", e);
            return;
        }
        teamCountSet = y.has("teamcount_set") && y.get("teamcount_set").getAsBoolean();
        teamCount = teamCountSet && y.has("teamcount") ? y.get("teamcount").getAsInt() : Teams.NAMES.size();
        teamMax = y.has("teammax") && !y.get("teammax").isJsonNull() ? y.get("teammax").getAsInt() : null;
        if (y.has("disabledteams")) {
            for (JsonElement el : y.getAsJsonArray("disabledteams")) disabledTeams.add(el.getAsString());
        }
        if (y.has("teamleaders")) {
            for (Map.Entry<String, JsonElement> e : y.getAsJsonObject("teamleaders").entrySet()) {
                teamLeaders.put(e.getKey(), UUID.fromString(e.getValue().getAsString()));
            }
        }
        if (y.has("players")) {
            for (Map.Entry<String, JsonElement> e : y.getAsJsonObject("players").entrySet()) {
                JsonObject o = e.getValue().getAsJsonObject();
                PlayerData pd = new PlayerData();
                pd.name = o.has("name") && !o.get("name").isJsonNull() ? o.get("name").getAsString() : null;
                pd.money = o.has("money") ? o.get("money").getAsDouble() : 0;
                pd.team = o.has("team") && !o.get("team").isJsonNull() ? o.get("team").getAsString() : null;
                pd.tier = o.has("tier") && !o.get("tier").isJsonNull() ? o.get("tier").getAsString() : null;
                UUID uid = UUID.fromString(e.getKey());
                players.put(uid, pd);
                if (pd.name != null) byName.put(pd.name, uid);
            }
        }
        if (y.has("transactions")) {
            for (JsonElement el : y.getAsJsonArray("transactions")) {
                JsonObject o = el.getAsJsonObject();
                transactions.add(new Tx(
                    o.get("stamp").getAsLong(),
                    o.get("type").getAsString(),
                    o.get("from").getAsString(),
                    o.get("to").getAsString(),
                    o.get("amount").getAsDouble(),
                    o.get("note").getAsString()));
            }
        }
        transactions.sort(Comparator.comparingLong(Tx::stamp));
        bootstrapLeaders();
    }

    // saves predate the leader system: give any team that has members but no recorded
    // leader one, so bidding isn't locked out on an existing world. picked by name so
    // it's at least deterministic, since join order was never recorded
    private void bootstrapLeaders() {
        Map<String, String> bestName = new HashMap<>();
        Map<String, UUID> bestUid = new HashMap<>();
        for (Map.Entry<UUID, PlayerData> e : players.entrySet()) {
            PlayerData pd = e.getValue();
            if (pd.team == null || teamLeaders.containsKey(pd.team)) continue;
            String name = pd.name == null ? "" : pd.name;
            if (!bestName.containsKey(pd.team) || name.compareTo(bestName.get(pd.team)) < 0) {
                bestName.put(pd.team, name);
                bestUid.put(pd.team, e.getKey());
            }
        }
        teamLeaders.putAll(bestUid);
    }

    void save() {
        if (!pending.isDone()) return;
        Snapshot snapshot = snapshot();
        pending = CompletableFuture.runAsync(() -> write(snapshot), writer);
    }

    void close() {
        Snapshot snapshot = snapshot();
        CompletableFuture<Void> last = CompletableFuture.runAsync(() -> write(snapshot), writer);
        writer.shutdown();
        last.join();
    }

    private Snapshot snapshot() {
        Map<UUID, PlayerData> copy = new HashMap<>();
        players.forEach((uid, pd) -> {
            PlayerData saved = new PlayerData();
            saved.name = pd.name;
            saved.money = pd.money;
            saved.team = pd.team;
            saved.tier = pd.tier;
            copy.put(uid, saved);
        });
        return new Snapshot(teamCount, teamCountSet, teamMax, Set.copyOf(disabledTeams), Map.copyOf(teamLeaders),
            copy, List.copyOf(transactions));
    }

    private void write(Snapshot snapshot) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling("data.json.tmp");
            try (var out = GSON.newJsonWriter(Files.newBufferedWriter(tmp))) {
                out.beginObject();
                if (snapshot.teamCountSet()) {
                    out.name("teamcount").value(snapshot.teamCount());
                    out.name("teamcount_set").value(true);
                }
                if (snapshot.teamMax() != null) out.name("teammax").value(snapshot.teamMax());
                out.name("disabledteams").beginArray();
                for (String t : snapshot.disabledTeams()) out.value(t);
                out.endArray();
                out.name("teamleaders").beginObject();
                for (Map.Entry<String, UUID> e : snapshot.teamLeaders().entrySet()) out.name(e.getKey()).value(e.getValue().toString());
                out.endObject();
                out.name("players").beginObject();
                for (Map.Entry<UUID, PlayerData> e : snapshot.players().entrySet()) {
                    out.name(e.getKey().toString());
                    GSON.toJson(e.getValue(), PlayerData.class, out);
                }
                out.endObject();
                out.name("transactions").beginArray();
                for (Tx tx : snapshot.transactions()) GSON.toJson(tx, Tx.class, out);
                out.endArray();
                out.endObject();
            }
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            MoneySMP.LOG.error("could not save data.json", e);
        }
    }

    PlayerData get(UUID uid) {
        return players.computeIfAbsent(uid, u -> new PlayerData());
    }

    PlayerData get(ServerPlayer p) {
        PlayerData pd = get(p.getUUID());
        pd.name = p.getScoreboardName();
        byName.put(pd.name, p.getUUID());
        return pd;
    }

    double money(UUID uid) {
        PlayerData pd = players.get(uid);
        return pd == null ? 0 : pd.money;
    }

    String team(UUID uid) {
        PlayerData pd = players.get(uid);
        return pd == null ? null : pd.team;
    }

    String tier(UUID uid) {
        PlayerData pd = players.get(uid);
        return pd == null ? null : pd.tier;
    }

    // the member of `team` holding `tier`, if any. a team only ever has one per tier
    PlayerData teamMember(String team, String tier) {
        for (PlayerData pd : players.values()) {
            if (team.equals(pd.team) && tier.equals(pd.tier)) return pd;
        }
        return null;
    }

    // puts a player on a team and, if that team has no leader yet, makes them it
    void assignTeam(UUID uid, String team) {
        get(uid).team = team;
        if (team != null) teamLeaders.putIfAbsent(team, uid);
    }

    // call before reshuffling a team's whole membership so the next assignTeam() on it
    // picks a fresh leader instead of keeping one who might not even be on it anymore
    void clearLeader(String team) {
        teamLeaders.remove(team);
    }

    boolean isLeader(UUID uid, String team) {
        return team != null && uid.equals(teamLeaders.get(team));
    }

    // stored name -> uuid, falling back to an online player with that exact name
    UUID lookup(String name) {
        UUID uid = byName.get(name);
        if (uid != null) return uid;
        ServerPlayer p = server.getPlayerList().getPlayerByName(name);
        if (p == null) return null;
        get(p);
        return p.getUUID();
    }

    // lookup, then fall back for players who have never joined: ask Mojang in online mode,
    // otherwise derive the offline uuid so it matches what they'll get when they connect.
    // new entries start on the join default so they don't lose it when they do show up
    UUID resolve(String name) {
        UUID uid = lookup(name);
        if (uid != null) return uid;
        Optional<NameAndId> found = server.usesAuthentication()
            ? server.services().nameToIdCache().get(name)
            : Optional.of(NameAndId.createOffline(name));
        if (found.isEmpty()) return null;
        uid = found.get().id();
        if (!players.containsKey(uid)) get(uid).money = 0;
        PlayerData pd = get(uid);
        pd.name = found.get().name();
        byName.put(pd.name, uid);
        return uid;
    }

    void log(String type, String from, String to, double amount, String note) {
        Tx tx = new Tx(System.currentTimeMillis(), type, from, to, amount, note);
        int size = transactions.size();
        if (size == 0 || transactions.get(size - 1).stamp() <= tx.stamp()) {
            transactions.add(tx);
        } else {
            transactions.add(firstTransaction(tx.stamp()), tx);
        }
    }

    int firstTransaction(long stamp) {
        int lo = 0;
        int hi = transactions.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (transactions.get(mid).stamp() < stamp) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }
}
