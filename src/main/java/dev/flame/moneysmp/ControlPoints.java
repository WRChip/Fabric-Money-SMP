package dev.flame.moneysmp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundTrackedWaypointPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.waypoints.Waypoint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

// KOTH-style capture points. /moneysmp point <n> marks one where the admin stands; the
// control-point event draws a particle beam and ring on each, puts them on the locator bar
// and pays out to the team that holds one long enough. nothing in the world is changed.
// ticked once a second by MoneySMP while running
final class ControlPoints {
    private static final int GRAY = 0x9D9D97;
    private static final int NETHERITE = 0x4D494D;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    record Point(ResourceKey<Level> dim, BlockPos pos) {}

    // several prize sets. "once" hands them out in order, each set a single time per event;
    // "random" rolls any set on every capture
    static final class Pool {
        final List<List<ItemStack>> sets = new ArrayList<>();
        boolean random;
        int next;

        List<ItemStack> take() {
            if (sets.isEmpty()) return List.of();
            if (random) return sets.get(ThreadLocalRandom.current().nextInt(sets.size()));
            return next < sets.size() ? sets.get(next++) : List.of();
        }

        String mode() {
            return random ? "random" : "once";
        }
    }

    private final MoneySMP plugin;
    private Path file;
    final Map<Integer, Point> points = new TreeMap<>();
    final Pool loot = new Pool();
    final Pool superLoot = new Pool();

    boolean running;
    final Set<Integer> superPoints = new HashSet<>();
    final Map<Integer, String> owner = new HashMap<>();
    private final Map<Integer, Map<String, Double>> progress = new HashMap<>();
    private int phase;

    ControlPoints(MoneySMP plugin) {
        this.plugin = plugin;
    }

    private void broadcast(String msg) {
        plugin.server.getPlayerList().broadcastSystemMessage(Fmt.c(msg), false);
    }

    private List<ServerPlayer> players() {
        return plugin.server.getPlayerList().getPlayers();
    }

    private RegistryOps<JsonElement> ops() {
        return RegistryOps.create(JsonOps.INSTANCE, plugin.server.registryAccess());
    }

    // ── points.json ──────────────────────────────────────────────

    void load(Path dir) {
        file = dir.resolve("points.json");
        points.clear();
        loot.sets.clear();
        superLoot.sets.clear();
        superPoints.clear();
        owner.clear();
        progress.clear();
        running = false;
        if (!Files.exists(file)) return;
        try {
            JsonObject y = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if (y.has("points")) {
                for (Map.Entry<String, JsonElement> e : y.getAsJsonObject("points").entrySet()) {
                    JsonObject o = e.getValue().getAsJsonObject();
                    points.put(Integer.parseInt(e.getKey()), new Point(dim(o), pos(o)));
                }
            }
            readPool(y, "loot", loot);
            readPool(y, "superloot", superLoot);
            if (y.has("event")) {
                JsonObject ev = y.getAsJsonObject("event");
                loot.next = ev.has("lootnext") ? ev.get("lootnext").getAsInt() : 0;
                superLoot.next = ev.has("superlootnext") ? ev.get("superlootnext").getAsInt() : 0;
                for (JsonElement el : ev.getAsJsonArray("super")) superPoints.add(el.getAsInt());
                for (Map.Entry<String, JsonElement> e : ev.getAsJsonObject("owner").entrySet()) {
                    owner.put(Integer.parseInt(e.getKey()), e.getValue().getAsString());
                }
                running = true;
                MoneySMP.LOG.info("resuming control point event: {} of {} points captured", owner.size(), points.size());
            }
        } catch (IOException | RuntimeException e) {
            MoneySMP.LOG.error("could not read points.json", e);
        }
    }

    void save() {
        JsonObject y = new JsonObject();
        JsonObject ps = new JsonObject();
        points.forEach((n, pt) -> {
            JsonObject o = new JsonObject();
            put(o, pt.dim(), pt.pos());
            ps.add(String.valueOf(n), o);
        });
        y.add("points", ps);
        y.add("loot", pool(loot));
        y.add("superloot", pool(superLoot));
        if (running) {
            JsonObject ev = new JsonObject();
            ev.addProperty("lootnext", loot.next);
            ev.addProperty("superlootnext", superLoot.next);
            JsonArray sup = new JsonArray();
            for (int n : superPoints) sup.add(n);
            ev.add("super", sup);
            JsonObject own = new JsonObject();
            owner.forEach((n, t) -> own.addProperty(String.valueOf(n), t));
            ev.add("owner", own);
            y.add("event", ev);
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(y));
        } catch (IOException e) {
            MoneySMP.LOG.error("could not write points.json", e);
        }
    }

    private JsonObject pool(Pool pool) {
        JsonObject o = new JsonObject();
        o.addProperty("mode", pool.mode());
        JsonArray sets = new JsonArray();
        for (List<ItemStack> set : pool.sets) {
            JsonArray arr = new JsonArray();
            for (ItemStack st : set) {
                ItemStack.CODEC.encodeStart(ops(), st)
                    .resultOrPartial(err -> MoneySMP.LOG.warn("points.json: could not save item: {}", err))
                    .ifPresent(arr::add);
            }
            sets.add(arr);
        }
        o.add("sets", sets);
        return o;
    }

    // 1.2.0 stored a single flat item list; read that as one set
    private void readPool(JsonObject y, String key, Pool into) {
        if (!y.has(key)) return;
        JsonElement el = y.get(key);
        if (el.isJsonArray()) {
            into.sets.add(readItems(el.getAsJsonArray(), key));
            return;
        }
        JsonObject o = el.getAsJsonObject();
        into.random = o.has("mode") && o.get("mode").getAsString().equals("random");
        for (JsonElement set : o.getAsJsonArray("sets")) into.sets.add(readItems(set.getAsJsonArray(), key));
    }

    private List<ItemStack> readItems(JsonArray arr, String key) {
        List<ItemStack> out = new ArrayList<>();
        for (JsonElement el : arr) {
            ItemStack.CODEC.parse(ops(), el)
                .resultOrPartial(err -> MoneySMP.LOG.warn("points.json: dropping item in {}: {}", key, err))
                .ifPresent(out::add);
        }
        return out;
    }

    private static void put(JsonObject o, ResourceKey<Level> dim, BlockPos pos) {
        o.addProperty("dim", dim.identifier().toString());
        o.addProperty("x", pos.getX());
        o.addProperty("y", pos.getY());
        o.addProperty("z", pos.getZ());
    }

    private static ResourceKey<Level> dim(JsonObject o) {
        return ResourceKey.create(Registries.DIMENSION, Identifier.parse(o.get("dim").getAsString()));
    }

    private static BlockPos pos(JsonObject o) {
        return new BlockPos(o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt());
    }

    // ── setup ────────────────────────────────────────────────────

    void set(int n, ServerPlayer p) {
        points.put(n, new Point(p.level().dimension(), p.blockPosition()));
        save();
    }

    boolean remove(int n) {
        if (points.remove(n) == null) return false;
        save();
        return true;
    }

    Pool pool(boolean sup) {
        return sup ? superLoot : loot;
    }

    // players never show on the locator bar; only control points do
    static void hideFromLocator(ServerPlayer p) {
        AttributeInstance a = p.getAttribute(Attributes.WAYPOINT_TRANSMIT_RANGE);
        if (a != null) a.setBaseValue(0);
        p.level().getWaypointManager().untrackWaypoint(p);
    }

    // ── event ────────────────────────────────────────────────────

    // null when the event began, otherwise why it couldn't
    String start() {
        if (running) return "&cA control point event is already running.";
        if (points.isEmpty()) return "&cNo control points set. Use &f/moneysmp point <number> &cfirst.";
        for (Map.Entry<Integer, Point> e : points.entrySet()) {
            if (plugin.server.getLevel(e.getValue().dim()) == null) {
                return "&cPoint &f#" + e.getKey() + " &cis in a dimension that no longer exists. Remove or re-set it.";
            }
        }
        superPoints.clear();
        owner.clear();
        progress.clear();
        loot.next = 0;
        superLoot.next = 0;
        List<Integer> ids = new ArrayList<>(points.keySet());
        Collections.shuffle(ids);
        long supers = Math.round(ids.size() * plugin.config.controlPointSuperPercent / 100);
        for (int i = 0; i < supers; i++) superPoints.add(ids.get(i));
        running = true;
        save();

        broadcast("");
        broadcast(Fmt.PREFIX + " &a&l⚑ CONTROL POINT EVENT STARTED ⚑");
        broadcast("  &7Stand inside a ring to capture it for your team. Follow the locator bar!");
        broadcast("  &7Points: &f" + points.size() + (superPoints.isEmpty() ? "" : "  &8|  &8&l✦ &7Super: &f" + superPoints.size()));
        broadcast("");
        for (ServerPlayer p : players()) sendWaypoints(p);
        return null;
    }

    void stop() {
        end("&7The control point event was stopped.");
    }

    private void end(String reason) {
        running = false;
        progress.clear();
        save();
        broadcast("");
        broadcast(Fmt.PREFIX + " " + reason);
        List<Map.Entry<String, Integer>> standings = new ArrayList<>(plugin.data.teamPoints.entrySet());
        standings.sort((a, b) -> b.getValue() - a.getValue());
        for (Map.Entry<String, Integer> e : standings) {
            if (e.getValue() > 0) broadcast("  " + Teams.color(e.getKey()) + "&l" + e.getKey() + "  &e" + e.getValue() + " pts");
        }
        broadcast("");
        for (ServerPlayer p : players()) clearWaypoints(p);
    }

    // dust lives about a second, so the beam and ring are redrawn a few times a second
    // to stay solid. called by MoneySMP every 5 ticks
    void draw() {
        if (!running) return;
        phase++;
        for (Map.Entry<Integer, Point> e : points.entrySet()) {
            Point pt = e.getValue();
            ServerLevel level = plugin.server.getLevel(pt.dim());
            if (level == null) continue;
            String held = owner.get(e.getKey());
            int color = held != null ? Teams.rgb(held) : superPoints.contains(e.getKey()) ? NETHERITE : GRAY;
            ring(level, pt.pos(), color);
            beam(level, pt.pos(), color);
        }
    }

    void tick() {
        if (!running) return;
        for (Map.Entry<Integer, Point> e : points.entrySet()) {
            int n = e.getKey();
            Point pt = e.getValue();
            ServerLevel level = plugin.server.getLevel(pt.dim());
            if (level == null) continue;
            String held = owner.get(n);
            boolean sup = superPoints.contains(n);
            if (held != null) continue;

            Map<String, Integer> present = new HashMap<>();
            List<ServerPlayer> inside = new ArrayList<>();
            for (ServerPlayer p : level.players()) {
                if (p.isSpectator() || !inRing(p, pt.pos())) continue;
                inside.add(p);
                String team = plugin.data.team(p.getUUID());
                if (team != null) present.merge(team, 1, Integer::sum);
            }
            String top = null;
            int topCount = 0;
            int second = 0;
            for (Map.Entry<String, Integer> pe : present.entrySet()) {
                if (pe.getValue() > topCount) {
                    second = topCount;
                    topCount = pe.getValue();
                    top = pe.getKey();
                } else if (pe.getValue() > second) {
                    second = pe.getValue();
                }
            }
            Map<String, Double> prog = progress.computeIfAbsent(n, k -> new HashMap<>());
            // a contested point only moves for the bigger team, at the pace of its extra players
            if (top != null && topCount > second) {
                double rate = plugin.config.controlPointPercentPer30s / 30;
                double gain = (topCount - second) * rate / (sup ? plugin.config.controlPointSuperSlowdown : 1);
                if (prog.merge(top, gain, Double::sum) >= 100) {
                    if (capture(n, top)) return;
                    continue;
                }
            }
            if (!inside.isEmpty()) {
                String msg = status(n, sup, prog, top != null && topCount == second);
                for (ServerPlayer p : inside) plugin.notify(p.getUUID(), msg, 2);
            }
        }
    }

    private boolean inRing(ServerPlayer p, BlockPos c) {
        int r = plugin.config.controlPointRadius;
        double dx = p.getX() - (c.getX() + 0.5);
        double dz = p.getZ() - (c.getZ() + 0.5);
        double dy = p.getY() - c.getY();
        return dx * dx + dz * dz <= r * r && dy >= -1 && dy <= plugin.config.controlPointHeight;
    }

    private static String status(int n, boolean sup, Map<String, Double> prog, boolean contested) {
        StringBuilder sb = new StringBuilder(sup ? "&8&l✦ &7Super point &f#" : "&7Point &f#").append(n);
        prog.entrySet().stream()
            .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
            .forEach(e -> sb.append("  ").append(Teams.color(e.getKey())).append(e.getKey())
                .append(" &f").append((int) Math.floor(e.getValue())).append('%'));
        if (contested) sb.append("  &c&lCONTESTED");
        return sb.toString();
    }

    private void ring(ServerLevel level, BlockPos c, int color) {
        DustParticleOptions dust = new DustParticleOptions(color, 1.5f);
        double cx = c.getX() + 0.5;
        double cz = c.getZ() + 0.5;
        double y = c.getY() + 0.3;
        int r = plugin.config.controlPointRadius;
        // same spacing as 8 around the default radius, so bigger rings don't thin out
        int count = Math.max(8, r * 8 / 5);
        for (int i = 0; i < count; i++) {
            double a = (i + phase * 0.37) * Math.PI * 2 / count;
            level.sendParticles(dust, cx + Math.cos(a) * r, y, cz + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
    }

    // a thick column of dust from the ring up into the sky. forced so it shows from far
    // away like a real beacon beam; each packet scatters its particles around one height
    private static void beam(ServerLevel level, BlockPos c, int color) {
        DustParticleOptions dust = new DustParticleOptions(color, 4f);
        double cx = c.getX() + 0.5;
        double cz = c.getZ() + 0.5;
        for (int i = 0; i < 8; i++) {
            level.sendParticles(dust, true, true, cx, c.getY() + 5 + i * 10, cz, 12, 0.4, 3, 0.4, 0);
        }
    }

    // true when this was the last point and the event is over
    private boolean capture(int n, String team) {
        Point pt = points.get(n);
        ServerLevel level = plugin.server.getLevel(pt.dim());
        boolean sup = superPoints.contains(n);
        owner.put(n, team);
        progress.remove(n);

        Config cfg = plugin.config;
        double money = sup ? cfg.controlPointSuperMoney : cfg.controlPointMoney;
        int total = plugin.data.teamPoints.merge(team, cfg.controlPointPoints, Integer::sum);
        for (Map.Entry<UUID, Data.PlayerData> e : plugin.data.players.entrySet()) {
            Data.PlayerData pd = e.getValue();
            if (!team.equals(pd.team)) continue;
            pd.money += money;
            plugin.data.log("POINT", "CONTROL POINT #" + n, pd.name, money, sup ? "Captured super control point" : "Captured control point");
            if (plugin.server.getPlayerList().getPlayer(e.getKey()) != null) {
                plugin.notify(e.getKey(), "&a&l+ $" + Fmt.money(money) + "  &7Point #" + n + " captured!  &8|  &a$ &e" + Fmt.money(pd.money), 6);
            }
        }
        drop(level, pt.pos(), pool(sup).take());

        String col = Teams.color(team);
        broadcast("");
        broadcast(Fmt.PREFIX + " " + col + "&l" + team + " &acaptured " + (sup ? "&8&l✦ super " : "") + "&acontrol point &f#" + n + "&a!");
        broadcast("  &7+" + cfg.controlPointPoints + " pts  &8|  &7+$" + Fmt.money(money) + " per member  &8|  " + col + team + " &7now has &e" + total + " pts");
        broadcast("");
        save();
        for (ServerPlayer p : players()) sendWaypoints(p);
        if (owner.size() < points.size()) return false;
        end("&a&lAll control points have been captured!");
        return true;
    }

    private static void drop(ServerLevel level, BlockPos c, List<ItemStack> pool) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (ItemStack st : pool) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double r = rnd.nextDouble() * 2.5;
            ItemEntity item = new ItemEntity(level, c.getX() + 0.5 + Math.cos(a) * r, c.getY() + 1.5, c.getZ() + 0.5 + Math.sin(a) * r,
                st.copy(), rnd.nextDouble(-0.1, 0.1), 0.2, rnd.nextDouble(-0.1, 0.1));
            item.setDefaultPickUpDelay();
            level.addFreshEntity(item);
        }
    }

    // ── locator bar ──────────────────────────────────────────────

    private static UUID waypointId(int n) {
        return UUID.nameUUIDFromBytes(("moneysmp-point-" + n).getBytes(StandardCharsets.UTF_8));
    }

    // uncaptured points are grey (netherite for super), captured ones take the team colour.
    // the client shows the bar whenever it has any waypoint, so this alone turns it on
    void sendWaypoints(ServerPlayer p) {
        if (!running) return;
        for (Map.Entry<Integer, Point> e : points.entrySet()) {
            int n = e.getKey();
            if (!p.level().dimension().equals(e.getValue().dim())) {
                p.connection.send(ClientboundTrackedWaypointPacket.removeWaypoint(waypointId(n)));
                continue;
            }
            String held = owner.get(n);
            Waypoint.Icon icon = new Waypoint.Icon();
            icon.color = Optional.of(held != null ? Teams.rgb(held) : superPoints.contains(n) ? NETHERITE : GRAY);
            p.connection.send(ClientboundTrackedWaypointPacket.addWaypointPosition(waypointId(n), icon, e.getValue().pos()));
        }
    }

    private void clearWaypoints(ServerPlayer p) {
        for (int n : points.keySet()) p.connection.send(ClientboundTrackedWaypointPacket.removeWaypoint(waypointId(n)));
    }
}
