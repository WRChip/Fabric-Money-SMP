package dev.flame.moneysmp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.AbstractWindCharge;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.MapItemColor;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// 5x5 goal board every team races on at once. unlike lockout a goal never locks: each team
// can still finish it, just for fewer points the later they get there (5 4 3 2 1 0, and
// 15 12 9 6 3 0 for a full row, column or diagonal). the first full board is +200 and ends
// it, otherwise the time limit does. everyone carries a map of the board that is redrawn
// once a second. progress is shared by the whole team. ticked once a second by MoneySMP
public final class Unlockout {
    static final int GOAL_POINTS = 5;
    static final int LINE_POINTS = 15;
    static final int LINE_STEP = 3;
    static final int BOARD_POINTS = 200;
    private static final long[] WARNINGS = {3600, 1800, 600, 300, 60, 10};
    private static final String ZOGLIN_TAG = "moneysmp_zoglin";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    enum Kind { FLAG, SET, COUNT }

    // label is what the map shows: up to three lines of six characters, split on |
    record Goal(String name, String label, Kind kind, int target, String unit) {}

    static final int WARDEN = 0, BREED = 1, Y320 = 2, SNEAK = 3, RAID = 4,
        FOODS = 5, ELITE = 6, VAULT = 7, WITHER = 8, COMPOST = 9,
        BREEZE = 10, PVP = 11, SPY = 12, RELIC = 13, DEALT = 14,
        CONCRETE = 15, HUNGER = 16, ZOGLIN = 17, COMPASS = 18, HOSTILE = 19,
        SPRINT = 20, EFFECTS = 21, CONDUIT = 22, TAKEN = 23, GHAST = 24;

    static final List<Goal> GOALS = List.of(
        new Goal("Kill the Warden", "KILL|THE|WARDEN", Kind.FLAG, 1, ""),
        new Goal("Breed 10 unique mobs", "BREED|10|MOBS", Kind.SET, 10, "mobs"),
        new Goal("Reach y=320", "REACH|Y=320|", Kind.FLAG, 1, ""),
        new Goal("Sneak 500 blocks", "SNEAK|500|BLOCKS", Kind.COUNT, 500, "blocks"),
        new Goal("Win a level-5 ominous raid", "WIN|LVL 5|RAID", Kind.FLAG, 1, ""),
        new Goal("Eat 15 different foods", "EAT 15|FOODS|", Kind.SET, 15, "foods"),
        new Goal("Kill an evoker, ravager, piglin brute and elder guardian", "KILL|ELITE|MOBS", Kind.SET, 4, "mobs"),
        new Goal("Open an ominous vault", "OPEN|OMINUS|VAULT", Kind.FLAG, 1, ""),
        new Goal("Summon and kill the Wither", "KILL|THE|WITHER", Kind.FLAG, 1, ""),
        new Goal("Compost 7 types of edible food", "COMPST|7|FOODS", Kind.SET, 7, "foods"),
        new Goal("Kill a Breeze with a wind charge", "BREEZE|WIND|CHARGE", Kind.FLAG, 1, ""),
        new Goal("Kill a player from 2 different opposing teams", "KILL|PLAYER|2TEAMS", Kind.SET, 2, "teams"),
        new Goal("Spy on 25 different mobs with a spyglass", "SPY|25|MOBS", Kind.SET, 25, "mobs"),
        new Goal("Relic disc from trail ruins", "RELIC|DISC|", Kind.FLAG, 1, ""),
        new Goal("Deal 1,000,000 damage", "DEAL|1M|DAMAGE", Kind.COUNT, 1_000_000, "damage"),
        new Goal("320 red concrete", "320|RED|CONCRT", Kind.COUNT, 320, "red concrete"),
        new Goal("Empty your hunger bar to zero", "EMPTY|HUNGER|BAR", Kind.FLAG, 1, ""),
        new Goal("Hoglin → zoglin, then kill it", "HOGLIN|ZOGLIN|KILL", Kind.FLAG, 1, ""),
        new Goal("Craft a recovery compass", "CRAFT|RECOVR|COMPAS", Kind.FLAG, 1, ""),
        new Goal("Kill 15 different hostile mob types", "KILL|15 MOB|TYPES", Kind.SET, 15, "types"),
        new Goal("Sprint 1,000 blocks", "SPRINT|1000|BLOCKS", Kind.COUNT, 1000, "blocks"),
        new Goal("10 effects active at once", "10|EFFCTS|ACTIVE", Kind.FLAG, 1, ""),
        new Goal("Active conduit", "ACTIVE|CONDUT|", Kind.FLAG, 1, ""),
        new Goal("Take 5,000 damage", "TAKE|5000|DAMAGE", Kind.COUNT, 5000, "damage"),
        new Goal("Rename a ghast Dinnerbone", "GHAST|DINNER|BONE", Kind.FLAG, 1, ""));

    // rows, columns, then the two diagonals
    static final int[][] LINES = new int[12][5];
    static {
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                LINES[i][j] = i * 5 + j;
                LINES[5 + i][j] = j * 5 + i;
            }
            LINES[10][i] = i * 6;
            LINES[11][i] = i * 4 + 4;
        }
    }

    static final class Progress {
        final Set<String> items = new LinkedHashSet<>();
        double count;
    }

    // the mixins have no handle on the mod, so the live instance parks itself here
    static Unlockout hooks;
    // Raid.tick sets this before it fires RAID_WIN, so the trigger can see the omen level
    public static Raid tickingRaid;

    private final MoneySMP plugin;
    private Path file;
    boolean running;
    long endAt;
    private long total;
    final Map<Integer, List<String>> done = new HashMap<>();
    final Map<Integer, List<String>> linesDone = new HashMap<>();
    private final Map<String, Map<Integer, Progress>> progress = new HashMap<>();
    // sneak base, sneak last, sprint base, sprint last, all in cm. base -1 until first seen
    private final Map<UUID, long[]> stats = new HashMap<>();
    // one board per team, "" for players without one
    final Map<String, MapId> maps = new HashMap<>();
    private final ServerBossEvent bar = new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);
    private long lastLeft;
    private int saveTimer;

    Unlockout(MoneySMP plugin) {
        this.plugin = plugin;
        hooks = this;
    }

    private void broadcast(String msg) {
        plugin.server.getPlayerList().broadcastSystemMessage(Fmt.c(msg), false);
    }

    private List<ServerPlayer> players() {
        return plugin.server.getPlayerList().getPlayers();
    }

    private String team(ServerPlayer p) {
        return plugin.data.team(p.getUUID());
    }

    // ── unlockout.json ───────────────────────────────────────────

    void load(Path dir) {
        file = dir.resolve("unlockout.json");
        running = false;
        clear();
        if (!Files.exists(file)) return;
        try {
            JsonObject y = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if (!y.has("running") || !y.get("running").getAsBoolean()) return;
            endAt = y.has("endat") ? y.get("endat").getAsLong() : 0;
            total = y.has("total") ? y.get("total").getAsLong() : 0;
            readOrder(y.getAsJsonObject("done"), done);
            readOrder(y.getAsJsonObject("lines"), linesDone);
            for (Map.Entry<String, JsonElement> e : y.getAsJsonObject("maps").entrySet()) {
                maps.put(e.getKey(), new MapId(e.getValue().getAsInt()));
            }
            for (Map.Entry<String, JsonElement> t : y.getAsJsonObject("progress").entrySet()) {
                for (Map.Entry<String, JsonElement> g : t.getValue().getAsJsonObject().entrySet()) {
                    JsonObject o = g.getValue().getAsJsonObject();
                    Progress p = prog(t.getKey(), Integer.parseInt(g.getKey()));
                    p.count = o.get("count").getAsDouble();
                    for (JsonElement el : o.getAsJsonArray("items")) p.items.add(el.getAsString());
                }
            }
            for (Map.Entry<String, JsonElement> e : y.getAsJsonObject("stats").entrySet()) {
                JsonArray arr = e.getValue().getAsJsonArray();
                long[] v = new long[4];
                for (int i = 0; i < 4; i++) v[i] = arr.get(i).getAsLong();
                stats.put(UUID.fromString(e.getKey()), v);
            }
            lastLeft = Long.MAX_VALUE;
            running = true;
            MoneySMP.LOG.info("resuming unlockout event: {} goal completions so far", done.values().stream().mapToInt(List::size).sum());
        } catch (IOException | RuntimeException e) {
            clear();
            MoneySMP.LOG.error("could not read unlockout.json", e);
        }
    }

    private void clear() {
        endAt = 0;
        total = 0;
        done.clear();
        linesDone.clear();
        progress.clear();
        stats.clear();
        maps.clear();
    }

    private static void readOrder(JsonObject o, Map<Integer, List<String>> into) {
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            List<String> teams = new ArrayList<>();
            for (JsonElement el : e.getValue().getAsJsonArray()) teams.add(el.getAsString());
            into.put(Integer.parseInt(e.getKey()), teams);
        }
    }

    void save() {
        JsonObject y = new JsonObject();
        y.addProperty("running", running);
        if (running) {
            y.addProperty("endat", endAt);
            y.addProperty("total", total);
            y.add("done", order(done));
            y.add("lines", order(linesDone));
            JsonObject ms = new JsonObject();
            maps.forEach((t, id) -> ms.addProperty(t, id.id()));
            y.add("maps", ms);
            JsonObject pr = new JsonObject();
            progress.forEach((t, goals) -> {
                JsonObject byGoal = new JsonObject();
                goals.forEach((g, p) -> {
                    JsonObject o = new JsonObject();
                    o.addProperty("count", p.count);
                    JsonArray items = new JsonArray();
                    for (String s : p.items) items.add(s);
                    o.add("items", items);
                    byGoal.add(String.valueOf(g), o);
                });
                pr.add(t, byGoal);
            });
            y.add("progress", pr);
            JsonObject st = new JsonObject();
            stats.forEach((uid, v) -> {
                JsonArray arr = new JsonArray();
                for (long n : v) arr.add(n);
                st.add(uid.toString(), arr);
            });
            y.add("stats", st);
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(y));
        } catch (IOException e) {
            MoneySMP.LOG.error("could not write unlockout.json", e);
        }
    }

    private static JsonObject order(Map<Integer, List<String>> m) {
        JsonObject o = new JsonObject();
        m.forEach((k, teams) -> {
            JsonArray arr = new JsonArray();
            for (String t : teams) arr.add(t);
            o.add(String.valueOf(k), arr);
        });
        return o;
    }

    // ── event ────────────────────────────────────────────────────

    // null when it began, otherwise why it couldn't. seconds <= 0 means no time limit
    String start(long seconds) {
        if (running) return "&cAn unlockout event is already running.";
        clear();
        endAt = seconds > 0 ? System.currentTimeMillis() + seconds * 1000 : 0;
        total = seconds;
        lastLeft = seconds;
        saveTimer = 0;
        running = true;
        broadcast("");
        broadcast(Fmt.PREFIX + " &a&l⚑ UNLOCKOUT STARTED ⚑");
        broadcast("  &7Complete goals on the board for your team. First team to a goal gets &e" + GOAL_POINTS
            + " pts&7, then one less each; a full row, column or diagonal is &e" + LINE_POINTS + " pts &7and down by " + LINE_STEP + ".");
        broadcast("  &7The whole board is worth &e+" + BOARD_POINTS + " &7and ends it"
            + (seconds > 0 ? ", otherwise it ends in &f" + Fmt.timeAgo(seconds) + "&7." : "."));
        broadcast("  &7Your map shows the board. &f/unlockout &7lists the goals, &f/unlockout map &7gets a new map.");
        broadcast("");
        for (ServerPlayer p : players()) join(p);
        render();
        updateBar();
        save();
        return null;
    }

    void stop() {
        end("&7The unlockout event was stopped.");
    }

    private void end(String reason) {
        running = false;
        render();
        bar.removeAllPlayers();
        save();
        broadcast("");
        broadcast(Fmt.PREFIX + " " + reason);
        for (Map.Entry<String, Integer> e : standings()) {
            broadcast("  " + Teams.color(e.getKey()) + "&l" + e.getKey() + "  &e" + e.getValue() + " pts  &8(" + doneCount(e.getKey()) + "/" + GOALS.size() + " goals)");
        }
        broadcast("");
    }

    private List<Map.Entry<String, Integer>> standings() {
        List<Map.Entry<String, Integer>> out = new ArrayList<>();
        for (String t : Teams.active(plugin.data.teamCount, plugin.data.disabledTeams)) {
            out.add(Map.entry(t, plugin.data.teamPoints.getOrDefault(t, 0)));
        }
        out.sort((a, b) -> b.getValue() - a.getValue());
        return out;
    }

    void join(ServerPlayer p) {
        if (!running) return;
        bar.addPlayer(p);
        giveMap(p);
    }

    void leave(ServerPlayer p) {
        bar.removePlayer(p);
    }

    void tick() {
        if (!running) return;
        if (endAt > 0) {
            long left = Math.max(0, (endAt - System.currentTimeMillis() + 999) / 1000);
            for (long w : WARNINGS) {
                if (lastLeft > w && left <= w && left > 0) broadcast(Fmt.PREFIX + " &e" + Fmt.timeAgo(left) + " &7left in the unlockout event!");
            }
            lastLeft = left;
            if (left == 0) {
                end("&e&lTime's up!");
                return;
            }
        }
        Map<String, Integer> concrete = new HashMap<>();
        for (ServerPlayer p : players()) {
            sample(p);
            String team = team(p);
            if (team == null || p.isSpectator()) continue;
            if (p.getY() >= 320) flag(team, Y320);
            if (p.getFoodData().getFoodLevel() <= 0) flag(team, HUNGER);
            if (p.getActiveEffects().size() >= 10) flag(team, EFFECTS);
            if (p.hasEffect(MobEffects.CONDUIT_POWER)) flag(team, CONDUIT);
            concrete.merge(team, p.getInventory().countItem(Items.RED_CONCRETE), Integer::sum);
            if (!running) return;
        }
        concrete.forEach((t, n) -> count(t, CONCRETE, n));
        Map<String, double[]> dist = new HashMap<>();
        for (Map.Entry<UUID, long[]> e : stats.entrySet()) {
            String t = plugin.data.team(e.getKey());
            if (t == null) continue;
            long[] v = e.getValue();
            double[] d = dist.computeIfAbsent(t, k -> new double[2]);
            d[0] += (v[1] - v[0]) / 100.0;
            d[1] += (v[3] - v[2]) / 100.0;
        }
        dist.forEach((t, d) -> {
            count(t, SNEAK, d[0]);
            count(t, SPRINT, d[1]);
        });
        if (!running) return;
        render();
        updateBar();
        if (++saveTimer % 60 == 0) save();
    }

    private void sample(ServerPlayer p) {
        long[] v = stats.computeIfAbsent(p.getUUID(), u -> new long[]{-1, 0, -1, 0});
        long sneak = p.getStats().getValue(Stats.CUSTOM, Stats.CROUCH_ONE_CM);
        long sprint = p.getStats().getValue(Stats.CUSTOM, Stats.SPRINT_ONE_CM);
        if (v[0] < 0) {
            v[0] = sneak;
            v[2] = sprint;
        }
        v[1] = sneak;
        v[3] = sprint;
    }

    private void updateBar() {
        StringBuilder sb = new StringBuilder("&a&l⚑ UNLOCKOUT");
        for (Map.Entry<String, Integer> e : standings()) {
            sb.append("  &8·  ").append(Teams.color(e.getKey())).append(e.getKey()).append(" &e").append(e.getValue());
        }
        if (endAt > 0) {
            long left = Math.max(0, (endAt - System.currentTimeMillis() + 999) / 1000);
            sb.append("  &8·  &f").append(Fmt.timeAgo(left)).append(" &7left");
            bar.setProgress(total > 0 ? (float) Math.min(1.0, (double) left / total) : 1f);
        }
        bar.setName(Fmt.c(sb.toString()));
    }

    // ── progress ─────────────────────────────────────────────────

    private Progress prog(String team, int g) {
        return progress.computeIfAbsent(team, t -> new HashMap<>()).computeIfAbsent(g, k -> new Progress());
    }

    boolean isDone(String team, int g) {
        List<String> order = done.get(g);
        return order != null && order.contains(team);
    }

    List<String> doneOrder(int g) {
        return done.getOrDefault(g, List.of());
    }

    int doneCount(String team) {
        int n = 0;
        for (int g = 0; g < GOALS.size(); g++) if (isDone(team, g)) n++;
        return n;
    }

    double fraction(String team, int g) {
        if (isDone(team, g)) return 1;
        Map<Integer, Progress> goals = progress.get(team);
        Progress p = goals == null ? null : goals.get(g);
        if (p == null) return 0;
        Goal goal = GOALS.get(g);
        return switch (goal.kind()) {
            case FLAG -> 0;
            case SET -> Math.min(1.0, (double) p.items.size() / goal.target());
            case COUNT -> Math.min(1.0, p.count / goal.target());
        };
    }

    private void flag(String team, int g) {
        if (!isDone(team, g)) complete(team, g);
    }

    private void item(String team, int g, String key) {
        if (isDone(team, g)) return;
        Progress p = prog(team, g);
        if (!p.items.add(key)) return;
        if (p.items.size() >= GOALS.get(g).target()) complete(team, g);
        else save();
    }

    private void count(String team, int g, double value) {
        if (isDone(team, g)) return;
        Progress p = prog(team, g);
        p.count = value;
        if (value >= GOALS.get(g).target()) complete(team, g);
    }

    private void add(String team, int g, double delta) {
        if (isDone(team, g)) return;
        count(team, g, prog(team, g).count + delta);
    }

    private void complete(String team, int g) {
        if (!running) return;
        List<String> order = done.computeIfAbsent(g, k -> new ArrayList<>());
        order.add(team);
        int pts = Math.max(0, GOAL_POINTS - (order.size() - 1));
        int now = plugin.data.teamPoints.merge(team, pts, Integer::sum);
        String col = Teams.color(team);
        broadcast(Fmt.PREFIX + " " + col + "&l" + team + " &acompleted &f" + GOALS.get(g).name() + "&a!  &e+" + pts + " pts &8("
            + ordinal(order.size()) + " team)  &7» &e" + now + " pts");
        // only lines through this goal can have just become complete
        for (int l = 0; l < LINES.length; l++) {
            boolean full = false;
            for (int cell : LINES[l]) full |= cell == g;
            for (int cell : LINES[l]) full &= isDone(team, cell);
            if (!full) continue;
            List<String> lo = linesDone.computeIfAbsent(l, k -> new ArrayList<>());
            lo.add(team);
            int lp = Math.max(0, LINE_POINTS - LINE_STEP * (lo.size() - 1));
            now = plugin.data.teamPoints.merge(team, lp, Integer::sum);
            broadcast("");
            broadcast(Fmt.PREFIX + " " + col + "&l" + team + " &afinished " + lineName(l) + "&a!  &e+" + lp + " pts &8(" + ordinal(lo.size()) + " team)  &7» &e" + now + " pts");
            broadcast("");
        }
        if (doneCount(team) == GOALS.size()) {
            now = plugin.data.teamPoints.merge(team, BOARD_POINTS, Integer::sum);
            broadcast("");
            broadcast(Fmt.PREFIX + " " + col + "&l" + team + " &a&lcompleted the entire board!  &e+" + BOARD_POINTS + " pts  &7» &e" + now + " pts");
            end(col + "&l" + team + " &acleared the board!");
            return;
        }
        save();
    }

    private static String lineName(int l) {
        if (l < 5) return "row " + (l + 1);
        if (l < 10) return "column " + (l - 4);
        return l == 10 ? "the top-left diagonal" : "the top-right diagonal";
    }

    private static String ordinal(int n) {
        return n + switch (n) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
    }

    private static String key(EntityType<?> type) {
        return EntityType.getKey(type).getPath();
    }

    private static String key(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).getPath();
    }

    // ── hooks ────────────────────────────────────────────────────

    private static String teamOf(ServerPlayer p) {
        return hooks == null || !hooks.running || p == null ? null : hooks.team(p);
    }

    void onDeath(LivingEntity victim, DamageSource source) {
        if (!running) return;
        ServerPlayer killer = source.getEntity() instanceof ServerPlayer sp ? sp
            : victim.getKillCredit() instanceof ServerPlayer sp ? sp : null;
        if (killer == null || killer == victim) return;
        String team = team(killer);
        if (team == null) return;
        if (victim instanceof ServerPlayer v) {
            String vt = team(v);
            if (vt != null && !vt.equals(team)) item(team, PVP, vt);
            return;
        }
        String type = key(victim.getType());
        if (victim instanceof Enemy) item(team, HOSTILE, type);
        if (victim instanceof Warden) flag(team, WARDEN);
        if (victim instanceof WitherBoss && prog(team, WITHER).items.contains(victim.getUUID().toString())) flag(team, WITHER);
        if (victim instanceof Breeze && source.getDirectEntity() instanceof AbstractWindCharge) flag(team, BREEZE);
        if (victim instanceof Zoglin && victim.getTags().contains(ZOGLIN_TAG)) flag(team, ZOGLIN);
        if (victim.getType() == EntityType.EVOKER || victim.getType() == EntityType.RAVAGER
            || victim.getType() == EntityType.PIGLIN_BRUTE || victim.getType() == EntityType.ELDER_GUARDIAN) {
            item(team, ELITE, type);
        }
    }

    // raw amount of a hit that landed, see LivingEntityMixin
    public static void damaged(LivingEntity entity, DamageSource source, float amount) {
        if (hooks == null || !hooks.running || amount <= 0) return;
        if (entity instanceof ServerPlayer v) {
            String vt = hooks.team(v);
            if (vt != null) hooks.add(vt, TAKEN, amount);
        }
        if (source.getEntity() instanceof ServerPlayer a && a != entity) {
            String at = hooks.team(a);
            if (at != null) hooks.add(at, DEALT, amount);
        }
    }

    void onConversion(Mob from, Mob to) {
        if (running && from instanceof Hoglin && to instanceof Zoglin) to.addTag(ZOGLIN_TAG);
    }

    public static void bred(ServerPlayer p, Animal parent) {
        String team = teamOf(p);
        if (team != null) hooks.item(team, BREED, key(parent.getType()));
    }

    public static void ate(ServerPlayer p, ItemStack stack) {
        String team = teamOf(p);
        if (team != null && stack.has(DataComponents.FOOD)) hooks.item(team, FOODS, key(stack.getItem()));
    }

    public static void composted(ServerPlayer p, ItemStack stack) {
        String team = teamOf(p);
        if (team != null && stack.has(DataComponents.FOOD)) hooks.item(team, COMPOST, key(stack.getItem()));
    }

    // fires every tick the spyglass is up; same ray the vanilla looking_at predicate uses
    public static void using(ServerPlayer p, ItemStack stack) {
        String team = teamOf(p);
        if (team == null || !stack.is(Items.SPYGLASS) || hooks.isDone(team, SPY)) return;
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getViewVector(1f).scale(100));
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(p.level(), p, eye, end, new AABB(eye, end).inflate(1), e -> !e.isSpectator() && e instanceof Mob, 0f);
        if (hit != null && hit.getEntity() instanceof Mob m && p.hasLineOfSight(m)) hooks.item(team, SPY, key(m.getType()));
    }

    public static void raidWon(ServerPlayer p) {
        String team = teamOf(p);
        if (team != null && tickingRaid != null && tickingRaid.getRaidOmenLevel() >= 5) hooks.flag(team, RAID);
    }

    public static void summoned(ServerPlayer p, Entity e) {
        String team = teamOf(p);
        if (team == null || !(e instanceof WitherBoss) || hooks.isDone(team, WITHER)) return;
        if (hooks.prog(team, WITHER).items.add(e.getUUID().toString())) hooks.save();
    }

    public static void crafted(ServerPlayer p, ResourceKey<Recipe<?>> recipe) {
        String team = teamOf(p);
        if (team != null && recipe.identifier().getPath().equals("recovery_compass")) hooks.flag(team, COMPASS);
    }

    public static void inventoryChanged(ServerPlayer p, ItemStack stack) {
        String team = teamOf(p);
        if (team != null && stack.is(Items.MUSIC_DISC_RELIC)) hooks.flag(team, RELIC);
    }

    public static void interacted(ServerPlayer p, ItemStack stack, Entity target) {
        String team = teamOf(p);
        if (team == null || !stack.is(Items.NAME_TAG) || !(target instanceof Ghast)) return;
        Component name = stack.get(DataComponents.CUSTOM_NAME);
        if (name != null && name.getString().equals("Dinnerbone")) hooks.flag(team, GHAST);
    }

    public static void vaultOpened(ServerPlayer p, boolean ominous) {
        String team = teamOf(p);
        if (team != null && ominous) hooks.flag(team, VAULT);
    }

    // ── board map ────────────────────────────────────────────────

    private MapId map(String key) {
        MapId id = maps.get(key);
        if (id != null) return id;
        ServerLevel ow = plugin.server.overworld();
        id = ow.getFreeMapId();
        ow.setMapData(id, MapItemSavedData.createFresh(0, 0, (byte) 0, false, false, Level.OVERWORLD).locked());
        maps.put(key, id);
        save();
        return id;
    }

    // the player ends up holding exactly one board: their team's. an old one from another
    // team (or from before a team switch) is taken away
    void giveMap(ServerPlayer p) {
        if (!running) return;
        String team = team(p);
        String key = team == null ? "" : team;
        MapId want = map(key);
        Inventory inv = p.getInventory();
        boolean has = false;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            MapId id = inv.getItem(i).get(DataComponents.MAP_ID);
            if (id == null || !maps.containsValue(id)) continue;
            if (id.equals(want)) has = true;
            else inv.setItem(i, ItemStack.EMPTY);
        }
        if (has) return;
        ItemStack st = new ItemStack(Items.FILLED_MAP);
        st.set(DataComponents.MAP_ID, want);
        st.set(DataComponents.ITEM_NAME, Fmt.c("&a&lUnlockout Board" + (team == null ? "" : "  " + Teams.color(team) + team)));
        if (team != null) st.set(DataComponents.MAP_COLOR, new MapItemColor(Teams.rgb(team)));
        if (!inv.add(st)) p.drop(st, false);
    }

    private void render() {
        ServerLevel ow = plugin.server.overworld();
        for (Map.Entry<String, MapId> e : maps.entrySet()) {
            MapItemSavedData data = ow.getMapData(e.getValue());
            if (data == null) continue;
            byte[] px = UnlockoutMap.draw(this, e.getKey());
            for (int y = 0; y < 128; y++) {
                for (int x = 0; x < 128; x++) data.updateColor(x, y, px[y * 128 + x]);
            }
        }
    }

    // ── chat board ───────────────────────────────────────────────

    void show(CommandSourceStack s, boolean list) {
        ServerPlayer p = s.getPlayer();
        String team = p == null ? null : team(p);
        String label = team == null ? "&7No Team" : Teams.color(team) + team;
        s.sendSystemMessage(Component.empty());
        s.sendSystemMessage(Fmt.c(Fmt.PREFIX + " &a&l⚑ UNLOCKOUT  &8|  &7You: " + label
            + (endAt > 0 ? "  &8|  &f" + Fmt.timeAgo(Math.max(0, (endAt - System.currentTimeMillis() + 999) / 1000)) + " &7left" : "")));
        if (!list) {
            for (int r = 0; r < 5; r++) {
                MutableComponent row = Component.literal("   ");
                for (int c = 0; c < 5; c++) {
                    int g = r * 5 + c;
                    int left = Math.max(0, GOAL_POINTS - doneOrder(g).size());
                    String cell = team != null && isDone(team, g) ? Teams.color(team) + "&l[✔]" : left == 0 ? "&8[0]" : "&f[&e" + left + "&f]";
                    row.append(Fmt.c(cell + " ").withStyle(st -> st.withHoverEvent(new HoverEvent.ShowText(Fmt.c(detail(team, g))))));
                }
                s.sendSystemMessage(row);
            }
            s.sendSystemMessage(Fmt.c("  &8Cells show the points still on offer; hover one for details. &f/unlockout list &8for all goals."));
        } else {
            for (int g = 0; g < GOALS.size(); g++) {
                StringBuilder who = new StringBuilder();
                for (String t : doneOrder(g)) who.append(Teams.color(t)).append("■");
                s.sendSystemMessage(Fmt.c("  &8#" + (g + 1) + " &f" + GOALS.get(g).name() + "  &8» " + progressText(team, g) + (who.length() > 0 ? "  " + who : "")));
            }
        }
        StringBuilder sb = new StringBuilder("  ");
        for (Map.Entry<String, Integer> e : standings()) {
            sb.append(Teams.color(e.getKey())).append(e.getKey()).append(" &e").append(e.getValue()).append("  ");
        }
        s.sendSystemMessage(Fmt.c(sb.toString()));
        s.sendSystemMessage(Component.empty());
    }

    private String detail(String team, int g) {
        StringBuilder sb = new StringBuilder("&f&l#" + (g + 1) + " " + GOALS.get(g).name());
        if (team != null) sb.append("\n&7Your team: ").append(progressText(team, g));
        List<String> order = doneOrder(g);
        for (int i = 0; i < order.size(); i++) {
            sb.append("\n&8").append(ordinal(i + 1)).append(' ').append(Teams.color(order.get(i))).append(order.get(i))
                .append(" &e+").append(Math.max(0, GOAL_POINTS - i));
        }
        sb.append("\n&7Next team: &e+").append(Math.max(0, GOAL_POINTS - order.size()));
        return sb.toString();
    }

    private String progressText(String team, int g) {
        if (team == null) return "&8-";
        if (isDone(team, g)) return "&adone";
        Goal goal = GOALS.get(g);
        Map<Integer, Progress> goals = progress.get(team);
        Progress p = goals == null ? null : goals.get(g);
        return switch (goal.kind()) {
            case FLAG -> g == WITHER && p != null && !p.items.isEmpty() ? "&esummoned, now kill it" : "&7not yet";
            case SET -> {
                int n = p == null ? 0 : p.items.size();
                StringBuilder sb = new StringBuilder("&e" + n + "&7/" + goal.target() + " " + goal.unit());
                if (n > 0) sb.append(" &8(").append(String.join(", ", p.items).replace('_', ' ')).append(')');
                yield sb.toString();
            }
            case COUNT -> "&e" + Fmt.money(p == null ? 0 : p.count) + "&7/" + Fmt.money(goal.target()) + " " + goal.unit();
        };
    }
}
