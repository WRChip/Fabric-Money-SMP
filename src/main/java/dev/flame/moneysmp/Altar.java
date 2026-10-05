package dev.flame.moneysmp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.math.Transformation;
import dev.flame.moneysmp.mixin.DisplayInvoker;
import dev.flame.moneysmp.mixin.ItemDisplayInvoker;
import dev.flame.moneysmp.mixin.TextDisplayInvoker;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundTrackedWaypointPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.waypoints.Waypoint;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// Eight core fragments sit at capture rings near the world border; hold one like a control
// point and its fragment drops. No money or points. Carrying a fragment makes you glow,
// strips leather armour, keeps it out of your ender chest and drops it if you log out within
// the first 30 minutes. Clicking the altar at 0,0 with all eight starts a 30-minute ritual
// that ends with a mace falling out of the sky. ticked by MoneySMP
public final class Altar {
    static final String[] NAMES = {"Ember", "Frost", "Storm", "Stone", "Tide", "Void", "Dawn", "Dusk"};
    private static final String[] CODES = {"&c", "&b", "&e", "&7", "&9", "&5", "&6", "&d"};
    private static final int[] COLORS = {0xFF5555, 0x55FFFF, 0xFFFF55, 0xAAAAAA, 0x5555FF, 0xAA00AA, 0xFFAA00, 0xFF55FF};
    private static final int PURPLE = 0xC8A2FF;
    private static final int BLACK = 0x000000;
    private static final long[] WARNINGS = {15 * 60, 5 * 60, 60, 10};
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    // scoreboard tags on the pedestal, recipe text and floating mace, so they can be found again
    private static final String TAG = "moneysmp_altar";
    private static final String TAG_MACE = "moneysmp_altar_mace";
    private record Need(Item item, int count, String name) {}
    // what the altar takes besides the eight fragments
    private static final List<Need> NEEDS = List.of(
        new Need(Items.DIAMOND_BLOCK, 16, "Diamond Block"),
        new Need(Items.BREEZE_ROD, 1, "Breeze Rod"),
        new Need(Items.NETHERITE_INGOT, 16, "Netherite Ingot"),
        new Need(Items.ENCHANTED_GOLDEN_APPLE, 4, "Enchanted Golden Apple"));
    static Altar hooks;

    private final MoneySMP plugin;
    private Path file;
    boolean running;
    // stamped into every fragment so ones left over from an earlier event are just junk
    private long round;
    private final BlockPos[] spots = new BlockPos[8];
    private final boolean[] dropped = new boolean[8];
    private final Map<Integer, Map<String, Double>> progress = new HashMap<>();
    // last player seen with each fragment, so a change of hands is announced once
    private final String[] carrier = new String[8];
    // where a fragment was last seen out of anyone's hands: in a container (kind names the
    // block) or on the ground (kind null). cleared when a player is seen with it
    private record Where(String kind, ResourceKey<Level> dim, BlockPos pos) {}
    private final Where[] seenAt = new Where[8];
    // every chunk currently loaded, kept by the chunk events since ChunkMap doesn't share
    private final Set<LevelChunk> loaded = new HashSet<>();
    private long scanned;
    // low corner of the pedestal's 2x2x2 footprint, so the pedestal is centred on 0,0
    private BlockPos base;
    private long ritualStart;
    long ritualEnd;
    private String ritualBy;
    private long lastLeft;
    // one per carrier from the moment they first pick a fragment up: the pickup time, and
    // how much of the current day they have been online. entries outlive the event so a
    // player who was offline when it ended still gets their glow cleared
    private static final class Hold {
        long since;
        long dayStart;
        long played;
        // where they were last seen, for the offline roll call
        int lastX;
        int lastY = Integer.MIN_VALUE;
        int lastZ;
    }
    private final Map<UUID, Hold> held = new HashMap<>();
    // bumped when a fragment is taken back, so the copy still in someone's inventory is junk
    private final int[] gen = new int[8];
    private final Set<UUID> safe = new HashSet<>();
    private final Map<UUID, ServerBossEvent> timers = new HashMap<>();
    private int saveTimer;
    private int locateOnline;
    private int locateOffline;
    private final ServerBossEvent bar = new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
    private int phase;
    private float spin;

    Altar(MoneySMP plugin) {
        this.plugin = plugin;
        hooks = this;
    }

    private void broadcast(String msg) {
        plugin.server.getPlayerList().broadcastSystemMessage(Fmt.c(msg), false);
    }

    private List<ServerPlayer> players() {
        return plugin.server.getPlayerList().getPlayers();
    }

    private ServerLevel overworld() {
        return plugin.server.overworld();
    }

    // ── altar.json ───────────────────────────────────────────────

    void load(Path dir) {
        file = dir.resolve("altar.json");
        running = false;
        held.clear();
        safe.clear();
        progress.clear();
        ritualStart = 0;
        ritualEnd = 0;
        ritualBy = null;
        Arrays.fill(carrier, null);
        Arrays.fill(gen, 0);
        Arrays.fill(seenAt, null);
        if (!Files.exists(file)) return;
        try {
            JsonObject y = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if (y.has("held")) {
                for (Map.Entry<String, JsonElement> e : y.getAsJsonObject("held").entrySet()) {
                    JsonObject o = e.getValue().getAsJsonObject();
                    Hold h = new Hold();
                    h.since = o.get("since").getAsLong();
                    h.dayStart = o.get("day").getAsLong();
                    h.played = o.get("played").getAsLong();
                    if (o.has("y")) {
                        h.lastX = o.get("x").getAsInt();
                        h.lastY = o.get("y").getAsInt();
                        h.lastZ = o.get("z").getAsInt();
                    }
                    held.put(UUID.fromString(e.getKey()), h);
                }
            }
            // nobody could play while the server was down, so that time doesn't count against them
            long gap = y.has("tick") ? Math.max(0, System.currentTimeMillis() - y.get("tick").getAsLong()) : 0;
            for (Hold h : held.values()) h.dayStart += gap;
            if (!y.has("event")) return;
            JsonObject ev = y.getAsJsonObject("event");
            round = ev.get("round").getAsLong();
            JsonArray sp = ev.getAsJsonArray("spots");
            for (int i = 0; i < 8; i++) {
                JsonObject o = sp.get(i).getAsJsonObject();
                spots[i] = pos(o);
                dropped[i] = o.get("dropped").getAsBoolean();
                carrier[i] = o.has("carrier") ? o.get("carrier").getAsString() : null;
                gen[i] = o.has("gen") ? o.get("gen").getAsInt() : 0;
                if (o.has("seen")) {
                    JsonObject w = o.getAsJsonObject("seen");
                    seenAt[i] = new Where(w.has("kind") ? w.get("kind").getAsString() : null,
                        ResourceKey.create(Registries.DIMENSION, Identifier.parse(w.get("dim").getAsString())), pos(w));
                }
            }
            base = pos(ev.getAsJsonObject("altar"));
            if (ev.has("ritual")) {
                JsonObject r = ev.getAsJsonObject("ritual");
                ritualStart = r.get("start").getAsLong();
                ritualEnd = r.get("end").getAsLong();
                ritualBy = r.get("by").getAsString();
                lastLeft = Math.max(0, (ritualEnd - System.currentTimeMillis() + 999) / 1000);
            }
            running = true;
            int out = 0;
            for (boolean d : dropped) if (d) out++;
            MoneySMP.LOG.info("resuming altar event: {} of 8 fragments claimed{}", out, ritualEnd > 0 ? ", ritual running" : "");
        } catch (IOException | RuntimeException e) {
            MoneySMP.LOG.error("could not read altar.json", e);
        }
    }

    // also called at shutdown, so the saved clock is exact and downtime is measured right
    void save() {
        if (file == null) return;
        JsonObject y = new JsonObject();
        JsonObject s = new JsonObject();
        held.forEach((uid, h) -> {
            JsonObject o = new JsonObject();
            o.addProperty("since", h.since);
            o.addProperty("day", h.dayStart);
            o.addProperty("played", h.played);
            if (h.lastY != Integer.MIN_VALUE) {
                o.addProperty("x", h.lastX);
                o.addProperty("y", h.lastY);
                o.addProperty("z", h.lastZ);
            }
            s.add(uid.toString(), o);
        });
        y.add("held", s);
        y.addProperty("tick", System.currentTimeMillis());
        if (running) {
            JsonObject ev = new JsonObject();
            ev.addProperty("round", round);
            JsonArray sp = new JsonArray();
            for (int i = 0; i < 8; i++) {
                JsonObject o = json(spots[i]);
                o.addProperty("dropped", dropped[i]);
                if (carrier[i] != null) o.addProperty("carrier", carrier[i]);
                o.addProperty("gen", gen[i]);
                if (seenAt[i] != null) {
                    JsonObject w = json(seenAt[i].pos());
                    if (seenAt[i].kind() != null) w.addProperty("kind", seenAt[i].kind());
                    w.addProperty("dim", seenAt[i].dim().identifier().toString());
                    o.add("seen", w);
                }
                sp.add(o);
            }
            ev.add("spots", sp);
            ev.add("altar", json(base));
            if (ritualEnd > 0) {
                JsonObject r = new JsonObject();
                r.addProperty("start", ritualStart);
                r.addProperty("end", ritualEnd);
                r.addProperty("by", ritualBy);
                ev.add("ritual", r);
            }
            y.add("event", ev);
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(y));
        } catch (IOException e) {
            MoneySMP.LOG.error("could not write altar.json", e);
        }
    }

    private static JsonObject json(BlockPos pos) {
        JsonObject o = new JsonObject();
        o.addProperty("x", pos.getX());
        o.addProperty("y", pos.getY());
        o.addProperty("z", pos.getZ());
        return o;
    }

    private static BlockPos pos(JsonObject o) {
        return new BlockPos(o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt());
    }

    // ── fragments ────────────────────────────────────────────────

    private ItemStack fragment(int n) {
        ItemStack st = new ItemStack(Items.ECHO_SHARD);
        st.set(DataComponents.ITEM_NAME, Fmt.c(CODES[n] + "&l" + NAMES[n] + " Fragment"));
        st.set(DataComponents.LORE, new ItemLore(List.of(
            Fmt.c("&7One of the eight Fragments of the World &8· " + (n + 1) + " of 8").withStyle(Style.EMPTY.withItalic(false)),
            Fmt.c("&8Bring all eight to the altar at 0, 0").withStyle(Style.EMPTY.withItalic(false)))));
        st.set(DataComponents.MAX_STACK_SIZE, 1);
        st.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        // this fragment's own shard from the resource pack
        st.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("moneysmp", "fragment_" + NAMES[n].toLowerCase(Locale.ROOT)));
        CompoundTag tag = new CompoundTag();
        tag.putLong("moneysmp_round", round);
        tag.putInt("moneysmp_fragment", n);
        tag.putInt("moneysmp_gen", gen[n]);
        st.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return st;
    }

    // the stack's tag if it is a fragment from the running event, else null
    private static CompoundTag tag(ItemStack st) {
        if (hooks == null || !hooks.running) return null;
        CustomData data = st.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        CompoundTag tag = data.copyTag();
        return tag.getLongOr("moneysmp_round", -1) == hooks.round ? tag : null;
    }

    // which fragment of the running event a stack is, or -1
    public static int index(ItemStack st) {
        CompoundTag tag = tag(st);
        if (tag == null) return -1;
        int n = tag.getIntOr("moneysmp_fragment", -1);
        return n >= 0 && n < 8 && tag.getIntOr("moneysmp_gen", 0) == hooks.gen[n] ? n : -1;
    }

    // a fragment somebody forfeited: right event, but its number has since been reissued
    private static int stale(ItemStack st) {
        CompoundTag tag = tag(st);
        if (tag == null) return -1;
        int n = tag.getIntOr("moneysmp_fragment", -1);
        return n >= 0 && n < 8 && tag.getIntOr("moneysmp_gen", 0) != hooks.gen[n] ? n : -1;
    }

    // a fragment, or a shulker box or bundle with one packed inside however deep
    public static boolean carries(ItemStack st) {
        if (index(st) >= 0) return true;
        ItemContainerContents box = st.get(DataComponents.CONTAINER);
        if (box != null) for (ItemStack in : box.nonEmptyItems()) if (carries(in)) return true;
        BundleContents bundle = st.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null) for (ItemStack in : bundle.items()) if (carries(in)) return true;
        return false;
    }

    // what may never go in an ender chest: a fragment or a mace, or anything with one inside
    public static boolean stash(ItemStack st) {
        if (st.is(Items.MACE) || index(st) >= 0) return true;
        ItemContainerContents box = st.get(DataComponents.CONTAINER);
        if (box != null) for (ItemStack in : box.nonEmptyItems()) if (stash(in)) return true;
        BundleContents bundle = st.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null) for (ItemStack in : bundle.items()) if (stash(in)) return true;
        return false;
    }

    // EntityMixin: a fragment item killed or discarded with its stack still on it (the void,
    // /kill, anything that got past invulnerability) goes back to its spot. pickups and
    // hoppers empty the stack before discarding, and as a second check nobody online may
    // be holding it
    public static void destroyed(ItemEntity it) {
        if (hooks == null || !hooks.running || !(it.level() instanceof ServerLevel)) return;
        Set<Integer> found = new HashSet<>();
        collect(it.getItem(), found);
        if (found.isEmpty()) return;
        Set<Integer> carried = new HashSet<>();
        for (ServerPlayer p : hooks.players()) {
            Inventory inv = p.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) collect(inv.getItem(i), carried);
            collect(p.containerMenu.getCarried(), carried);
        }
        for (int n : found) {
            if (carried.contains(n)) continue;
            hooks.reissue(n, it, hooks.overworld(), hooks.spots[n]);
            hooks.broadcast(Fmt.PREFIX + " &7The " + CODES[n] + NAMES[n] + " &7fragment was destroyed and is back at its spot &8(" + hooks.spots[n].getX() + ", " + hooks.spots[n].getZ() + ")&7.");
        }
    }

    void chunkLoaded(LevelChunk chunk) {
        loaded.add(chunk);
    }

    void chunkUnloaded(LevelChunk chunk) {
        loaded.remove(chunk);
    }

    // every fragment lying loose or sitting in a loaded container. what it finds replaces the
    // old record; what it can't see (unloaded chunks, offline inventories) keeps the last one.
    // unopened loot chests are skipped, since reading them would roll their loot
    private void scan() {
        long now = System.currentTimeMillis();
        if (!running || now - scanned < 5000) return;
        scanned = now;
        Where[] found = new Where[8];
        Set<Integer> in = new HashSet<>();
        for (LevelChunk chunk : loaded) {
            if (!(chunk.getLevel() instanceof ServerLevel level)) continue;
            for (BlockEntity be : chunk.getBlockEntities().values()) {
                if (!(be instanceof Container c) || (be instanceof RandomizableContainer r && r.getLootTable() != null)) continue;
                in.clear();
                for (int i = 0; i < c.getContainerSize(); i++) collect(c.getItem(i), in);
                for (int n : in) {
                    found[n] = new Where(BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).getPath().replace('_', ' '), level.dimension(), be.getBlockPos());
                }
            }
        }
        for (ServerLevel level : plugin.server.getAllLevels()) {
            for (ItemEntity it : level.getEntities(EntityType.ITEM, it -> carries(it.getItem()))) {
                in.clear();
                collect(it.getItem(), in);
                for (int n : in) found[n] = new Where(null, level.dimension(), it.blockPosition());
            }
        }
        for (int n = 0; n < 8; n++) {
            if (found[n] == null) continue;
            seenAt[n] = found[n];
            carrier[n] = null;
            // a copy out in the world means it has left its ring, however it got out
            dropped[n] = true;
        }
    }

    private static String place(Where w) {
        String dim = w.dim().equals(Level.OVERWORLD) ? "" : " &7in " + w.dim().identifier().getPath().replace('_', ' ');
        return (w.kind() == null ? "&7on the ground at &f" : "&7in a " + w.kind() + " at &f")
            + w.pos().getX() + ", " + w.pos().getY() + ", " + w.pos().getZ() + dim;
    }

    private static void collect(ItemStack st, Set<Integer> into) {
        int n = index(st);
        if (n >= 0) into.add(n);
        ItemContainerContents box = st.get(DataComponents.CONTAINER);
        if (box != null) for (ItemStack in : box.nonEmptyItems()) collect(in, into);
        BundleContents bundle = st.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null) for (ItemStack in : bundle.items()) collect(in, into);
    }

    // a dropped fragment glows, never despawns and shrugs off lava, fire and explosions.
    // ItemEntityMixin calls this whenever an item entity takes one
    public static void protect(ItemEntity it) {
        it.setGlowingTag(true);
        it.setInvulnerable(true);
        it.setUnlimitedLifetime();
    }

    // ── altar ────────────────────────────────────────────────────

    // the pedestal people see is an item display wearing the pack's model at twice its size;
    // the 2x2x2 of barriers under it, base being the low corner, is what they click and bump
    // into
    private List<BlockPos> footprint() {
        List<BlockPos> out = new ArrayList<>(8);
        for (int dx = 0; dx < 2; dx++) {
            for (int dy = 0; dy < 2; dy++) {
                for (int dz = 0; dz < 2; dz++) out.add(base.offset(dx, dy, dz));
            }
        }
        return out;
    }

    // doubles as the repair
    private void build(ServerLevel level) {
        for (BlockPos pos : footprint()) {
            if (!level.getBlockState(pos).is(Blocks.BARRIER)) level.setBlockAndUpdate(pos, Blocks.BARRIER.defaultBlockState());
        }
    }

    private void tearDown(ServerLevel level) {
        for (BlockPos pos : footprint()) {
            if (level.getBlockState(pos).is(Blocks.BARRIER)) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
    }

    boolean isAltar(Level level, BlockPos pos) {
        return running && level == overworld() && footprint().contains(pos);
    }

    // the footprint straddles the chunk seam at 0,0, so both sides are held
    private void forceChunks(ServerLevel level, boolean on) {
        for (int cx = base.getX() >> 4; cx <= base.getX() + 1 >> 4; cx++) {
            for (int cz = base.getZ() >> 4; cz <= base.getZ() + 1 >> 4; cz++) level.setChunkForced(cx, cz, on);
        }
    }

    private static String needsList() {
        List<String> out = new ArrayList<>();
        for (Need n : NEEDS) out.add("&f" + n.count() + "x " + n.name());
        return String.join("&7, ", out);
    }

    // the ingredient list that floats over the altar
    private static Component recipe() {
        MutableComponent c = Fmt.c("&d8x The Fragments of the World");
        for (Need n : NEEDS) c.append("\n").append(Fmt.c("&f" + n.count() + "x " + n.name()));
        return c;
    }

    private void text(ServerLevel level, double x, double y, double z, Component what, float scale) {
        Display.TextDisplay t = EntityType.TEXT_DISPLAY.create(level, EntitySpawnReason.EVENT);
        if (t == null) return;
        t.setPos(x, y, z);
        ((TextDisplayInvoker) t).invokeSetText(what);
        ((TextDisplayInvoker) t).invokeSetBackgroundColor(0);
        ((TextDisplayInvoker) t).invokeSetFlags(Display.TextDisplay.FLAG_SHADOW);
        ((DisplayInvoker) t).invokeSetBillboardConstraints(Display.BillboardConstraints.CENTER);
        ((DisplayInvoker) t).invokeSetTransformation(new Transformation(null, null, new Vector3f(scale), null));
        t.addTag(TAG);
        level.addFreshEntity(t);
    }

    // the pedestal, a mace hanging over it, its name, and the ingredients above, like a crafting altar
    private void spawnDisplays(ServerLevel level) {
        Display.ItemDisplay pedestal = EntityType.ITEM_DISPLAY.create(level, EntitySpawnReason.EVENT);
        Display.ItemDisplay item = EntityType.ITEM_DISPLAY.create(level, EntitySpawnReason.EVENT);
        if (pedestal == null || item == null) return;
        double x = base.getX() + 1.0;
        double z = base.getZ() + 1.0;
        ItemStack model = new ItemStack(Items.STONE);
        model.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("moneysmp", "pedestal"));
        pedestal.setPos(x, base.getY() + 1.0, z);
        ((ItemDisplayInvoker) pedestal).invokeSetItemStack(model);
        // NONE keeps the model at block scale; the other contexts shrink it like a held item
        ((ItemDisplayInvoker) pedestal).invokeSetItemTransform(ItemDisplayContext.NONE);
        ((DisplayInvoker) pedestal).invokeSetTransformation(new Transformation(null, null, new Vector3f(2f), null));
        pedestal.addTag(TAG);
        level.addFreshEntity(pedestal);
        // the cap tops out at +2 and the mace spans about +2.5 to +4.3. the result's name sits
        // above that at the size it always was, the ingredients smaller above it
        text(level, x, base.getY() + 4.9, z, Fmt.c("&5&lMace"), 1.6f);
        text(level, x, base.getY() + 5.5, z, recipe(), 1f);
        item.setPos(x, base.getY() + 3.4, z);
        ((ItemDisplayInvoker) item).invokeSetItemStack(new ItemStack(Items.MACE));
        ((ItemDisplayInvoker) item).invokeSetItemTransform(ItemDisplayContext.FIXED);
        ((DisplayInvoker) item).invokeSetTransformationInterpolationDuration(20);
        item.addTag(TAG);
        item.addTag(TAG_MACE);
        level.addFreshEntity(item);
    }

    private List<? extends Display> displays(ServerLevel level) {
        return level.getEntities(EntityTypeTest.forClass(Display.class), e -> e.getTags().contains(TAG));
    }

    // once a second: put the four back if any went missing, and turn the mace a notch.
    // the client eases each notch out over the second, so it reads as a steady spin
    private void tendDisplays(ServerLevel level) {
        List<? extends Display> found = displays(level);
        if (found.size() != 4) {
            found.forEach(Entity::discard);
            spawnDisplays(level);
            return;
        }
        spin += (float) Math.PI / 8;
        for (Display d : found) {
            if (!d.getTags().contains(TAG_MACE)) continue;
            ((DisplayInvoker) d).invokeSetTransformationInterpolationDelay(0);
            ((DisplayInvoker) d).invokeSetTransformation(new Transformation(null, new Quaternionf().rotationY(spin), new Vector3f(1.8f), null));
        }
    }

    // the first air block above the ground at x,z. the heightmap only answers for loaded
    // chunks, so this pulls the chunk in first (generating it if it has to)
    private static BlockPos surface(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    // ── event ────────────────────────────────────────────────────

    // null when it began, otherwise why it couldn't
    String start() {
        if (running) return "&cAn altar event is already running.";
        ServerLevel ow = overworld();
        WorldBorder border = ow.getWorldBorder();
        if (border.getSize() > 1_000_000) return "&cThere's no world border. Fragments spawn around it, so set one with &f/worldborder set <size> &cfirst.";
        round = System.currentTimeMillis();
        progress.clear();
        safe.clear();
        Arrays.fill(dropped, false);
        Arrays.fill(carrier, null);
        Arrays.fill(gen, 0);
        Arrays.fill(seenAt, null);
        ritualStart = 0;
        ritualEnd = 0;
        ritualBy = null;
        double half = border.getSize() / 2;
        for (int i = 0; i < 8; i++) {
            // evenly around the border, 45 degrees apart. a spot that lands in water is only
            // nudged in or out, so the spacing holds
            double a = (i + 0.5) * Math.PI / 4;
            for (double r : new double[]{0.8, 0.75, 0.85, 0.7, 0.9}) {
                spots[i] = surface(ow, (int) Math.round(border.getCenterX() + Math.cos(a) * half * r), (int) Math.round(border.getCenterZ() + Math.sin(a) * half * r));
                if (ow.getFluidState(spots[i].below()).isEmpty()) break;
            }
        }
        // centred on 0,0 and standing on the highest of its four columns
        int top = Integer.MIN_VALUE;
        for (int dx = -1; dx <= 0; dx++) {
            for (int dz = -1; dz <= 0; dz++) top = Math.max(top, surface(ow, dx, dz).getY());
        }
        base = new BlockPos(-1, top, -1);
        build(ow);
        // kept loaded for the whole event so the displays, repairs and the falling mace
        // don't depend on someone standing nearby
        forceChunks(ow, true);
        spawnDisplays(ow);
        running = true;
        save();

        broadcast("");
        broadcast(Fmt.PREFIX + " &5&l✦ ALTAR EVENT STARTED ✦");
        broadcast("  &dThe Fragments of the World &7lie near the world border, eight of them. Hold a ring like a control point and its fragment drops.");
        broadcast("  &7Carrying one makes you glow, bans leather armour and locks your ender chest.");
        broadcast("  &cLog out within " + plugin.config.altarLogoutMinutes + " minutes of picking one up and it drops where you stood.");
        broadcast("  &cHold one and you have to play at least " + playNeed() + " a day, or it goes back to its spot.");
        broadcast("  &7The altar at &f0, 0 &7asks for &d8x The Fragments of the World&7, " + needsList() + "&7. Click it with everything to begin the ritual.");
        broadcast("");
        for (ServerPlayer p : players()) sendWaypoints(p);
        return null;
    }

    void stop() {
        end(ritualEnd > 0 ? "&7The altar event was stopped and the ritual cancelled." : "&7The altar event was stopped.");
    }

    private void end(String reason) {
        // leftover fragments are junk from here on: stop them glowing and let them burn
        for (ServerLevel level : plugin.server.getAllLevels()) {
            for (ItemEntity it : level.getEntities(EntityType.ITEM, it -> carries(it.getItem()))) {
                it.setGlowingTag(false);
                it.setInvulnerable(false);
            }
        }
        ServerLevel ow = overworld();
        running = false;
        ritualStart = 0;
        ritualEnd = 0;
        ritualBy = null;
        progress.clear();
        safe.clear();
        bar.removeAllPlayers();
        timers.values().forEach(ServerBossEvent::removeAllPlayers);
        timers.clear();
        displays(ow).forEach(Entity::discard);
        tearDown(ow);
        forceChunks(ow, false);
        for (ServerPlayer p : players()) {
            clearWaypoints(p);
            if (held.remove(p.getUUID()) != null) p.setGlowingTag(false);
        }
        save();
        broadcast("");
        broadcast(Fmt.PREFIX + " " + reason);
        broadcast("");
    }

    void join(ServerPlayer p) {
        if (!running) {
            if (held.remove(p.getUUID()) != null) {
                p.setGlowingTag(false);
                save();
            }
            return;
        }
        sendWaypoints(p);
        if (ritualEnd > 0) bar.addPlayer(p);
        // a carrier can't slip back in quietly
        Set<Integer> found = new HashSet<>();
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) collect(inv.getItem(i), found);
        if (found.isEmpty()) return;
        List<String> names = new ArrayList<>();
        for (int n : found) names.add(CODES[n] + NAMES[n]);
        BlockPos at = p.blockPosition();
        broadcast(Fmt.PREFIX + " &f" + p.getScoreboardName() + " &7logged on carrying the " + String.join("&7, ", names)
            + " &7fragment" + (names.size() > 1 ? "s" : "") + " at &f" + at.getX() + ", " + at.getY() + ", " + at.getZ() + "&7.");
    }

    void leave(ServerPlayer p) {
        bar.removePlayer(p);
        ServerBossEvent timer = timers.remove(p.getUUID());
        if (timer != null) timer.removeAllPlayers();
        // a restart isn't a logout
        if (!running || !plugin.server.isRunning()) return;
        UUID uid = p.getUUID();
        Hold h = held.get(uid);
        if (h == null || System.currentTimeMillis() - h.since >= plugin.config.altarLogoutMinutes * 60_000L) return;
        // the inventory is left alone: by now it may already be saved. a fresh copy of each
        // fragment appears where they stood and the one they keep is junk
        Set<Integer> lost = new HashSet<>();
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) collect(inv.getItem(i), lost);
        held.remove(uid);
        safe.remove(uid);
        p.setGlowingTag(false);
        if (lost.isEmpty()) return;
        BlockPos at = p.blockPosition();
        ServerLevel level = (ServerLevel) p.level();
        List<String> names = new ArrayList<>();
        for (int n : lost) {
            reissue(n, null, level, at);
            names.add(CODES[n] + NAMES[n]);
        }
        broadcast(Fmt.PREFIX + " &f" + p.getScoreboardName() + " &clogged out carrying the " + String.join("&7, ", names)
            + " &cfragment" + (names.size() > 1 ? "s" : "") + "! &7Dropped at &f" + at.getX() + ", " + at.getY() + ", " + at.getZ() + "&7.");
    }

    // called by MoneySMP every 5 ticks, like the control point beams
    void draw() {
        if (!running) return;
        phase++;
        ServerLevel ow = overworld();
        int r = plugin.config.controlPointRadius;
        for (int i = 0; i < 8; i++) {
            int color = dropped[i] ? BLACK : COLORS[i];
            ControlPoints.ring(ow, spots[i], color, r, phase);
            ControlPoints.beam(ow, spots[i], color);
        }
        ControlPoints.beam(ow, base, PURPLE);
    }

    // once a second: capture progress, the ritual clock and the altar's repair
    void tick() {
        if (!running) return;
        ServerLevel ow = overworld();
        if (ow.hasChunk(base.getX() >> 4, base.getZ() >> 4)) {
            build(ow);
            tendDisplays(ow);
        }
        Config cfg = plugin.config;
        for (int i = 0; i < 8; i++) {
            if (dropped[i]) continue;
            Map<String, Integer> present = new HashMap<>();
            List<ServerPlayer> inside = new ArrayList<>();
            for (ServerPlayer p : ow.players()) {
                if (p.isSpectator() || !ControlPoints.inRing(p, spots[i], cfg.controlPointRadius, cfg.controlPointHeight)) continue;
                inside.add(p);
                String team = plugin.data.team(p.getUUID());
                if (team != null) present.merge(team, 1, Integer::sum);
            }
            String top = null;
            int topCount = 0;
            int second = 0;
            for (Map.Entry<String, Integer> e : present.entrySet()) {
                if (e.getValue() > topCount) {
                    second = topCount;
                    topCount = e.getValue();
                    top = e.getKey();
                } else if (e.getValue() > second) {
                    second = e.getValue();
                }
            }
            Map<String, Double> prog = progress.computeIfAbsent(i, k -> new HashMap<>());
            if (top != null && topCount > second) {
                double gain = (topCount - second) * cfg.controlPointPercentPer30s / 30;
                if (prog.merge(top, gain, Double::sum) >= 100) {
                    release(i, top);
                    continue;
                }
            }
            if (inside.isEmpty()) continue;
            StringBuilder sb = new StringBuilder(CODES[i]).append(NAMES[i]).append(" fragment");
            prog.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .forEach(e -> sb.append("  ").append(Teams.color(e.getKey())).append(e.getKey())
                    .append(" &f").append((int) Math.floor(e.getValue())).append('%'));
            if (top != null && topCount == second) sb.append("  &c&lCONTESTED");
            for (ServerPlayer p : inside) plugin.notify(p.getUUID(), sb.toString(), 2);
        }
        playtime();
        timers();
        locate();
        if (ritualEnd > 0) ritual();
    }

    // each carrier's own bar: how much of today's hour is still owed (filling as they play),
    // and how long the logout grace has left while it applies
    private void timers() {
        long now = System.currentTimeMillis();
        long need = plugin.config.altarDailyPlayMinutes * 60_000L;
        long grace = plugin.config.altarLogoutMinutes * 60_000L;
        for (ServerPlayer p : players()) {
            UUID uid = p.getUUID();
            Hold h = held.get(uid);
            if (h == null) {
                ServerBossEvent gone = timers.remove(uid);
                if (gone != null) gone.removeAllPlayers();
                continue;
            }
            ServerBossEvent bar = timers.computeIfAbsent(uid, k -> new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.PINK, BossEvent.BossBarOverlay.NOTCHED_6));
            bar.addPlayer(p);
            long left = Math.max(0, need - h.played);
            String text = left == 0 ? "&a&l✔ &aToday's " + playNeed() + " is done" : "&d&l⌛ &dPlay &f" + Fmt.timeAgo((left + 999) / 1000) + " &dmore today";
            long hold = grace - (now - h.since);
            if (hold > 0) text += "  &8|  &cDon't log out for &f" + Fmt.timeAgo((hold + 999) / 1000);
            bar.setName(Fmt.c(text));
            bar.setProgress((float) Math.min(1, (double) h.played / need));
        }
    }

    // where the carriers are: online ones every altarLocateOnlineMinutes with their live
    // position, offline ones every altarLocateOfflineMinutes with where they were last seen
    private void locate() {
        boolean online = ++locateOnline >= plugin.config.altarLocateOnlineMinutes * 60;
        boolean offline = ++locateOffline >= plugin.config.altarLocateOfflineMinutes * 60;
        if (!online && !offline) return;
        if (online) locateOnline = 0;
        if (offline) locateOffline = 0;
        if (online) scan();
        Map<String, List<String>> by = new LinkedHashMap<>();
        for (int n = 0; n < 8; n++) {
            if (carrier[n] != null) by.computeIfAbsent(carrier[n], k -> new ArrayList<>()).add(CODES[n] + NAMES[n]);
        }
        List<String> lines = new ArrayList<>();
        by.forEach((name, frags) -> {
            ServerPlayer p = plugin.server.getPlayerList().getPlayerByName(name);
            if (p != null && online) {
                BlockPos at = p.blockPosition();
                lines.add("  &f" + name + " &8» " + String.join("&7, ", frags) + " &7at &f" + at.getX() + ", " + at.getY() + ", " + at.getZ());
            } else if (p == null && offline) {
                UUID uid = plugin.data.lookup(name);
                Hold h = uid == null ? null : held.get(uid);
                String seen = h == null || h.lastY == Integer.MIN_VALUE ? "" : ", last seen at &7" + h.lastX + ", " + h.lastY + ", " + h.lastZ;
                lines.add("  &f" + name + " &8» " + String.join("&7, ", frags) + " &8(offline" + seen + "&8)");
            }
        });
        if (online) {
            for (int n = 0; n < 8; n++) {
                if (carrier[n] == null && seenAt[n] != null) lines.add("  " + CODES[n] + NAMES[n] + " &8» " + place(seenAt[n]));
            }
        }
        if (lines.isEmpty()) return;
        broadcast("");
        broadcast(Fmt.PREFIX + " &d&lFragment carriers right now:");
        for (String line : lines) broadcast(line);
        broadcast("");
    }

    // an hour a day, or the fragment goes back to its spot. carriers accrue online time a
    // second at a time; each day is judged when it ends, online or not
    private void playtime() {
        long now = System.currentTimeMillis();
        long day = plugin.config.altarDayMinutes * 60_000L;
        long need = plugin.config.altarDailyPlayMinutes * 60_000L;
        for (ServerPlayer p : players()) {
            Hold h = held.get(p.getUUID());
            if (h != null) h.played += 1000;
        }
        for (Iterator<Map.Entry<UUID, Hold>> it = held.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Hold> e = it.next();
            Hold h = e.getValue();
            if (now - h.dayStart < day) continue;
            if (h.played >= need) {
                h.dayStart += day;
                h.played = 0;
                continue;
            }
            it.remove();
            forfeit(e.getKey());
        }
        // the played counters only live in memory between saves
        if (++saveTimer >= 60) {
            saveTimer = 0;
            save();
        }
    }

    // every fragment the player is known to carry goes back to its spot; their copy turns
    // to junk that sweep() deletes when they are next seen
    private void forfeit(UUID uid) {
        Data.PlayerData pd = plugin.data.players.get(uid);
        String name = pd == null || pd.name == null ? uid.toString() : pd.name;
        ServerPlayer p = plugin.server.getPlayerList().getPlayer(uid);
        if (p != null) p.setGlowingTag(false);
        safe.remove(uid);
        for (int n = 0; n < 8; n++) {
            if (!name.equals(carrier[n])) continue;
            reissue(n, null, overworld(), spots[n]);
            broadcast(Fmt.PREFIX + " &7The " + CODES[n] + NAMES[n] + " &7fragment left &f" + name + "&7, who didn't play " + playNeed()
                + " today, and is back at its spot &8(" + spots[n].getX() + ", " + spots[n].getZ() + ")&7.");
        }
        save();
    }

    // a fresh copy of fragment n appears at `at`, and every older copy is junk: the loaded
    // ones lying around are cleared here, the rest by sweep() when they are next seen.
    // `dying` is an item being removed right now, if any, which must not be discarded again
    private void reissue(int n, ItemEntity dying, ServerLevel level, BlockPos at) {
        gen[n]++;
        dropped[n] = true;
        carrier[n] = null;
        seenAt[n] = null;
        for (ServerLevel lv : plugin.server.getAllLevels()) {
            for (ItemEntity it : lv.getEntities(EntityType.ITEM, it -> it != dying && !it.isRemoved() && stale(it.getItem()) >= 0)) it.discard();
        }
        level.getChunk(at.getX() >> 4, at.getZ() >> 4);
        ItemEntity item = new ItemEntity(level, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, fragment(n), 0, 0.2, 0);
        item.setDefaultPickUpDelay();
        level.addFreshEntity(item);
        save();
    }

    private static int fragmentNamed(String name) {
        for (int n = 0; n < 8; n++) if (NAMES[n].equalsIgnoreCase(name)) return n;
        return -1;
    }

    // admin: a fresh copy straight into someone's hands. null when done, otherwise why not
    String give(ServerPlayer to, String name) {
        if (!running) return "&cNo altar event is running.";
        int n = fragmentNamed(name);
        if (n < 0) return "&cNo such fragment. They are: &f" + String.join(", ", NAMES);
        reissue(n, null, (ServerLevel) to.level(), to.blockPosition());
        // reissue dropped it at their feet; move it into the inventory when there is room
        for (ItemEntity it : ((ServerLevel) to.level()).getEntities(EntityType.ITEM, it -> index(it.getItem()) == n)) {
            if (!to.getInventory().add(it.getItem().copy())) continue;
            it.setItem(ItemStack.EMPTY);
            it.discard();
        }
        broadcast(Fmt.PREFIX + " &7An admin handed the " + CODES[n] + NAMES[n] + " &7fragment to &f" + to.getScoreboardName() + "&7.");
        return null;
    }

    // admin: put a fragment back at its ring, wherever its last copy went
    String respawn(String name) {
        if (!running) return "&cNo altar event is running.";
        int n = fragmentNamed(name);
        if (n < 0) return "&cNo such fragment. They are: &f" + String.join(", ", NAMES);
        reissue(n, null, overworld(), spots[n]);
        broadcast(Fmt.PREFIX + " &7An admin put the " + CODES[n] + NAMES[n] + " &7fragment back at its spot &8(" + spots[n].getX() + ", " + spots[n].getZ() + ")&7.");
        for (ServerPlayer o : players()) sendWaypoints(o);
        return null;
    }

    private String playNeed() {
        int mins = plugin.config.altarDailyPlayMinutes;
        return mins == 60 ? "an hour" : mins + " minutes";
    }

    // admin: where a carrier's clocks stand. null when shown, otherwise why not
    String showTimers(CommandSourceStack s, String name) {
        UUID uid = plugin.data.lookup(name);
        Hold h = uid == null ? null : held.get(uid);
        if (h == null) return "&f" + name + " &7isn't carrying a fragment.";
        long now = System.currentTimeMillis();
        long grace = Math.max(0, plugin.config.altarLogoutMinutes * 60_000L - (now - h.since));
        long need = Math.max(0, plugin.config.altarDailyPlayMinutes * 60_000L - h.played);
        long day = Math.max(0, h.dayStart + plugin.config.altarDayMinutes * 60_000L - now);
        s.sendSystemMessage(Fmt.p("&f" + name + "&7: logout grace &f" + (grace == 0 ? "over" : Fmt.timeAgo((grace + 999) / 1000) + " left")
            + "&7, today's play &f" + (need == 0 ? "done" : Fmt.timeAgo((need + 999) / 1000) + " to go") + "&7, day resets in &f" + Fmt.timeAgo((day + 999) / 1000) + "&7."));
        return null;
    }

    // admin: the grace is over and today's hour counts as played
    String skipTimers(String name) {
        UUID uid = plugin.data.lookup(name);
        Hold h = uid == null ? null : held.get(uid);
        if (h == null) return "&f" + name + " &7isn't carrying a fragment.";
        long now = System.currentTimeMillis();
        h.since = Math.min(h.since, now - plugin.config.altarLogoutMinutes * 60_000L);
        h.played = Math.max(h.played, plugin.config.altarDailyPlayMinutes * 60_000L);
        safe.add(uid);
        save();
        ServerPlayer p = plugin.server.getPlayerList().getPlayer(uid);
        if (p != null) p.sendSystemMessage(Fmt.p("&aAn admin cleared your fragment timers: you may log out, and today's " + playNeed() + " counts as done."));
        return null;
    }

    // /altar: where every fragment is and who has it
    void show(CommandSourceStack s) {
        if (!running) {
            s.sendSystemMessage(Fmt.p("&7No altar event is running."));
            return;
        }
        scan();
        int out = 0;
        for (boolean d : dropped) if (d) out++;
        s.sendSystemMessage(Component.empty());
        s.sendSystemMessage(Fmt.p("&d&lThe Fragments of the World  &8|  &f" + out + "&7/8 claimed"
            + (ritualEnd > 0 ? "  &8|  &dritual: &f" + Fmt.timeAgo(lastLeft) + " &7left" : "")));
        for (int n = 0; n < 8; n++) {
            String where;
            if (!dropped[n]) where = "&7still at its ring &8(" + spots[n].getX() + ", " + spots[n].getZ() + ")";
            else if (carrier[n] != null) where = "&7carried by &f" + carrier[n] + (plugin.server.getPlayerList().getPlayerByName(carrier[n]) == null ? " &8(offline)" : "");
            else if (seenAt[n] != null) where = place(seenAt[n]);
            else where = "&7claimed, whereabouts unknown";
            s.sendSystemMessage(Fmt.c("  " + CODES[n] + "&l" + NAMES[n] + " &8» " + where));
        }
        s.sendSystemMessage(Component.empty());
    }

    private void release(int n, String team) {
        ServerLevel ow = overworld();
        BlockPos c = spots[n];
        dropped[n] = true;
        progress.remove(n);
        ItemEntity item = new ItemEntity(ow, c.getX() + 0.5, c.getY() + 1, c.getZ() + 0.5, fragment(n), 0, 0.2, 0);
        item.setDefaultPickUpDelay();
        ow.addFreshEntity(item);
        broadcast("");
        broadcast(Fmt.PREFIX + " " + Teams.color(team) + "&l" + team + " &dclaimed the " + CODES[n] + "&l" + NAMES[n] + " fragment&d! &7It dropped at &f" + c.getX() + ", " + c.getZ() + "&7.");
        broadcast("");
        save();
        for (ServerPlayer p : players()) sendWaypoints(p);
    }

    // every tick: who carries what, and what that costs them
    void sweep() {
        if (!running) return;
        long now = System.currentTimeMillis();
        long grace = plugin.config.altarLogoutMinutes * 60_000L;
        boolean dirty = false;
        Set<Integer> found = new HashSet<>();
        for (ServerPlayer p : players()) {
            found.clear();
            Inventory inv = p.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack st = inv.getItem(i);
                int old = stale(st);
                if (old >= 0) {
                    inv.setItem(i, ItemStack.EMPTY);
                    p.sendSystemMessage(Fmt.p("&cThe " + CODES[old] + NAMES[old] + " &cfragment is no longer yours; it was reissued while you were away."));
                    continue;
                }
                collect(st, found);
            }
            collect(p.containerMenu.getCarried(), found);
            UUID uid = p.getUUID();
            // a carrier record that this player no longer backs up is cleared, so /altar
            // doesn't keep naming someone who lost it
            for (int n = 0; n < 8; n++) {
                if (p.getScoreboardName().equals(carrier[n]) && !found.contains(n)) {
                    carrier[n] = null;
                    dirty = true;
                }
            }
            if (found.isEmpty()) {
                if (held.remove(uid) != null) {
                    safe.remove(uid);
                    p.setGlowingTag(false);
                    dirty = true;
                }
                continue;
            }
            Hold h = held.get(uid);
            if (h == null) {
                h = new Hold();
                h.since = now;
                h.dayStart = now;
                held.put(uid, h);
                warn(p);
                dirty = true;
            } else if (now - h.since >= grace && safe.add(uid)) {
                p.sendSystemMessage(Fmt.p("&aYou've carried a fragment for " + plugin.config.altarLogoutMinutes + " minutes. Logging out is safe now."));
            }
            if (!p.hasGlowingTag()) p.setGlowingTag(true);
            BlockPos at = p.blockPosition();
            h.lastX = at.getX();
            h.lastY = at.getY();
            h.lastZ = at.getZ();
            strip(p);
            String name = p.getScoreboardName();
            for (int n : found) {
                seenAt[n] = null;
                if (name.equals(carrier[n])) continue;
                carrier[n] = name;
                broadcast(Fmt.PREFIX + " &f" + name + " &7now carries the " + CODES[n] + NAMES[n] + " fragment&7.");
                dirty = true;
            }
        }
        if (dirty) save();
    }

    private void warn(ServerPlayer p) {
        int mins = plugin.config.altarLogoutMinutes;
        p.sendSystemMessage(Fmt.c(""));
        p.sendSystemMessage(Fmt.p("&5&l✦ &dYou are carrying a core fragment!"));
        p.sendSystemMessage(Fmt.c("  &7Everyone can see you glow. Leather armour comes off, and it can't go in your ender chest."));
        p.sendSystemMessage(Fmt.c("  &c&lDo not log out for the next " + mins + " minutes &cor it drops where you stand."));
        p.sendSystemMessage(Fmt.c("  &cPlay at least " + playNeed() + " a day while you hold it, or it goes back to its spot."));
        p.sendSystemMessage(Fmt.c(""));
        plugin.notify(p.getUUID(), "&c&lDon't log out for " + mins + " minutes!  &7You carry a core fragment", 8);
    }

    private void strip(ServerPlayer p) {
        for (EquipmentSlot slot : ARMOR) {
            ItemStack st = p.getItemBySlot(slot);
            if (!(st.is(Items.LEATHER_HELMET) || st.is(Items.LEATHER_CHESTPLATE) || st.is(Items.LEATHER_LEGGINGS) || st.is(Items.LEATHER_BOOTS))) continue;
            p.setItemSlot(slot, ItemStack.EMPTY);
            if (!p.getInventory().add(st)) p.drop(st, false);
            plugin.notify(p.getUUID(), "&cNo leather armour while you carry a fragment!", 4);
        }
    }

    // ── ritual ───────────────────────────────────────────────────

    // right click on the altar. every click on it is swallowed so nothing gets placed against it
    InteractionResult use(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        if (!(player instanceof ServerPlayer p) || !isAltar(level, hit.getBlockPos())) return InteractionResult.PASS;
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.SUCCESS;
        if (ritualEnd > 0) {
            p.sendSystemMessage(Fmt.p("&dThe ritual is already under way. &7" + Fmt.timeAgo(lastLeft) + " to go."));
            return InteractionResult.SUCCESS;
        }
        Inventory inv = p.getInventory();
        int[] slot = new int[8];
        Arrays.fill(slot, -1);
        for (int i = 0; i < inv.getContainerSize(); i++) {
            int n = index(inv.getItem(i));
            if (n >= 0 && slot[n] < 0) slot[n] = i;
        }
        List<String> missing = new ArrayList<>();
        for (int n = 0; n < 8; n++) if (slot[n] < 0) missing.add(CODES[n] + NAMES[n] + " fragment");
        for (Need need : NEEDS) {
            int lack = need.count() - inv.countItem(need.item());
            if (lack > 0) missing.add("&f" + lack + "x " + need.name());
        }
        if (!missing.isEmpty()) {
            p.sendSystemMessage(Fmt.p("&7The altar still needs: " + String.join("&7, ", missing) + "&7."));
            return InteractionResult.SUCCESS;
        }
        for (int s : slot) inv.setItem(s, ItemStack.EMPTY);
        for (Need need : NEEDS) inv.clearOrCountMatchingItems(st -> st.is(need.item()), need.count(), p.inventoryMenu.getCraftSlots());
        long now = System.currentTimeMillis();
        ritualStart = now;
        ritualEnd = now + plugin.config.altarRitualMinutes * 60_000L;
        ritualBy = p.getScoreboardName();
        lastLeft = plugin.config.altarRitualMinutes * 60L;
        for (ServerPlayer o : players()) bar.addPlayer(o);
        ritual();
        lightning(overworld());
        save();
        broadcast("");
        broadcast(Fmt.PREFIX + " &5&l✦ THE RITUAL HAS BEGUN ✦");
        broadcast("  &f" + ritualBy + " &7laid &dThe Fragments of the World &7and the offering on the altar at &f0, 0&7.");
        broadcast("  &7In &d" + plugin.config.altarRitualMinutes + " minutes &7a mace falls from the sky there, for whoever is standing under it.");
        broadcast("");
        return InteractionResult.SUCCESS;
    }

    private void ritual() {
        long left = Math.max(0, (ritualEnd - System.currentTimeMillis() + 999) / 1000);
        for (long w : WARNINGS) {
            if (lastLeft > w && left <= w && left > 0) broadcast(Fmt.PREFIX + " &d" + Fmt.timeAgo(left) + " &7until the ritual at &f0, 0 &7completes!");
        }
        lastLeft = left;
        if (left == 0) {
            finish();
            return;
        }
        bar.setName(Fmt.c("&5&l✦ Ritual &d" + Fmt.timeAgo(left) + "  &8|  &7started by &f" + ritualBy));
        bar.setProgress((float) Math.min(1, (double) (System.currentTimeMillis() - ritualStart) / (ritualEnd - ritualStart)));
    }

    private void finish() {
        ServerLevel ow = overworld();
        ow.getChunk(base.getX() >> 4, base.getZ() >> 4);
        lightning(ow);
        ItemEntity mace = new ItemEntity(ow, base.getX() + 1.0, base.getY() + 30, base.getZ() + 1.0, new ItemStack(Items.MACE), 0, 0, 0);
        mace.setUnlimitedLifetime();
        mace.setInvulnerable(true);
        mace.setDefaultPickUpDelay();
        ow.addFreshEntity(mace);
        end("&5&lThe ritual is complete! &dA mace is falling from the sky at &f0, 0&d.");
    }

    private void lightning(ServerLevel level) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.EVENT);
        if (bolt == null) return;
        bolt.setPos(base.getX() + 1.0, base.getY() + 2, base.getZ() + 1.0);
        bolt.setVisualOnly(true);
        level.addFreshEntity(bolt);
    }

    // ── locator bar ──────────────────────────────────────────────

    private static UUID waypointId(String key) {
        return UUID.nameUUIDFromBytes(("moneysmp-altar-" + key).getBytes(StandardCharsets.UTF_8));
    }

    // every spot in its own colour, black once its fragment is out, plus the altar. overworld only
    void sendWaypoints(ServerPlayer p) {
        if (!running) return;
        boolean here = p.level() == overworld();
        for (int i = 0; i < 8; i++) {
            if (!here) {
                p.connection.send(ClientboundTrackedWaypointPacket.removeWaypoint(waypointId(NAMES[i])));
                continue;
            }
            Waypoint.Icon icon = new Waypoint.Icon();
            icon.color = Optional.of(dropped[i] ? BLACK : COLORS[i]);
            p.connection.send(ClientboundTrackedWaypointPacket.addWaypointPosition(waypointId(NAMES[i]), icon, spots[i]));
        }
        if (!here) {
            p.connection.send(ClientboundTrackedWaypointPacket.removeWaypoint(waypointId("altar")));
            return;
        }
        Waypoint.Icon icon = new Waypoint.Icon();
        icon.color = Optional.of(PURPLE);
        p.connection.send(ClientboundTrackedWaypointPacket.addWaypointPosition(waypointId("altar"), icon, base));
    }

    private void clearWaypoints(ServerPlayer p) {
        for (String n : NAMES) p.connection.send(ClientboundTrackedWaypointPacket.removeWaypoint(waypointId(n)));
        p.connection.send(ClientboundTrackedWaypointPacket.removeWaypoint(waypointId("altar")));
    }
}
