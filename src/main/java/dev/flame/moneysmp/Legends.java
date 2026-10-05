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
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DamageResistant;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.scores.PlayerTeam;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

// The Altar SMP arc 1 to 3 legendaries: their items, the altars that craft them (each takes
// items and money from the crafter's balance, once), the vampire / pale rot / human species and
// the contagion ritual. What the weapons do lives in Weapons. ticked by MoneySMP
public final class Legends {
    static final String KEY = "moneysmp_legend";
    private static final String TAG = "moneysmp_legend_altar";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    static final TagKey<net.minecraft.world.damagesource.DamageType> PROOF =
        TagKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath("moneysmp", "legendary_proof"));
    static Legends hooks;

    record Def(String id, String name, Item base, String model, boolean weapon, List<String> lore) {}
    static final Map<String, Def> DEFS = new LinkedHashMap<>();

    private static void def(String id, String name, Item base, String model, boolean weapon, String... lore) {
        DEFS.put(id, new Def(id, name, base, model, weapon, List.of(lore)));
    }

    static {
        def("bone_blade", "&fBone Blade", Items.NETHERITE_SWORD, "moneysmp:bone_blade", true,
            "&8A blade made from the oldest bones known to mankind.", "&8Some say the bones are still alive.", "",
            "&eSkeletal Leap &6(RMB)", "&7Leap where you look, gaining &fSpeed III &7for a few seconds.", "&830s cooldown", "",
            "&eBone Cage &6(Crouch-RMB)", "&7Throw a fast bone that stuns whatever it hits for 5 seconds.", "&860s cooldown");
        def("bloodlust", "&cBloodlust", Items.NETHERITE_SWORD, "moneysmp:bloodlust", true,
            "&8Some say they can still hear the screams...", "",
            "&eInfection &6(0+ kills)", "&7Hits on players have a chance to make them bleed.", "",
            "&eSpeed II &6(1+ kills)", "&7While held.", "",
            "&eBlood Tracker &6(2+ kills)", "&7See a trail to every player within 30 blocks while held.", "",
            "&eBlood Trail &6(3+ kills, RMB)", "&7Sink into a pool of blood: unseen and untouchable for 10s.", "&81 minute cooldown", "",
            "&eStrength I &6(4+ kills)", "&7While held.", "",
            "&eBlood Hook &6(5+ kills, Crouch-RMB)", "&7Throw a blood chain that pulls whatever it touches to you.", "&830s cooldown");
        def("frost_scythe", "&bFrost Scythe", Items.NETHERITE_SWORD, "moneysmp:frost_scythe", true,
            "&8An ancient scythe used to harvest seas of ice.", "&8It is cool to the touch.", "",
            "&eCommand of Ice &6(Crouch-RMB)", "&7Raise three blocks of ice over your head, then hurl them where you look.", "&845s cooldown", "",
            "&eScythe Throw &6(RMB)", "&7Throw the scythe, slowing and freezing whatever it hits.", "&830s cooldown");
        def("vulcans_crossbow", "&4Vulcan's Crossbow", Items.CROSSBOW, "moneysmp:vulcans_crossbow", true,
            "&8The legendary crossbow of Vulcan.", "&8Every arrow loaded into it catches flame.", "",
            "&eVulcan's Wrath &6(Crouch-RMB)", "&7Your next shot is a fireball that leaves a crater of magma.", "&845s cooldown", "",
            "&eScattershot &6(Passive)", "&7Fires three shield-piercing arrows; the middle one sets its target alight.");
        def("wand_of_illusion", "&5Wand of Illusion", Items.NETHERITE_SWORD, "moneysmp:wand_of_illusion", true,
            "&8Kill with it and the wand keeps what you killed:", "&8its shape, its health and its gifts.", "",
            "&eDisguise &6(RMB)", "&7Become whatever the wand holds.", "",
            "&eMob Ability &6(RMB while disguised)", "&7Warden, Enderman, Elder Guardian and Ender Dragon have one.", "",
            "&eUndisguise &6(Crouch-RMB)");
        def("hyperion", "&6Hyperion", Items.NETHERITE_SWORD, "moneysmp:hyperion", true,
            "&7You are immune to fire while holding Hyperion.", "&7Its hits inflict &fHallowed Flames &7on vampires.", "",
            "&8Do not go gentle into that good night.", "&8Rage, rage against the dying of the light.", "",
            "&6(Swap Hands + Crouch) &eScorching Blade", "&7Your next hit looses an arc of holy fire.", "&830s cooldown", "",
            "&6(Swap Hands) &eHoly Lance", "&7Call a beam of light down where you look; it burns non-humans hardest.", "&81 minute cooldown");
        def("nightpiercer", "&4Nightpiercer", Items.NETHERITE_SWORD, "moneysmp:nightpiercer", true,
            "&7You gain &fRegeneration I &7at night while holding it.", "", "&8Vampiri sunt praedatores noctis ultimi.", "",
            "&6(Swap Hands + Crouch) &eTransformation", "&7Become a cloud of bats flying where you look.", "&830s cooldown", "",
            "&6(Swap Hands) &eCrimson Bite", "&7Non-vampires in front of you lose 2 hearts for 10s; you gain 2 per bite.", "&830s cooldown");
        def("pale_cannon", "&f&kPale Cannon", Items.CROSSBOW, "moneysmp:pale_cannon", true,
            "&7&kA weapon of unknown power. &6(Shoot) &7fires a mossy shot.",
            "&6Crouch-RMB &7to load &kthe vines&7: a blast that infects with pale rot.", "&860s cooldown");
        def("shadow_blade", "&8Shadow Blade", Items.NETHERITE_SWORD, "moneysmp:shadow_blade", true,
            "&7Speed II while held. A hit from behind may pull", "&7its target in close.", "",
            "&eShadow Leap &6(Crouch-RMB)", "&7Melt into shadow and glide where you look.", "&7Crouch-RMB again to surface early.", "&830s cooldown", "",
            "&6(Swap Hands) &eShadow Daggers", "&7Throw three daggers that slow and blind.", "&845s cooldown");
        def("windweaver", "&fWindweaver", Items.NETHERITE_SWORD, "moneysmp:windweaver", true,
            "&8Woven from the breath of a breeze.", "",
            "&6(Swap Hands) &eWind Leap", "&7Leap where you look with a burst of speed.", "&7The landing doesn't hurt.", "&815s cooldown", "",
            "&6(Swap Hands + Crouch) &eWindweaver Gust", "&7Keep crouching to charge, let go to blow", "&7everything around you away.", "&830s cooldown");
        def("emerald_helmet", "&aEmerald Helmet", Items.NETHERITE_HELMET, "moneysmp:emerald_helmet", true,
            "&7Water breathing while worn.", "",
            "&eEmerald Vision &6(Hold Crouch 3s)", "&7Every other player within 100 blocks glows for 15s.", "&830s cooldown");
        def("emerald_chestplate", "&aEmerald Chestplate", Items.NETHERITE_CHESTPLATE, "moneysmp:emerald_chestplate", true,
            "&7Resistance I while worn. Lightning can't hurt you.", "",
            "&eLightning Ring &6(Passive)", "&7Every 7th hit on a player calls lightning down around you.");
        def("emerald_leggings", "&aEmerald Leggings", Items.NETHERITE_LEGGINGS, "moneysmp:emerald_leggings", true,
            "&7No fall damage while worn.", "",
            "&eShockwave &6(Passive)", "&7Land from 5 or more blocks to slam everyone nearby.");
        def("emerald_boots", "&aEmerald Boots", Items.NETHERITE_BOOTS, "moneysmp:emerald_boots", true,
            "&7Fire Resistance and Speed II while worn,", "&7Speed III on emerald blocks.");
        def("emerald_pickaxe", "&aEmerald Pickaxe", Items.NETHERITE_PICKAXE, "moneysmp:emerald_pickaxe", true,
            "&6(Swap Hands) &eExcavate", "&7Toggle mining 3x3.", "",
            "&6(Swap Hands + Crouch) &eEnchant Switch", "&7Swap between Silk Touch and Fortune III.");
        def("crazy_slots", "&6Crazy Slots", Items.CLAY_BALL, "moneysmp:crazy_slots", true,
            "&7Right-click to become a random legendary weapon.", "&8Transforms for 30 seconds", "&860s cooldown");
        def("weapon_handle", "&cWeapon Handle", Items.CLAY_BALL, "moneysmp:weapon_handle", false,
            "&8Part of a legendary weapon.");
        def("wardens_heart", "&2Warden's Heart", Items.CLAY_BALL, "moneysmp:wardens_heart", false,
            "&8Still beating. Dropped by every warden.");
        def("vulkan_skull", "&4Vulkan Skull", Items.CLAY_BALL, "moneysmp:vulkan_skull", false,
            "&8Part of Vulcan's Crossbow.");
        def("illusion_core", "&5Illusion Core", Items.CLAY_BALL, "moneysmp:illusion_core", false,
            "&8Part of the Wand of Illusion.");
        def("hyperion_shard", "&6Hyperion Shard", Items.AMETHYST_SHARD, "moneysmp:hyperion_shard", false,
            "&8Five make Hyperion.");
        def("nightpiercer_shard", "&4Nightpiercer Shard", Items.AMETHYST_SHARD, "moneysmp:nightpiercer_shard", false,
            "&8Five make Nightpiercer.");
        def("pale_shard", "&fPale Shard", Items.AMETHYST_SHARD, "moneysmp:pale_shard", false,
            "&8Five make the Pale Cannon.");
        def("contagion_catalyst", "&aContagion Catalyst", Items.CLAY_BALL, "minecraft:stripped_crimson_hyphae", false,
            "&8The power to heal worlds, or to shatter them?",
            "&7Use it on the contagion altar to start the ritual.", "&7Your species decides what everyone becomes.");
    }

    // what Crazy Slots can turn into
    static final List<String> SLOT_WEAPONS = List.of("bone_blade", "wand_of_illusion", "frost_scythe", "bloodlust",
        "vulcans_crossbow", "hyperion", "nightpiercer", "pale_cannon", "shadow_blade", "windweaver");

    static final Map<String, EquipmentSlot> ARMOR = Map.of("emerald_helmet", EquipmentSlot.HEAD, "emerald_chestplate", EquipmentSlot.CHEST,
        "emerald_leggings", EquipmentSlot.LEGS, "emerald_boots", EquipmentSlot.FEET);
    private static final ResourceKey<EquipmentAsset> EMERALD = ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath("moneysmp", "emerald"));

    // one line of an altar's recipe: a vanilla item, a legendary part, or player heads
    record Need(Item item, String legend, int count) {
        String label() {
            if (legend != null) return count + "x " + DEFS.get(legend).name();
            if (item == Items.PLAYER_HEAD) return "&f" + count + "x Player Head";
            return "&f" + count + "x " + item.getName().getString();
        }

        boolean matches(ItemStack st) {
            if (legend != null) return legend.equals(id(st)) && slotsUntil(st) == 0;
            if (item == Items.PLAYER_HEAD) return st.is(Items.PLAYER_HEAD) && st.has(DataComponents.PROFILE);
            return st.is(item) && id(st) == null;
        }
    }

    private static Need of(Item item, int count) {
        return new Need(item, null, count);
    }

    private static Need part(String id, int count) {
        return new Need(null, id, count);
    }

    // what each altar takes besides money. pale_shard has no altar in the original plugin
    // (only an admin command), so it gets one shaped like the other shard altars
    static final Map<String, List<Need>> RECIPES = new LinkedHashMap<>();

    static {
        RECIPES.put("bone_blade", List.of(of(Items.PLAYER_HEAD, 15), of(Items.WITHER_SKELETON_SKULL, 30), of(Items.SKELETON_SKULL, 30),
            of(Items.COPPER_BLOCK, 320), of(Items.IRON_BLOCK, 320), of(Items.BONE_BLOCK, 320), part("wardens_heart", 1), part("weapon_handle", 1)));
        RECIPES.put("bloodlust", List.of(of(Items.REDSTONE_BLOCK, 320), of(Items.GOLD_BLOCK, 240), of(Items.NETHER_STAR, 15),
            of(Items.PLAYER_HEAD, 30), part("wardens_heart", 1), part("weapon_handle", 1)));
        RECIPES.put("frost_scythe", List.of(of(Items.TRIDENT, 1), of(Items.ICE, 320), of(Items.BLUE_ICE, 320), of(Items.DIAMOND_BLOCK, 320),
            of(Items.PRISMARINE_SHARD, 320), of(Items.PACKED_ICE, 320), part("wardens_heart", 1), part("weapon_handle", 1)));
        RECIPES.put("vulcans_crossbow", List.of(of(Items.TNT, 320), of(Items.BLAZE_ROD, 320), of(Items.MAGMA_CREAM, 320),
            of(Items.ANCIENT_DEBRIS, 320), of(Items.PLAYER_HEAD, 15), part("wardens_heart", 1), part("vulkan_skull", 1)));
        RECIPES.put("wand_of_illusion", List.of(of(Items.TOTEM_OF_UNDYING, 30), of(Items.GOLD_BLOCK, 160), of(Items.AMETHYST_BLOCK, 320),
            of(Items.PLAYER_HEAD, 10), of(Items.ENCHANTED_GOLDEN_APPLE, 1), part("wardens_heart", 1), part("illusion_core", 1)));
        RECIPES.put("hyperion_shard", List.of(of(Items.IRON_BLOCK, 20), of(Items.STICK, 1), of(Items.BLAZE_POWDER, 1)));
        RECIPES.put("nightpiercer_shard", List.of(of(Items.REDSTONE_BLOCK, 20), of(Items.STICK, 1), of(Items.MAGMA_CREAM, 1)));
        RECIPES.put("pale_shard", List.of(of(Items.PALE_MOSS_BLOCK, 20), of(Items.STICK, 1), of(Items.RESIN_CLUMP, 1)));
        RECIPES.put("hyperion", List.of(part("hyperion_shard", 25)));
        RECIPES.put("nightpiercer", List.of(part("nightpiercer_shard", 25)));
        RECIPES.put("pale_cannon", List.of(part("pale_shard", 25)));
        RECIPES.put("crazy_slots", List.of(of(Items.DRAGON_EGG, 1)));
        // the original's Shadow Blade wants the dragon egg too, which Crazy Slots already
        // takes and the world only has one of, so a dragon head stands in for it here.
        // Windweaver's fabricator ritual is folded into a plain altar recipe
        RECIPES.put("shadow_blade", List.of(of(Items.DRAGON_BREATH, 20), of(Items.DISC_FRAGMENT_5, 60), of(Items.DRAGON_HEAD, 1),
            of(Items.ECHO_SHARD, 80), of(Items.BLACK_CANDLE, 120), of(Items.PLAYER_HEAD, 10)));
        RECIPES.put("windweaver", List.of(of(Items.BREEZE_ROD, 160), of(Items.HEAVY_CORE, 1), of(Items.DIAMOND_BLOCK, 160), of(Items.PLAYER_HEAD, 10)));
        // the copper set came out of trial events in the original; here the emerald set is
        // emerald blocks on top of the netherite piece it replaces
        RECIPES.put("emerald_helmet", List.of(of(Items.EMERALD_BLOCK, 80), of(Items.NETHERITE_HELMET, 1), of(Items.HEART_OF_THE_SEA, 1)));
        RECIPES.put("emerald_chestplate", List.of(of(Items.EMERALD_BLOCK, 120), of(Items.NETHERITE_CHESTPLATE, 1), of(Items.LIGHTNING_ROD, 80)));
        RECIPES.put("emerald_leggings", List.of(of(Items.EMERALD_BLOCK, 100), of(Items.NETHERITE_LEGGINGS, 1), of(Items.WIND_CHARGE, 80)));
        RECIPES.put("emerald_boots", List.of(of(Items.EMERALD_BLOCK, 60), of(Items.NETHERITE_BOOTS, 1), of(Items.BLAZE_POWDER, 80)));
        RECIPES.put("emerald_pickaxe", List.of(of(Items.EMERALD_BLOCK, 80), of(Items.NETHERITE_PICKAXE, 1), of(Items.TNT, 80)));
        RECIPES.put("contagion", List.of(part("contagion_catalyst", 1)));
    }

    private final MoneySMP plugin;
    private Path file;

    // an altar an admin placed: one barrier with a pedestal, the result and its recipe over it
    static final class Stand {
        String type;
        ResourceKey<Level> dim;
        BlockPos pos;

        String tag() {
            return "moneysmp_la_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ();
        }
    }
    final List<Stand> stands = new ArrayList<>();
    private float spin;

    // species: anyone missing is human. og marks the first vampire (whoever crafted Crazy
    // Slots) and the plague doctor, who can't be turned
    private final Map<UUID, String> species = new HashMap<>();
    private final Set<UUID> og = new HashSet<>();
    // standing on moss, or hit by the pale cannon's blast: die like this and you rot
    private final Set<UUID> onMoss = new HashSet<>();
    private final Map<UUID, Long> paleShot = new HashMap<>();
    private final Map<UUID, ServerBossEvent> rotBars = new HashMap<>();

    // the contagion ritual: at most one at a time, and once one finishes everyone (joiners too)
    // is its species until an admin resets it
    private static final class Ritual {
        Stand at;
        String type;
        String by;
        long start;
        long end;
        int integrity;
        long lastLeft;
    }
    private Ritual ritual;
    String contagionDone;
    private final ServerBossEvent ritualBar = new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.NOTCHED_10);
    private final Map<UUID, Long> lastHit = new HashMap<>();

    Legends(MoneySMP plugin) {
        this.plugin = plugin;
        hooks = this;
    }

    private void broadcast(String msg) {
        plugin.server.getPlayerList().broadcastSystemMessage(Fmt.c(msg), false);
    }

    private List<ServerPlayer> players() {
        return plugin.server.getPlayerList().getPlayers();
    }

    // a sound only this player hears, wherever they are
    private static void ping(ServerPlayer p, net.minecraft.sounds.SoundEvent s) {
        p.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(s), SoundSource.MASTER, p.getX(), p.getY(), p.getZ(), 1f, 1f, p.getRandom().nextLong()));
    }

    // ── items ────────────────────────────────────────────────────

    static ItemStack make(String id) {
        Def d = DEFS.get(id);
        ItemStack st = new ItemStack(d.base());
        st.set(DataComponents.ITEM_NAME, flat(d.name(), false));
        st.set(DataComponents.ITEM_MODEL, Identifier.parse(d.model()));
        CompoundTag tag = new CompoundTag();
        tag.putString(KEY, id);
        st.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        if (d.weapon()) {
            st.set(DataComponents.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE);
            st.set(DataComponents.MAX_STACK_SIZE, 1);
            // lava, fire and explosions leave a dropped one alone
            st.set(DataComponents.DAMAGE_RESISTANT, new DamageResistant(PROOF));
        }
        if (id.equals("contagion_catalyst")) st.set(DataComponents.DAMAGE_RESISTANT, new DamageResistant(PROOF));
        EquipmentSlot slot = ARMOR.get(id);
        if (slot != null) st.set(DataComponents.EQUIPPABLE, Equippable.builder(slot).setEquipSound(SoundEvents.ARMOR_EQUIP_NETHERITE).setAsset(EMERALD).build());
        if (hooks != null) enchant(st, id, hooks.plugin.server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT));
        lore(st);
        return st;
    }

    // the emerald set comes enchanted, as the copper set did
    private static void enchant(ItemStack st, String id, HolderLookup.RegistryLookup<Enchantment> ench) {
        if (ARMOR.containsKey(id)) st.enchant(ench.getOrThrow(Enchantments.PROTECTION), 3);
        if (id.equals("emerald_helmet")) {
            st.enchant(ench.getOrThrow(Enchantments.RESPIRATION), 3);
            st.enchant(ench.getOrThrow(Enchantments.AQUA_AFFINITY), 1);
        }
        if (id.equals("emerald_pickaxe")) {
            st.enchant(ench.getOrThrow(Enchantments.EFFICIENCY), 5);
            st.enchant(ench.getOrThrow(Enchantments.SILK_TOUCH), 1);
        }
    }

    public static String id(ItemStack st) {
        CustomData data = st.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        String id = data.copyTag().getStringOr(KEY, "");
        return DEFS.containsKey(id) ? id : null;
    }

    static CompoundTag tag(ItemStack st) {
        CustomData data = st.get(DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag();
    }

    static void putTag(ItemStack st, CompoundTag tag) {
        st.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    // when a Crazy Slots transformation runs out (millis), 0 for anything else
    public static long slotsUntil(ItemStack st) {
        CustomData data = st.get(DataComponents.CUSTOM_DATA);
        return data == null ? 0 : data.copyTag().getLongOr("moneysmp_slots", 0L);
    }

    // the item's lore plus whatever it has picked up: Bloodlust's kills, what the wand holds
    static void lore(ItemStack st) {
        String id = id(st);
        if (id == null) return;
        List<Component> lines = new ArrayList<>();
        CompoundTag tag = tag(st);
        if (id.equals("bloodlust")) lines.add(line("&9Kills: &f" + tag.getIntOr("moneysmp_kills", 0)));
        if (id.equals("wand_of_illusion")) {
            String held = tag.getStringOr("moneysmp_mob", "");
            String who = tag.getStringOr("moneysmp_victim", "");
            lines.add(line("&5Holds: &f" + (held.isEmpty() ? "nothing yet" : !who.isEmpty() ? who : mobName(held))));
        }
        if (id.equals("emerald_pickaxe")) {
            lines.add(line("&aMining: &f" + (tag.getBooleanOr("moneysmp_single", false) ? "1x1" : "3x3")));
            lines.add(line("&aEnchant: &f" + (tag.getBooleanOr("moneysmp_fortune", false) ? "Fortune III" : "Silk Touch")));
        }
        if (!lines.isEmpty()) lines.add(Component.empty());
        for (String l : DEFS.get(id).lore()) lines.add(line(l));
        if (slotsUntil(st) > 0) {
            lines.add(Component.empty());
            lines.add(line("&6Crazy Slots"));
        }
        st.set(DataComponents.LORE, new ItemLore(lines));
    }

    private static Component line(String s) {
        return flat(s, true);
    }

    // one colour reads back from the recipe JSON as a single styled literal, so build it the
    // same way or crafted and handed-out parts won't stack
    private static Component flat(String s, boolean upright) {
        MutableComponent c = Fmt.c(s);
        if (c.getSiblings().size() == 1 && c.getContents().equals(net.minecraft.network.chat.contents.PlainTextContents.EMPTY)) c = c.getSiblings().get(0).copy();
        return upright ? c.withStyle(c.getStyle().withItalic(false)) : c;
    }

    static String mobName(String typeId) {
        return BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(typeId)).getDescription().getString();
    }

    static boolean is(ItemStack st, String id) {
        return id.equals(id(st));
    }

    private static void give(ServerPlayer p, ItemStack st) {
        if (!p.getInventory().add(st)) p.drop(st, false);
    }

    // admin: a fresh one into someone's inventory
    String give(ServerPlayer to, String id) {
        if (!DEFS.containsKey(id)) return "&cNo such item. They are: &f" + String.join(", ", DEFS.keySet());
        give(to, make(id));
        return null;
    }

    // ── altars ───────────────────────────────────────────────────

    private static String title(String type) {
        return type.equals("contagion") ? "&a&lContagion Ritual" : DEFS.get(type).name().replaceFirst("&(.)", "&$1&l");
    }

    private Component recipe(String type) {
        MutableComponent c = Fmt.c("&a$" + Fmt.money(plugin.config.legendary.cost(type)) + " &7from your balance");
        for (Need n : RECIPES.get(type)) c.append("\n").append(Fmt.c(n.label()));
        return c;
    }

    // null when placed, otherwise why not
    String place(ServerPlayer p, String type) {
        if (!RECIPES.containsKey(type)) return "&cNo such altar. They are: &f" + String.join(", ", RECIPES.keySet());
        ServerLevel level = p.level();
        BlockPos pos = p.blockPosition();
        if (!level.getBlockState(pos).canBeReplaced()) return "&cStand somewhere with room for the altar.";
        for (Stand s : stands) if (s.dim == level.dimension() && s.pos.equals(pos)) return "&cThere's already an altar here.";
        Stand s = new Stand();
        s.type = type;
        s.dim = level.dimension();
        s.pos = pos;
        stands.add(s);
        level.setBlockAndUpdate(pos, Blocks.BARRIER.defaultBlockState());
        spawnDisplays(level, s);
        // the player is now inside the barrier
        p.teleportTo(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
        save();
        return null;
    }

    // admin: the nearest altar within 8 blocks goes. null when done
    String remove(ServerPlayer p) {
        Stand best = null;
        double bestD = 64;
        for (Stand s : stands) {
            if (s.dim != p.level().dimension()) continue;
            double d = s.pos.distToCenterSqr(p.position());
            if (d < bestD) {
                bestD = d;
                best = s;
            }
        }
        if (best == null) return "&cNo altar within 8 blocks.";
        if (ritual != null && ritual.at == best) return "&cA contagion ritual is running on it. &f/moneysmp legendary contagion stop &cfirst.";
        tearDown(best);
        return null;
    }

    private void tearDown(Stand s) {
        stands.remove(s);
        ServerLevel level = plugin.server.getLevel(s.dim);
        if (level != null) {
            level.getChunk(s.pos.getX() >> 4, s.pos.getZ() >> 4);
            if (level.getBlockState(s.pos).is(Blocks.BARRIER)) level.setBlockAndUpdate(s.pos, Blocks.AIR.defaultBlockState());
            displays(level, s).forEach(Entity::discard);
        }
        save();
    }

    void list(CommandSourceStack src) {
        if (stands.isEmpty()) {
            src.sendSystemMessage(Fmt.p("&7No legendary altars are placed."));
            return;
        }
        src.sendSystemMessage(Fmt.p("&6&lLegendary altars:"));
        for (Stand s : stands) {
            String dim = s.dim.equals(Level.OVERWORLD) ? "" : " &8(" + s.dim.identifier().getPath().replace('_', ' ') + ")";
            src.sendSystemMessage(Fmt.c("  " + title(s.type) + " &8» &f" + s.pos.getX() + ", " + s.pos.getY() + ", " + s.pos.getZ() + dim));
        }
    }

    Stand standAt(Level level, BlockPos pos) {
        for (Stand s : stands) if (s.dim == level.dimension() && s.pos.equals(pos)) return s;
        return null;
    }

    boolean isAltar(Level level, BlockPos pos) {
        return standAt(level, pos) != null;
    }

    private List<? extends Display> displays(ServerLevel level, Stand s) {
        return level.getEntities(EntityTypeTest.forClass(Display.class), new AABB(s.pos).inflate(4), e -> e.getTags().contains(s.tag()));
    }

    private void text(ServerLevel level, Stand s, double y, Component what, float scale) {
        Display.TextDisplay t = EntityType.TEXT_DISPLAY.create(level, EntitySpawnReason.EVENT);
        if (t == null) return;
        t.setPos(s.pos.getX() + 0.5, s.pos.getY() + y, s.pos.getZ() + 0.5);
        ((TextDisplayInvoker) t).invokeSetText(what);
        ((TextDisplayInvoker) t).invokeSetBackgroundColor(0);
        ((TextDisplayInvoker) t).invokeSetFlags(Display.TextDisplay.FLAG_SHADOW);
        ((DisplayInvoker) t).invokeSetBillboardConstraints(Display.BillboardConstraints.CENTER);
        ((DisplayInvoker) t).invokeSetTransformation(new Transformation(null, null, new Vector3f(scale), null));
        t.addTag(TAG);
        t.addTag(s.tag());
        level.addFreshEntity(t);
    }

    private void spawnDisplays(ServerLevel level, Stand s) {
        Display.ItemDisplay pedestal = EntityType.ITEM_DISPLAY.create(level, EntitySpawnReason.EVENT);
        Display.ItemDisplay item = EntityType.ITEM_DISPLAY.create(level, EntitySpawnReason.EVENT);
        if (pedestal == null || item == null) return;
        double x = s.pos.getX() + 0.5;
        double z = s.pos.getZ() + 0.5;
        ItemStack model = new ItemStack(Items.STONE);
        model.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("moneysmp", "legend_altar"));
        pedestal.setPos(x, s.pos.getY() + 0.5, z);
        ((ItemDisplayInvoker) pedestal).invokeSetItemStack(model);
        ((ItemDisplayInvoker) pedestal).invokeSetItemTransform(ItemDisplayContext.NONE);
        pedestal.addTag(TAG);
        pedestal.addTag(s.tag());
        level.addFreshEntity(pedestal);
        item.setPos(x, s.pos.getY() + 1.9, z);
        ((ItemDisplayInvoker) item).invokeSetItemStack(make(s.type.equals("contagion") ? "contagion_catalyst" : s.type));
        ((ItemDisplayInvoker) item).invokeSetItemTransform(ItemDisplayContext.FIXED);
        ((DisplayInvoker) item).invokeSetTransformationInterpolationDuration(20);
        item.addTag(TAG);
        item.addTag(s.tag());
        item.addTag(TAG + "_item");
        level.addFreshEntity(item);
        text(level, s, 2.6, Fmt.c(title(s.type)), 1.3f);
        text(level, s, 3.05, recipe(s.type), 0.8f);
    }

    // once a second: every altar in a loaded chunk gets its barrier and displays back if
    // anything took them, and the result on top turns a notch
    private void tend() {
        spin += (float) Math.PI / 8;
        for (Stand s : stands) {
            ServerLevel level = plugin.server.getLevel(s.dim);
            if (level == null || !level.hasChunk(s.pos.getX() >> 4, s.pos.getZ() >> 4)) continue;
            if (!level.getBlockState(s.pos).is(Blocks.BARRIER)) level.setBlockAndUpdate(s.pos, Blocks.BARRIER.defaultBlockState());
            List<? extends Display> found = displays(level, s);
            if (found.size() != 4) {
                found.forEach(Entity::discard);
                spawnDisplays(level, s);
                continue;
            }
            for (Display d : found) {
                if (!d.getTags().contains(TAG + "_item")) continue;
                ((DisplayInvoker) d).invokeSetTransformationInterpolationDelay(0);
                ((DisplayInvoker) d).invokeSetTransformation(new Transformation(null, new Quaternionf().rotationY(spin), new Vector3f(0.9f), null));
            }
        }
    }

    // right click on an altar. every click on it is swallowed so nothing gets placed against it
    InteractionResult use(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        if (!(player instanceof ServerPlayer p)) return InteractionResult.PASS;
        Stand s = standAt(level, hit.getBlockPos());
        if (s == null) return InteractionResult.PASS;
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.SUCCESS;
        if (s.type.equals("contagion")) startRitual(p, s);
        else craft(p, s);
        return InteractionResult.SUCCESS;
    }

    // everything in the recipe and the money, or a list of what's short. nothing is taken
    // unless all of it is there
    private boolean take(ServerPlayer p, String type) {
        Inventory inv = p.getInventory();
        List<String> missing = new ArrayList<>();
        for (Need n : RECIPES.get(type)) {
            int have = 0;
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack st = inv.getItem(i);
                if (n.matches(st)) have += st.getCount();
            }
            if (have < n.count()) missing.add(n.count() - have + "x " + n.label().replaceFirst("^(&.)?\\d+x ", "$1"));
        }
        double cost = plugin.config.legendary.cost(type);
        Data.PlayerData pd = plugin.data.get(p);
        double spare = pd.money - plugin.auction.committed(p.getUUID());
        if (spare < cost) missing.add("&a$" + Fmt.money(cost - Math.max(0, spare)));
        if (!missing.isEmpty()) {
            p.sendSystemMessage(Fmt.p("&7This altar still needs: " + String.join("&7, ", missing) + "&7."));
            return false;
        }
        for (Need n : RECIPES.get(type)) {
            Predicate<ItemStack> match = n::matches;
            inv.clearOrCountMatchingItems(match, n.count(), p.inventoryMenu.getCraftSlots());
        }
        if (cost > 0) {
            pd.money -= cost;
            plugin.data.log("LEGENDARY", p.getScoreboardName(), "Altar", cost, type.equals("contagion") ? "Contagion ritual" : "Crafted " + type);
        }
        return true;
    }

    private void craft(ServerPlayer p, Stand s) {
        if (!take(p, s.type)) return;
        give(p, make(s.type));
        String name = DEFS.get(s.type).name();
        boolean shard = s.type.endsWith("_shard");
        ServerLevel level = plugin.server.getLevel(s.dim);
        level.playSound(null, s.pos, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 1f, 1.2f);
        level.sendParticles(ParticleTypes.END_ROD, s.pos.getX() + 0.5, s.pos.getY() + 1.5, s.pos.getZ() + 0.5, 60, 0.4, 0.8, 0.4, 0.05);
        broadcast(Fmt.PREFIX + " &f" + p.getScoreboardName() + " &7has crafted " + (shard ? "a " : "") + name + "&7!");
        if (!shard) {
            for (ServerPlayer o : players()) {
                o.connection.send(new ClientboundSetTitlesAnimationPacket(10, 140, 20));
                o.connection.send(new ClientboundSetSubtitleTextPacket(Fmt.c("&7has crafted " + name + "&7!")));
                o.connection.send(new ClientboundSetTitleTextPacket(Fmt.c("&f" + p.getScoreboardName())));
            }
        }
        // whoever crafts Crazy Slots is the first vampire
        if (s.type.equals("crazy_slots")) {
            setSpecies(p.getUUID(), "vampire", true);
            p.sendSystemMessage(Fmt.p("&4You feel the thirst for blood... You are now the first vampire."));
        }
        tearDown(s);
    }

    // ── species ──────────────────────────────────────────────────

    String species(UUID uid) {
        return species.getOrDefault(uid, "human");
    }

    boolean isOg(UUID uid) {
        return og.contains(uid);
    }

    static String speciesName(String type) {
        return switch (type) {
            case "vampire" -> "&cVampire";
            case "pale" -> "&ePale Rot";
            default -> "&fHuman";
        };
    }

    void setSpecies(UUID uid, String type, boolean original) {
        if (type.equals("human")) species.remove(uid);
        else species.put(uid, type);
        if (original) og.add(uid);
        else og.remove(uid);
        ServerPlayer p = plugin.server.getPlayerList().getPlayer(uid);
        if (p != null) refreshTab(p);
        save();
    }

    // vampires and pale rots get a mark after their name in the tab list, on top of the team
    // colour and tier prefix vanilla would show
    public static Component tabName(ServerPlayer p) {
        if (hooks == null) return null;
        String type = hooks.species(p.getUUID());
        if (type.equals("human")) return null;
        boolean og = hooks.isOg(p.getUUID());
        String mark = type.equals("vampire") ? (og ? " &4☾" : " &c☾") : (og ? " &5✿" : " &e✿");
        return PlayerTeam.formatNameForTeam(p.getTeam(), p.getName()).append(Fmt.c(mark));
    }

    void refreshTab(ServerPlayer p) {
        plugin.server.getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME, p));
    }

    // admin: one player or everyone online
    String setSpeciesCmd(String who, String type) {
        String t = switch (type.toLowerCase()) {
            case "human" -> "human";
            case "vampire" -> "vampire";
            case "pale", "palerot", "pale_rot" -> "pale";
            case "plaguedoctor", "plague_doctor" -> "plaguedoctor";
            default -> null;
        };
        if (t == null) return "&cSpecies are: &fhuman, vampire, pale, plaguedoctor";
        List<UUID> targets = new ArrayList<>();
        if (who.equalsIgnoreCase("all")) {
            for (ServerPlayer p : players()) targets.add(p.getUUID());
        } else {
            UUID uid = plugin.data.lookup(who);
            if (uid == null) return "&cPlayer &f" + who + " &chas never joined.";
            targets.add(uid);
        }
        for (UUID uid : targets) {
            if (t.equals("plaguedoctor")) {
                setSpecies(uid, "pale", true);
                ServerPlayer p = plugin.server.getPlayerList().getPlayer(uid);
                if (p != null) {
                    p.connection.send(new ClientboundSetTitlesAnimationPacket(0, 100, 20));
                    p.connection.send(new ClientboundSetTitleTextPacket(Fmt.c("&5Plague Doctor")));
                    p.sendSystemMessage(Fmt.p("&5You are the Plague Doctor. Infect the others."));
                }
            } else {
                setSpecies(uid, t, false);
            }
        }
        return null;
    }

    // death conversions: a vampire's kill turns its victim, a human's Hyperion kill cures it,
    // and anyone who dies rotting (on moss or hit by the pale cannon) becomes a pale rot.
    // the first vampire and the plague doctor never change. also the warden heart and the
    // player heads the altars want
    void onDeath(LivingEntity dead, DamageSource source) {
        if (!(dead.level() instanceof ServerLevel level)) return;
        if (dead instanceof Warden) drop(level, dead, make("wardens_heart"));
        if (!(dead instanceof ServerPlayer victim)) return;
        UUID uid = victim.getUUID();
        boolean rotting = onMoss.remove(uid) | paleShot.remove(uid) != null;
        ServerPlayer killer = source.getEntity() instanceof ServerPlayer k && k != victim ? k : null;
        if (killer != null) {
            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            head.set(DataComponents.PROFILE, ResolvableProfile.createResolved(victim.getGameProfile()));
            drop(level, victim, head);
        }
        if (isOg(uid)) return;
        String was = species(uid);
        if (killer != null && species(killer.getUUID()).equals("vampire") && !was.equals("vampire")) {
            setSpecies(uid, "vampire", false);
            return;
        }
        if (killer != null && species(killer.getUUID()).equals("human") && is(killer.getMainHandItem(), "hyperion") && !was.equals("human")) {
            setSpecies(uid, "human", false);
            return;
        }
        if (rotting && !was.equals("pale")) setSpecies(uid, "pale", false);
    }

    private static void drop(ServerLevel level, Entity at, ItemStack st) {
        ItemEntity it = new ItemEntity(level, at.getX(), at.getY() + 0.5, at.getZ(), st);
        it.setDefaultPickUpDelay();
        level.addFreshEntity(it);
    }

    void respawned(ServerPlayer p) {
        String type = species(p.getUUID());
        if (type.equals("vampire")) {
            p.sendSystemMessage(Fmt.p("&cYou feel the thirst for blood... You are a vampire."));
            p.level().sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 1, p.getZ(), 40, 1, 1, 1, 0);
        } else if (type.equals("pale")) {
            p.sendSystemMessage(Fmt.p("&7You feel rotted inside... You are a pale rot."));
        }
    }

    // milk washes the pale cannon's rot off
    public static void drank(ServerPlayer p, ItemStack st) {
        if (hooks == null || !st.is(Items.MILK_BUCKET)) return;
        hooks.paleShot.remove(p.getUUID());
    }

    void infect(ServerPlayer p) {
        paleShot.put(p.getUUID(), System.currentTimeMillis() + 10 * 60_000L);
    }

    // once a second: what each species gets, and who is rotting
    private void effects() {
        Config.Legendary cfg = plugin.config.legendary;
        long now = System.currentTimeMillis();
        paleShot.values().removeIf(t -> t < now);
        for (ServerPlayer p : players()) {
            UUID uid = p.getUUID();
            ServerLevel level = p.level();
            String type = species(uid);
            if (type.equals("vampire") && !p.isSpectator()) {
                long time = level.getDayTime() % 24000;
                boolean sun = level.dimension() == Level.OVERWORLD && time < 12300 && !level.isRaining() && level.canSeeSky(p.blockPosition().above());
                if (sun) {
                    p.setRemainingFireTicks(Math.max(p.getRemainingFireTicks(), 30));
                } else {
                    p.addEffect(new MobEffectInstance(MobEffects.SPEED, 40, 0, true, false, true));
                    p.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 40, 0, true, false, true));
                    if (cfg.vampireFireResistance) p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 40, 0, true, false, true));
                }
            } else if (type.equals("pale") && cfg.paleWeaknessInRain && level.isRainingAt(p.blockPosition().above())) {
                p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0, true, false, true));
            }
            String under = BuiltInRegistries.BLOCK.getKey(level.getBlockState(p.blockPosition().below()).getBlock()).getPath();
            String feet = BuiltInRegistries.BLOCK.getKey(level.getBlockState(p.blockPosition()).getBlock()).getPath();
            if (p.onGround() && (under.contains("moss") || feet.contains("moss"))) onMoss.add(uid);
            else onMoss.remove(uid);
            boolean rotting = onMoss.contains(uid) || paleShot.containsKey(uid);
            ServerBossEvent bar = rotBars.get(uid);
            if (rotting && bar == null) {
                bar = new ServerBossEvent(Fmt.c("&7Pale Rot"), BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS);
                bar.addPlayer(p);
                rotBars.put(uid, bar);
            } else if (!rotting && bar != null) {
                bar.removeAllPlayers();
                rotBars.remove(uid);
            }
        }
    }

    void join(ServerPlayer p) {
        if (contagionDone != null && !species(p.getUUID()).equals(contagionDone)) setSpecies(p.getUUID(), contagionDone, false);
        if (ritual != null) ritualBar.addPlayer(p);
        refreshTab(p);
    }

    void leave(ServerPlayer p) {
        ServerBossEvent bar = rotBars.remove(p.getUUID());
        if (bar != null) bar.removeAllPlayers();
        ritualBar.removePlayer(p);
        onMoss.remove(p.getUUID());
    }

    // ── contagion ritual ─────────────────────────────────────────

    private void startRitual(ServerPlayer p, Stand s) {
        if (contagionDone != null) {
            p.sendSystemMessage(Fmt.p("&cThe &aContagion Ritual &chas already been completed. An admin can reset it with &f/moneysmp legendary contagion reset&c."));
            return;
        }
        if (ritual != null) {
            p.sendSystemMessage(Fmt.p("&aThe ritual is already under way. &7" + Fmt.timeAgo(ritual.lastLeft) + " to go."));
            return;
        }
        if (!take(p, "contagion")) return;
        Ritual r = new Ritual();
        r.at = s;
        r.type = species(p.getUUID());
        r.by = p.getScoreboardName();
        r.start = System.currentTimeMillis();
        r.end = r.start + plugin.config.legendary.contagionSeconds * 1000L;
        r.integrity = plugin.config.legendary.contagionIntegrity;
        r.lastLeft = plugin.config.legendary.contagionSeconds;
        ritual = r;
        for (ServerPlayer o : players()) {
            ritualBar.addPlayer(o);
            ping(o, SoundEvents.END_PORTAL_SPAWN);
        }
        save();
        broadcast("");
        broadcast("&aSUCCESSFULLY DOCKED TO ALTAR. &eBEGIN BOOTING SEQUENCE.");
        broadcast("&2CONTAGION RITUAL TUNED TO " + switch (r.type) {
            case "vampire" -> "&cVAMPIRE";
            case "pale" -> "&ePALE ROT";
            default -> "&fHUMAN";
        } + "&2.");
        broadcast("&2INPUTTING COORDINATES... &ax " + s.pos.getX() + " y " + s.pos.getY() + " z " + s.pos.getZ());
        broadcast("&aESTIMATED TIME OF COMPLETION... &e" + Fmt.timeAgo(r.lastLeft) + "&a.");
        broadcast("&aOnce the Contagion Ritual is complete, everyone becomes " + speciesName(r.type) + "&a! &7Hit the altar to break it.");
        broadcast("");
    }

    // left click on the contagion altar while its ritual runs knocks a point off its integrity
    InteractionResult attack(Player player, Level level, InteractionHand hand, BlockPos pos) {
        Stand s = standAt(level, pos);
        if (s == null) return InteractionResult.PASS;
        if (!(player instanceof ServerPlayer p) || ritual == null || ritual.at != s || p.isSpectator()) return InteractionResult.SUCCESS;
        long now = System.currentTimeMillis();
        Long last = lastHit.get(p.getUUID());
        if (last != null && now - last < 500) return InteractionResult.SUCCESS;
        lastHit.put(p.getUUID(), now);
        ServerLevel lv = (ServerLevel) level;
        lv.playSound(null, pos, SoundEvents.ELDER_GUARDIAN_HURT, SoundSource.BLOCKS, 1f, 1f);
        int max = plugin.config.legendary.contagionIntegrity;
        ritual.integrity--;
        for (int q = 3; q >= 1; q--) {
            if (ritual.integrity == max * q / 4) broadcast("&cWARNING!! &aContagion Signal is at " + q * 25 + "% integrity!");
        }
        if (ritual.integrity <= 0) {
            ritual = null;
            ritualBar.removeAllPlayers();
            for (ServerPlayer o : players()) ping(o, SoundEvents.WARDEN_ROAR);
            broadcast(Fmt.PREFIX + " &cContagion Ritual destroyed by &f" + p.getScoreboardName() + "&c, aborting conversion.");
            save();
        }
        return InteractionResult.SUCCESS;
    }

    private void ritualTick() {
        if (ritual == null) return;
        long now = System.currentTimeMillis();
        long total = ritual.end - ritual.start;
        long left = Math.max(0, (ritual.end - now + 999) / 1000);
        long totalSecs = total / 1000;
        for (int q = 1; q <= 3; q++) {
            long mark = totalSecs * (4 - q) / 4;
            if (ritual.lastLeft > mark && left <= mark && left > 0) {
                broadcast("&aTHE CONTAGION RITUAL IS " + q * 25 + "% COMPLETE.");
                for (ServerPlayer o : players()) ping(o, SoundEvents.END_PORTAL_FRAME_FILL);
            }
        }
        if (left <= 10 && left > 0 && left < ritual.lastLeft) broadcast("&cT-MINUS " + left + " second" + (left == 1 ? "" : "s"));
        ritual.lastLeft = left;
        if (left == 0) {
            finishRitual();
            return;
        }
        ritualBar.setName(Fmt.c("&a&lContagion Ritual &7→ " + speciesName(ritual.type) + "  &8|  &f" + Fmt.timeAgo(left)
            + "  &8|  &aintegrity &f" + ritual.integrity + "/" + plugin.config.legendary.contagionIntegrity));
        ritualBar.setProgress((float) Math.min(1, (double) (now - ritual.start) / total));
    }

    private void finishRitual() {
        String type = ritual.type;
        ritual = null;
        contagionDone = type;
        ritualBar.removeAllPlayers();
        for (ServerPlayer o : players()) {
            ping(o, SoundEvents.WITHER_SPAWN);
            setSpecies(o.getUUID(), type, isOg(o.getUUID()) && species(o.getUUID()).equals(type));
            o.sendSystemMessage(Fmt.p(switch (type) {
                case "vampire" -> "&cYou feel the thirst for blood... You are now a vampire.";
                case "pale" -> "&7You feel connected to the earth... You are now a pale rot.";
                default -> "&fYou feel normal... You are now a human.";
            }));
        }
        broadcast("");
        broadcast("&aTHE CONTAGION RITUAL IS 100% COMPLETE. &7Everyone is now " + speciesName(type) + "&7.");
        broadcast("");
        save();
    }

    // particles over a running ritual, every 5 ticks
    void draw() {
        if (ritual == null) return;
        ServerLevel level = plugin.server.getLevel(ritual.at.dim);
        if (level == null) return;
        BlockPos b = ritual.at.pos;
        double x = b.getX() + 0.5;
        double z = b.getZ() + 0.5;
        for (double y = 1; y < 40; y += 1) level.sendParticles(ParticleTypes.END_ROD, true, false, x, b.getY() + y, z, 1, 0, 0, 0, 0);
        double a = spin;
        for (double r = 1.5; r <= 5; r += 1.2) {
            for (int i = 0; i < 16; i++) {
                double t = a + i * Math.PI / 8;
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x + Math.cos(t) * r, b.getY() + 0.1, z + Math.sin(t) * r, 1, 0, 0, 0, 0);
            }
        }
    }

    String stopRitual() {
        if (ritual == null) return "&cNo contagion ritual is running.";
        ritual = null;
        ritualBar.removeAllPlayers();
        broadcast(Fmt.PREFIX + " &7An admin stopped the contagion ritual.");
        save();
        return null;
    }

    void resetRitual() {
        contagionDone = null;
        save();
    }

    // once a second
    void tick() {
        tend();
        effects();
        ritualTick();
    }

    // ── legends.json ─────────────────────────────────────────────

    void load(Path dir) {
        file = dir.resolve("legends.json");
        stands.clear();
        species.clear();
        og.clear();
        paleShot.clear();
        ritual = null;
        contagionDone = null;
        if (!Files.exists(file)) return;
        try {
            JsonObject y = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if (y.has("altars")) {
                for (JsonElement el : y.getAsJsonArray("altars")) {
                    JsonObject o = el.getAsJsonObject();
                    Stand s = new Stand();
                    s.type = o.get("type").getAsString();
                    if (!RECIPES.containsKey(s.type)) continue;
                    s.dim = ResourceKey.create(Registries.DIMENSION, Identifier.parse(o.get("dim").getAsString()));
                    s.pos = new BlockPos(o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt());
                    stands.add(s);
                }
            }
            if (y.has("species")) {
                for (Map.Entry<String, JsonElement> e : y.getAsJsonObject("species").entrySet()) species.put(UUID.fromString(e.getKey()), e.getValue().getAsString());
            }
            if (y.has("og")) for (JsonElement el : y.getAsJsonArray("og")) og.add(UUID.fromString(el.getAsString()));
            if (y.has("rotting")) {
                for (Map.Entry<String, JsonElement> e : y.getAsJsonObject("rotting").entrySet()) paleShot.put(UUID.fromString(e.getKey()), e.getValue().getAsLong());
            }
            if (y.has("contagionDone")) contagionDone = y.get("contagionDone").getAsString();
            if (y.has("ritual")) {
                JsonObject r = y.getAsJsonObject("ritual");
                BlockPos at = new BlockPos(r.get("x").getAsInt(), r.get("y").getAsInt(), r.get("z").getAsInt());
                for (Stand s : stands) {
                    if (s.pos.equals(at) && s.type.equals("contagion")) {
                        ritual = new Ritual();
                        ritual.at = s;
                    }
                }
                if (ritual != null) {
                    ritual.type = r.get("type").getAsString();
                    ritual.by = r.get("by").getAsString();
                    ritual.start = r.get("start").getAsLong();
                    ritual.end = r.get("end").getAsLong();
                    ritual.integrity = r.get("integrity").getAsInt();
                    ritual.lastLeft = Math.max(0, (ritual.end - System.currentTimeMillis() + 999) / 1000);
                }
            }
        } catch (IOException | RuntimeException e) {
            MoneySMP.LOG.error("could not read legends.json", e);
        }
    }

    void save() {
        if (file == null) return;
        JsonObject y = new JsonObject();
        JsonArray altars = new JsonArray();
        for (Stand s : stands) {
            JsonObject o = new JsonObject();
            o.addProperty("type", s.type);
            o.addProperty("dim", s.dim.identifier().toString());
            o.addProperty("x", s.pos.getX());
            o.addProperty("y", s.pos.getY());
            o.addProperty("z", s.pos.getZ());
            altars.add(o);
        }
        y.add("altars", altars);
        JsonObject sp = new JsonObject();
        species.forEach((uid, t) -> sp.addProperty(uid.toString(), t));
        y.add("species", sp);
        JsonArray o = new JsonArray();
        for (UUID uid : og) o.add(uid.toString());
        y.add("og", o);
        JsonObject rot = new JsonObject();
        paleShot.forEach((uid, t) -> rot.addProperty(uid.toString(), t));
        y.add("rotting", rot);
        if (contagionDone != null) y.addProperty("contagionDone", contagionDone);
        if (ritual != null) {
            JsonObject r = new JsonObject();
            r.addProperty("x", ritual.at.pos.getX());
            r.addProperty("y", ritual.at.pos.getY());
            r.addProperty("z", ritual.at.pos.getZ());
            r.addProperty("type", ritual.type);
            r.addProperty("by", ritual.by);
            r.addProperty("start", ritual.start);
            r.addProperty("end", ritual.end);
            r.addProperty("integrity", ritual.integrity);
            y.add("ritual", r);
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(y));
        } catch (IOException e) {
            MoneySMP.LOG.error("could not write legends.json", e);
        }
    }
}
