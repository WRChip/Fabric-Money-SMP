package dev.flame.moneysmp;

import com.mojang.math.Transformation;
import dev.flame.moneysmp.mixin.DisplayInvoker;
import dev.flame.moneysmp.mixin.ItemDisplayInvoker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.PowerParticleOption;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

// what the legendaries do. every ability is tied to the item in hand (found by its custom data,
// never its name), cooldowns are per player and shown as a boss bar while they run, and
// anything that outlives a click (stuns, bleeding, disguises, flying ice) is ticked from here.
// ticked by MoneySMP every tick
public final class Weapons {
    static Weapons hooks;
    // effect entities: hovering ice, stun bones, bats, disguise shells. any found in the world
    // that isn't live here is left over from a crash or restart and gets removed
    static final String FX = "moneysmp_fx";
    private static final Identifier STUN = Identifier.fromNamespaceAndPath("moneysmp", "stun");
    private static final Identifier BITE_LOSS = Identifier.fromNamespaceAndPath("moneysmp", "crimson_bite_loss");
    private static final Identifier BITE_GAIN = Identifier.fromNamespaceAndPath("moneysmp", "crimson_bite_gain");
    private static final Identifier ILLUSION = Identifier.fromNamespaceAndPath("moneysmp", "illusion_health");
    private static final ResourceKey<DamageType> VULCAN_ARROW = ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath("moneysmp", "vulcan_arrow"));
    private static final ResourceKey<DamageType> VULCAN_WRATH = ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath("moneysmp", "vulcan_wrath"));
    private static final ResourceKey<DamageType> ABILITY = ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath("moneysmp", "ability"));
    private static final ResourceKey<DamageType> BLEED = ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath("moneysmp", "bleed"));

    private final MoneySMP plugin;
    private int tick;
    private final Set<Entity> fx = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private final List<Runnable> later = new ArrayList<>();

    private record Cd(long start, long end, ServerBossEvent bar) {}
    private final Map<UUID, Map<String, Cd>> cooldowns = new HashMap<>();

    // fully hidden from other players until the given millis; ghosts can't hurt or be hurt
    private final Map<UUID, Long> hidden = new HashMap<>();
    private final Set<UUID> ghosts = new HashSet<>();
    private final Map<UUID, Vec3> lastPos = new HashMap<>();

    private final Set<UUID> wrathReady = new HashSet<>();
    // what each wrath crater replaced, put back 30 seconds on. a block caught by a second
    // crater moves to it, still holding what was there first
    private record Crater(ServerLevel level, Map<BlockPos, BlockState> was, int at) {}
    private final List<Crater> craters = new ArrayList<>();
    // each flying vulcan arrow and who its volley has already hit
    private final Map<Arrow, Set<Entity>> vulcanArrows = new java.util.IdentityHashMap<>();
    private final Set<UUID> paleReady = new HashSet<>();
    private final Set<UUID> scorching = new HashSet<>();
    private final Set<UUID> hallowed = new HashSet<>();

    private static final class Bleed {
        int secondsLeft;
    }
    private final Map<UUID, Bleed> bleeding = new HashMap<>();

    private static final class Stun {
        LivingEntity who;
        int ticks;
        Vec3 at;
        final List<Display> bones = new ArrayList<>();
    }
    private final List<Stun> stuns = new ArrayList<>();

    private static final class Hover {
        ServerPlayer owner;
        Display.ItemDisplay ice;
        double dx;
        int ticks = 40;
    }
    private final List<Hover> hovering = new ArrayList<>();

    private static final class Dash {
        ServerPlayer p;
        int ticks;
        final List<Bat> bats = new ArrayList<>();
    }
    private final List<Dash> dashes = new ArrayList<>();

    private record Bite(LivingEntity who, Identifier id, long until) {}
    private final List<Bite> bites = new ArrayList<>();

    private static final class Disguise {
        LivingEntity shell;
        String type;
    }
    private final Map<UUID, Disguise> disguised = new HashMap<>();
    private final Map<Entity, ServerPlayer> shells = new java.util.IdentityHashMap<>();

    private static final class Leap {
        ServerPlayer p;
        int ticks = 30;
    }
    private final List<Leap> leaps = new ArrayList<>();

    private static final class Gust {
        ServerPlayer p;
        int ticks;
    }
    private final List<Gust> gusts = new ArrayList<>();

    // a Wind Leap or Shadow Leap landing doesn't hurt until this (millis)
    private final Map<UUID, Long> softLanding = new HashMap<>();
    private final Map<UUID, Integer> crouching = new HashMap<>();
    private final Map<UUID, Integer> ringHits = new HashMap<>();
    private boolean mining;

    Weapons(MoneySMP plugin) {
        this.plugin = plugin;
        hooks = this;
    }

    private Config.Legendary cfg() {
        return plugin.config.legendary;
    }

    private static void sound(ServerLevel level, Vec3 at, SoundEvent s, float vol, float pitch) {
        level.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    private static void particles(ServerLevel level, ParticleOptions type, Vec3 at, int count, double spread, double speed) {
        level.sendParticles(type, at.x, at.y, at.z, count, spread, spread, spread, speed);
    }

    private <T extends Entity> T spawnFx(ServerLevel level, T e) {
        e.addTag(FX);
        fx.add(e);
        level.addFreshEntity(e);
        return e;
    }

    private void dropFx(Entity e) {
        fx.remove(e);
        if (!e.isRemoved()) e.discard();
    }

    // ServerEntityEvents.ENTITY_LOAD
    void loaded(Entity e) {
        if (e.getTags().contains(FX) && !fx.contains(e)) e.discard();
    }

    // ── cooldowns ────────────────────────────────────────────────

    // true and the clock starts, or false with how long is left on the action bar
    private boolean ready(ServerPlayer p, String key, String label, int secs, BossEvent.BossBarColor color) {
        Map<String, Cd> mine = cooldowns.computeIfAbsent(p.getUUID(), k -> new HashMap<>());
        long now = System.currentTimeMillis();
        Cd cd = mine.get(key);
        if (cd != null && cd.end() > now) {
            long left = (cd.end() - now + 999) / 1000;
            plugin.notify(p.getUUID(), "&eYou can't use " + label + " &efor another " + left + " second" + (left == 1 ? "" : "s"), 2);
            p.displayClientMessage(Fmt.c("&eYou can't use " + label + " &efor another " + left + " second" + (left == 1 ? "" : "s")), true);
            return false;
        }
        bar(p, key, label, secs, color);
        return true;
    }

    // a draining boss bar; also used for being stunned or bleeding
    private void bar(ServerPlayer p, String key, String label, int secs, BossEvent.BossBarColor color) {
        Map<String, Cd> mine = cooldowns.computeIfAbsent(p.getUUID(), k -> new HashMap<>());
        Cd old = mine.remove(key);
        if (old != null) old.bar().removeAllPlayers();
        ServerBossEvent bar = new ServerBossEvent(Fmt.c(label), color, BossEvent.BossBarOverlay.PROGRESS);
        bar.addPlayer(p);
        long now = System.currentTimeMillis();
        mine.put(key, new Cd(now, now + secs * 1000L, bar));
    }

    private void drainBars() {
        long now = System.currentTimeMillis();
        for (Iterator<Map<String, Cd>> it = cooldowns.values().iterator(); it.hasNext(); ) {
            Map<String, Cd> mine = it.next();
            mine.values().removeIf(cd -> {
                if (cd.end() <= now) {
                    cd.bar().removeAllPlayers();
                    return true;
                }
                cd.bar().setProgress((float) (cd.end() - now) / (cd.end() - cd.start()));
                return false;
            });
            if (mine.isEmpty()) it.remove();
        }
    }

    // ── hiding ───────────────────────────────────────────────────

    // EntityMixin: whether `viewer` gets to see `e` at all
    public static boolean visible(Entity e, ServerPlayer viewer) {
        if (hooks == null || e == viewer) return true;
        if (e instanceof ServerPlayer p && (hooks.hidden.containsKey(p.getUUID()) || hooks.disguised.containsKey(p.getUUID()))) return false;
        return hooks.shells.get(e) != viewer;
    }

    // vanilla only re-checks who sees an entity when it crosses a chunk section, so after
    // hiding or showing one, ask again now
    private static void retrack(Entity e) {
        if (!(e.level() instanceof ServerLevel level)) return;
        ChunkMap.TrackedEntity t = level.getChunkSource().chunkMap.entityMap.get(e.getId());
        if (t != null) t.updatePlayers(level.players());
    }

    private void hide(ServerPlayer p, int ticks) {
        hidden.merge(p.getUUID(), System.currentTimeMillis() + ticks * 50L, Math::max);
        retrack(p);
    }

    // ── items in hand ────────────────────────────────────────────

    // UseItemCallback, main hand only. never stops vanilla use, so crossbows still load
    InteractionResult use(Player player, Level level, InteractionHand hand) {
        if (!(player instanceof ServerPlayer p) || hand != InteractionHand.MAIN_HAND || p.isSpectator()) return InteractionResult.PASS;
        ItemStack st = p.getMainHandItem();
        String id = Legends.id(st);
        if (id == null) return InteractionResult.PASS;
        boolean sneak = p.isShiftKeyDown();
        switch (id) {
            case "bone_blade" -> {
                if (sneak) {
                    if (ready(p, "bone_cage", "&fBone Cage", 60, BossEvent.BossBarColor.WHITE)) throwSnowball(p, new ItemStack(Items.BONE_MEAL), 4, "moneysmp_bone", SoundEvents.SKELETON_HURT);
                } else if (ready(p, "skeletal_leap", "&fSkeletal Leap", 30, BossEvent.BossBarColor.WHITE)) {
                    p.addEffect(new MobEffectInstance(MobEffects.SPEED, 80, 2, true, false, true));
                    p.setDeltaMovement(p.getLookAngle().scale(2));
                    p.hurtMarked = true;
                    particles(p.level(), ParticleTypes.CAMPFIRE_COSY_SMOKE, p.position(), 10, 0.3, 0.01);
                    sound(p.level(), p.position(), SoundEvents.SKELETON_HURT, 1, 1);
                }
            }
            case "bloodlust" -> {
                int kills = Legends.tag(st).getIntOr("moneysmp_kills", 0);
                if (sneak && kills >= 5) {
                    if (ready(p, "blood_hook", "&cBlood Hook", 30, BossEvent.BossBarColor.RED)) bloodHook(p);
                } else if (!sneak && kills >= 3 && ready(p, "blood_trail", "&cBlood Trail", 60, BossEvent.BossBarColor.RED)) {
                    ghosts.add(p.getUUID());
                    hide(p, 200);
                    sound(p.level(), p.position(), SoundEvents.MAGMA_CUBE_DEATH, 1, 1);
                }
            }
            case "frost_scythe" -> {
                if (sneak) {
                    if (ready(p, "command_of_ice", "&bCommand of Ice", 45, BossEvent.BossBarColor.BLUE)) raiseIce(p);
                } else if (ready(p, "scythe_throw", "&bScythe Throw", 30, BossEvent.BossBarColor.BLUE)) {
                    ItemStack blade = new ItemStack(Items.NETHERITE_SWORD);
                    blade.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("moneysmp", "frost_scythe"));
                    throwSnowball(p, blade, 2, "moneysmp_scythe", SoundEvents.SKELETON_CONVERTED_TO_STRAY);
                }
            }
            case "vulcans_crossbow" -> {
                if (sneak && !wrathReady.contains(p.getUUID()) && ready(p, "vulcans_wrath", "&4Vulcan's Wrath", 45, BossEvent.BossBarColor.RED)) {
                    wrathReady.add(p.getUUID());
                    p.sendSystemMessage(Fmt.p("&4Your next shot is Vulcan's Wrath."));
                }
            }
            case "pale_cannon" -> {
                if (sneak && !paleReady.contains(p.getUUID()) && ready(p, "pale_vines", "&fNature's Vines", 60, BossEvent.BossBarColor.WHITE)) {
                    paleReady.add(p.getUUID());
                    p.sendSystemMessage(Fmt.p("&7Your next shot carries the rot."));
                }
            }
            case "wand_of_illusion" -> {
                Disguise d = disguised.get(p.getUUID());
                if (d == null && !sneak) disguise(p, st);
                else if (d != null && sneak) undisguise(p, true);
                else if (d != null) mobAbility(p, d);
            }
            case "crazy_slots" -> {
                if (Legends.slotsUntil(st) == 0 && ready(p, "crazy_slots", "&6Crazy Slots", 60, BossEvent.BossBarColor.YELLOW)) spin(p);
            }
            case "shadow_blade" -> {
                if (!sneak) break;
                Leap going = null;
                for (Leap l : leaps) if (l.p == p) going = l;
                if (going != null) going.ticks = 0;
                else if (ready(p, "shadow_leap", "&8Shadow Leap", 30, BossEvent.BossBarColor.PURPLE)) shadowLeap(p);
            }
            default -> {}
        }
        return InteractionResult.PASS;
    }

    // ServerGamePacketListenerImplMixin: the swap-hands key. Hyperion and Nightpiercer use it
    // for their abilities, and keep the sword where it is
    public static boolean swap(ServerPlayer p) {
        if (hooks == null || p.isSpectator()) return false;
        String id = Legends.id(p.getMainHandItem());
        if (id == null) return false;
        boolean sneak = p.isShiftKeyDown();
        if (id.equals("hyperion")) {
            if (sneak) {
                if (hooks.ready(p, "scorching_blade", "&6Scorching Blade", 30, BossEvent.BossBarColor.YELLOW)) {
                    hooks.scorching.add(p.getUUID());
                    p.sendSystemMessage(Fmt.p("&6Your next hit looses a Scorching Blade."));
                }
            } else {
                hooks.holyLance(p);
            }
            return true;
        }
        if (id.equals("nightpiercer")) {
            if (sneak) {
                if (hooks.ready(p, "transformation", "&4Transformation", 30, BossEvent.BossBarColor.RED)) hooks.batDash(p);
            } else if (hooks.ready(p, "crimson_bite", "&4Crimson Bite", 30, BossEvent.BossBarColor.RED)) {
                hooks.crimsonBite(p);
            }
            return true;
        }
        if (id.equals("shadow_blade")) {
            if (!sneak && hooks.ready(p, "shadow_daggers", "&8Shadow Daggers", 45, BossEvent.BossBarColor.PURPLE)) hooks.daggers(p);
            return true;
        }
        if (id.equals("windweaver")) {
            if (!sneak) {
                if (hooks.ready(p, "wind_leap", "&fWind Leap", 15, BossEvent.BossBarColor.WHITE)) hooks.windLeap(p);
            } else if (hooks.gusts.stream().noneMatch(g -> g.p == p) && hooks.ready(p, "wind_gust", "&fWindweaver Gust", 30, BossEvent.BossBarColor.WHITE)) {
                Gust g = new Gust();
                g.p = p;
                hooks.gusts.add(g);
                sound(p.level(), p.position(), SoundEvents.BREEZE_INHALE, 1, 1);
            }
            return true;
        }
        if (id.equals("emerald_pickaxe")) {
            hooks.pickaxeMode(p, sneak);
            return true;
        }
        return false;
    }

    private void throwSnowball(ServerPlayer p, ItemStack look, double speed, String tag, SoundEvent s) {
        Snowball sb = new Snowball(p.level(), p, look);
        sb.shootFromRotation(p, p.getXRot(), p.getYRot(), 0, (float) speed, 0);
        sb.addTag(tag);
        p.level().addFreshEntity(sb);
        sound(p.level(), p.position(), s, 1, 1);
    }

    // ── bloodlust ────────────────────────────────────────────────

    private void bloodHook(ServerPlayer p) {
        ServerLevel level = p.level();
        Vec3 eye = p.getEyePosition();
        Vec3 dir = p.getLookAngle();
        for (double d = 0; d < 20; d += 0.5) {
            Vec3 at = eye.add(dir.scale(d));
            particles(level, ParticleTypes.DRIPPING_LAVA, at, 3, 0, 0);
            if (!level.getBlockState(BlockPos.containing(at)).getCollisionShape(level, BlockPos.containing(at)).isEmpty()) return;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(1), e -> e != p && e.isAlive() && !(e instanceof Player o && o.isSpectator()))) {
                LivingEntity target = shells.containsKey(e) ? shells.get(e) : e;
                if (target == p) continue;
                target.setDeltaMovement(p.position().subtract(target.position()).normalize().scale(3));
                target.hurtMarked = true;
                p.sendSystemMessage(Fmt.p("&4Blood Hook caught &f" + target.getName().getString() + "&4!"));
                sound(level, target.position(), SoundEvents.ZOMBIE_INFECT, 1, 0.8f);
                return;
            }
        }
    }

    private void bleed(ServerPlayer victim, ServerPlayer by) {
        if (bleeding.containsKey(victim.getUUID())) return;
        Bleed b = new Bleed();
        b.secondsLeft = cfg().bleedSeconds;
        bleeding.put(victim.getUUID(), b);
        bar(victim, "bleeding", "&c!BLEEDING!", cfg().bleedSeconds, BossEvent.BossBarColor.RED);
        particles(victim.level(), new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()), victim.position().add(0, 1, 0), 10, 0.1, 0.1);
        by.sendSystemMessage(Fmt.p("&c" + victim.getScoreboardName() + " is bleeding."));
    }

    // ── frost scythe ─────────────────────────────────────────────

    private void raiseIce(ServerPlayer p) {
        for (double dx : new double[]{-2, 0, 2}) {
            Display.ItemDisplay ice = EntityType.ITEM_DISPLAY.create(p.level(), EntitySpawnReason.EVENT);
            if (ice == null) continue;
            ice.setPos(p.getX() + dx, p.getY() + 4, p.getZ());
            ((ItemDisplayInvoker) ice).invokeSetItemStack(new ItemStack(Items.BLUE_ICE));
            ((ItemDisplayInvoker) ice).invokeSetItemTransform(ItemDisplayContext.FIXED);
            ice.setPosRotInterpolationDuration(1);
            Hover h = new Hover();
            h.owner = p;
            h.ice = spawnFx(p.level(), ice);
            h.dx = dx;
            hovering.add(h);
        }
        sound(p.level(), p.position(), SoundEvents.POWDER_SNOW_HIT, 1, 0.6f);
    }

    // the ice follows its caster for two seconds, then becomes real snowballs flung where they look
    private void tickHover() {
        for (Iterator<Hover> it = hovering.iterator(); it.hasNext(); ) {
            Hover h = it.next();
            ServerPlayer p = h.owner;
            if (p.isRemoved() || h.ice.isRemoved() || p.level() != h.ice.level()) {
                dropFx(h.ice);
                it.remove();
                continue;
            }
            Vec3 at = new Vec3(p.getX() + h.dx, p.getY() + 4, p.getZ());
            h.ice.setPos(at);
            particles(p.level(), ParticleTypes.SNOWFLAKE, at, 2, 0.1, 0);
            if (--h.ticks > 0) continue;
            dropFx(h.ice);
            it.remove();
            Snowball sb = new Snowball(p.level(), at.x, at.y, at.z, new ItemStack(Items.BLUE_ICE));
            sb.setOwner(p);
            sb.setDeltaMovement(p.getLookAngle().scale(2));
            sb.addTag("moneysmp_ice");
            p.level().addFreshEntity(sb);
        }
    }

    // ── vulcan's crossbow and the pale cannon ────────────────────

    // CrossbowItemMixin: a loaded legendary crossbow firing. true when it was ours
    public static boolean shoot(ServerLevel level, LivingEntity shooter, ItemStack bow) {
        if (hooks == null || !(shooter instanceof ServerPlayer p)) return false;
        String id = Legends.id(bow);
        if (!"vulcans_crossbow".equals(id) && !"pale_cannon".equals(id)) return false;
        bow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
        Vec3 look = p.getLookAngle();
        if (id.equals("vulcans_crossbow")) {
            sound(level, p.position(), SoundEvents.ENDER_DRAGON_HURT, 1, 1);
            fireBeam(level, p, ParticleTypes.FLAME, ParticleTypes.SOUL_FIRE_FLAME);
            if (hooks.wrathReady.remove(p.getUUID())) {
                hooks.fireball(p, look, "moneysmp_wrath");
                return true;
            }
            Set<Entity> volley = new HashSet<>();
            for (int i = -1; i <= 1; i++) {
                Vec3 dir = look.yRot((float) Math.toRadians(5 * i));
                Arrow arrow = new Arrow(level, p, new ItemStack(Items.ARROW), bow);
                arrow.shoot(dir.x, dir.y, dir.z, 3, 0);
                arrow.setCritArrow(true);
                arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
                // a piercing arrow goes through shields
                arrow.setPierceLevel((byte) 1);
                arrow.addTag("moneysmp_vulcan");
                if (i == 0) arrow.igniteForSeconds(50);
                level.addFreshEntity(arrow);
                hooks.vulcanArrows.put(arrow, volley);
            }
            return true;
        }
        if (hooks.paleReady.remove(p.getUUID())) {
            sound(level, p.position(), SoundEvents.ENDER_DRAGON_HURT, 1, 0.6f);
            fireBeam(level, p, ParticleTypes.MYCELIUM, ParticleTypes.WITCH);
            hooks.fireball(p, look, "moneysmp_pale_big");
            return true;
        }
        hooks.throwSnowball(p, new ItemStack(Items.MOSS_BLOCK), 4.5, "moneysmp_pale_shot", SoundEvents.CROSSBOW_SHOOT);
        particles(level, ParticleTypes.WITCH, p.position().add(0, 1, 0), 30, 0.6, 0);
        return true;
    }

    // the datapack's moneysmp damage types skip armour, enchantments, effects and shields, so an
    // ability hits as hard on netherite as on bare skin
    private static DamageSource trueDamage(ServerLevel level, ResourceKey<DamageType> type, Entity direct, ServerPlayer owner) {
        return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(type), direct, owner);
    }

    private static void fireBeam(ServerLevel level, ServerPlayer p, ParticleOptions a, ParticleOptions b) {
        Vec3 eye = p.getEyePosition();
        Vec3 dir = p.getLookAngle();
        for (double i = 0; i < 10; i += 0.4) {
            Vec3 at = eye.add(dir.scale(i));
            particles(level, a, at, 1, 0.3, 0);
            particles(level, b, at, 1, 0.3, 0);
        }
    }

    private void fireball(ServerPlayer p, Vec3 look, String tag) {
        LargeFireball fb = new LargeFireball(p.level(), p, look, 0);
        fb.setPos(p.getEyePosition().add(look));
        fb.setDeltaMovement(look.scale(2));
        fb.addTag(tag);
        p.level().addFreshEntity(fb);
    }

    // a bowl of magma and lava where the wrath lands. bedrock, barriers, anything holding
    // items and the altars stay put
    private void crater(ServerLevel level, Vec3 center) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        BlockPos c = BlockPos.containing(center);
        int r = 4;
        List<AABB> standing = level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(r + 2)).stream().map(Entity::getBoundingBox).toList();
        Map<BlockPos, BlockState> was = new HashMap<>();
        for (int x = -r; x <= r; x++) {
            for (int y = -2; y <= 1; y++) {
                for (int z = -r; z <= r; z++) {
                    double dist = Math.sqrt(x * x + y * y + z * z);
                    double edge = r - rng.nextDouble() * 1.5;
                    if (dist > edge) continue;
                    BlockPos pos = c.offset(x, y, z);
                    BlockState st = level.getBlockState(pos);
                    if (st.getDestroySpeed(level, pos) < 0 || st.hasBlockEntity() || Legends.hooks.isAltar(level, pos)
                        || (plugin.altar.isAltar(level, pos))) continue;
                    BlockState to;
                    if (y < 0) to = rng.nextDouble() < 0.75 ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.LAVA.defaultBlockState();
                    else if (y == 0 && dist < edge - 1) to = rng.nextDouble() < 0.3 ? Blocks.LAVA.defaultBlockState() : Blocks.MAGMA_BLOCK.defaultBlockState();
                    else to = Blocks.AIR.defaultBlockState();
                    // whoever stands in the blast drops into the pool rather than being sealed in a block
                    if (to.is(Blocks.MAGMA_BLOCK) && standing.stream().anyMatch(b -> b.intersects(new AABB(pos)))) to = Blocks.AIR.defaultBlockState();
                    BlockState first = st;
                    for (Crater old : craters) {
                        if (old.level() == level && old.was().containsKey(pos)) first = old.was().remove(pos);
                    }
                    was.put(pos, first);
                    level.setBlockAndUpdate(pos, to);
                }
            }
        }
        craters.add(new Crater(level, was, tick + 30 * 20));
        sound(level, center, SoundEvents.GENERIC_EXPLODE.value(), 3, 0.8f);
        sound(level, center, SoundEvents.LAVA_POP, 2, 0.5f);
        particles(level, ParticleTypes.LARGE_SMOKE, center, 50, 2, 0.05);
        particles(level, ParticleTypes.FLAME, center, 30, 1.5, 0.05);
        particles(level, ParticleTypes.LAVA, center, 40, 2, 0.02);
    }

    // anything the crater left (magma, lava, air) goes back to what it was; a block someone
    // has since placed there stays. whoever is standing in it is lifted out
    private void fill(Crater c) {
        for (Map.Entry<BlockPos, BlockState> e : c.was().entrySet()) {
            BlockState now = c.level().getBlockState(e.getKey());
            if (now.is(Blocks.MAGMA_BLOCK) || now.is(Blocks.LAVA) || now.isAir() || now.is(Blocks.FIRE)) c.level().setBlockAndUpdate(e.getKey(), e.getValue());
        }
        for (BlockPos pos : c.was().keySet()) {
            for (LivingEntity e : c.level().getEntitiesOfClass(LivingEntity.class, new AABB(pos))) {
                for (int i = 0; i < 8 && !c.level().noCollision(e); i++) e.teleportTo(e.getX(), Math.floor(e.getY()) + 1, e.getZ());
            }
        }
    }

    // the server stopping puts every crater back now, or it would stay for good
    void fillCraters() {
        craters.forEach(this::fill);
        craters.clear();
    }

    // ProjectileMixin: one of ours landed. true when handled, and the projectile is gone
    public static boolean landed(Projectile proj, HitResult hit) {
        if (hooks == null || !(proj.level() instanceof ServerLevel level)) return false;
        Set<String> tags = proj.getTags();
        String kind = null;
        for (String t : new String[]{"moneysmp_bone", "moneysmp_scythe", "moneysmp_ice", "moneysmp_pale_shot", "moneysmp_wrath", "moneysmp_pale_big", "moneysmp_vulcan", "moneysmp_dagger"}) {
            if (tags.contains(t)) kind = t;
        }
        if (kind == null) return false;
        ServerPlayer owner = proj.getOwner() instanceof ServerPlayer o ? o : null;
        Entity e = hit instanceof EntityHitResult eh ? eh.getEntity() : null;
        if (e != null && hooks.shells.containsKey(e)) e = hooks.shells.get(e);
        LivingEntity target = e instanceof LivingEntity le && le != owner ? le : null;
        // a vulcan arrow that misses sticks in the ground like any other
        if (kind.equals("moneysmp_vulcan") && target == null) {
            hooks.vulcanArrows.remove(proj);
            return false;
        }
        Vec3 at = hit.getLocation();
        Config.Legendary cfg = hooks.cfg();
        switch (kind) {
            case "moneysmp_bone" -> {
                if (target != null) hooks.stun(target);
            }
            case "moneysmp_scythe" -> {
                if (target != null) {
                    target.hurtServer(level, trueDamage(level, ABILITY, proj, owner), (float) cfg.scytheThrowDamage);
                    target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 160, 2, true, false, true));
                    target.setTicksFrozen(Math.min(target.getTicksFrozen() + 200, target.getTicksRequiredToFreeze() * 2));
                    sound(level, target.position(), SoundEvents.SNOW_HIT, 1, 1);
                    particles(level, ParticleTypes.SNOWFLAKE, target.position().add(0, 1, 0), 40, 0.4, 0.05);
                }
            }
            case "moneysmp_ice" -> {
                particles(level, ParticleTypes.EXPLOSION, at, 1, 0, 0);
                particles(level, ParticleTypes.SNOWFLAKE, at.add(0, 1, 0), 30, 0.5, 0.05);
                sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 1, 1.4f);
                hooks.blast(level, trueDamage(level, ABILITY, proj, owner), owner, at, 1.5, cfg.scytheIceDamage);
            }
            case "moneysmp_pale_shot" -> {
                if (target != null) {
                    target.hurtServer(level, trueDamage(level, ABILITY, proj, owner), (float) cfg.paleShotDamage);
                    particles(level, ParticleTypes.WITCH, target.position().add(0, 1, 0), 40, 0.6, 0);
                    sound(level, target.position(), SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, 1, 1);
                }
            }
            case "moneysmp_wrath" -> {
                particles(level, ParticleTypes.EXPLOSION_EMITTER, at, 1, 0, 0);
                hooks.blast(level, trueDamage(level, VULCAN_WRATH, proj, owner), owner, at, 2, cfg.vulcanWrathDamage);
                hooks.crater(level, at);
            }
            case "moneysmp_pale_big" -> {
                particles(level, ParticleTypes.EXPLOSION_EMITTER, at, 1, 0, 0);
                particles(level, ParticleTypes.WITCH, at, 80, 2, 0);
                sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 2, 0.6f);
                for (LivingEntity hitBy : hooks.blast(level, trueDamage(level, ABILITY, proj, owner), owner, at, 2, cfg.paleBigShotDamage)) {
                    if (hitBy instanceof ServerPlayer p) Legends.hooks.infect(p);
                }
            }
            case "moneysmp_vulcan" -> {
                // vulcan damage ignores the hit cooldown, so a volley would stack three times
                // over: each target takes one arrow of it
                Set<Entity> volley = hooks.vulcanArrows.remove(proj);
                if (target != null && (volley == null || volley.add(target))) {
                    target.hurtServer(level, trueDamage(level, VULCAN_ARROW, proj, owner), (float) cfg.vulcanArrowDamage);
                    if (proj.isOnFire()) target.igniteForSeconds(5);
                }
            }
            case "moneysmp_dagger" -> {
                if (target != null) {
                    target.hurtServer(level, trueDamage(level, ABILITY, proj, owner), (float) cfg.shadowDaggerDamage);
                    target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
                    target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
                    particles(level, ParticleTypes.SQUID_INK, target.position().add(0, 1, 0), 20, 0.3, 0.02);
                }
            }
            default -> {}
        }
        proj.discard();
        return true;
    }

    // everything living within `r` of `at` takes the hit, the thrower spared
    private List<LivingEntity> blast(ServerLevel level, DamageSource src, ServerPlayer owner, Vec3 at, double r, double dmg) {
        List<LivingEntity> hit = new ArrayList<>();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(r), e -> e != owner && e.isAlive() && !shells.containsKey(e))) {
            e.hurtServer(level, src, (float) dmg);
            hit.add(e);
        }
        return hit;
    }

    // ── bone blade ───────────────────────────────────────────────

    // five seconds rooted to the spot, with bones circling
    private void stun(LivingEntity who) {
        stuns.removeIf(s -> {
            if (s.who != who) return false;
            s.bones.forEach(this::dropFx);
            return true;
        });
        Stun s = new Stun();
        s.who = who;
        s.ticks = 100;
        s.at = who.position();
        ServerLevel level = (ServerLevel) who.level();
        for (int i = 0; i < 3; i++) {
            Display.BlockDisplay bone = EntityType.BLOCK_DISPLAY.create(level, EntitySpawnReason.EVENT);
            if (bone == null) continue;
            bone.setBlockState(Blocks.BONE_BLOCK.defaultBlockState());
            bone.setPos(s.at);
            bone.setPosRotInterpolationDuration(1);
            ((DisplayInvoker) bone).invokeSetTransformation(new Transformation(null, null, new Vector3f(0.3f), null));
            s.bones.add(spawnFx(level, bone));
        }
        stuns.add(s);
        AttributeModifier root = new AttributeModifier(STUN, -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        for (var attr : List.of(Attributes.MOVEMENT_SPEED, Attributes.JUMP_STRENGTH)) {
            AttributeInstance inst = who.getAttribute(attr);
            if (inst != null) inst.addOrUpdateTransientModifier(root);
        }
        sound(level, who.position(), SoundEvents.SKELETON_DEATH, 1, 1);
        if (who instanceof ServerPlayer p) {
            bar(p, "stunned", "&fSTUNNED!", 5, BossEvent.BossBarColor.WHITE);
            p.sendSystemMessage(Fmt.p("&fYou are stunned!"));
        }
    }

    private void tickStuns() {
        for (Iterator<Stun> it = stuns.iterator(); it.hasNext(); ) {
            Stun s = it.next();
            if (--s.ticks <= 0 || s.who.isRemoved() || !s.who.isAlive()) {
                s.bones.forEach(this::dropFx);
                for (var attr : List.of(Attributes.MOVEMENT_SPEED, Attributes.JUMP_STRENGTH)) {
                    AttributeInstance inst = s.who.getAttribute(attr);
                    if (inst != null) inst.removeModifier(STUN);
                }
                it.remove();
                continue;
            }
            double a = (100 - s.ticks) * Math.PI / 30;
            for (int i = 0; i < s.bones.size(); i++) {
                double t = a + i * Math.PI * 2 / 3;
                s.bones.get(i).setPos(s.at.add(Math.cos(t) * 1.5, 0.5, Math.sin(t) * 1.5));
            }
            ServerLevel level = (ServerLevel) s.who.level();
            for (int i = 0; i < 8; i++) {
                double t = a * 2 + i * Math.PI / 4;
                particles(level, ParticleTypes.ASH, s.who.position().add(Math.cos(t) * 1.5, 1, Math.sin(t) * 1.5), 1, 0, 0);
            }
        }
    }

    // ── hyperion ─────────────────────────────────────────────────

    // aimed at someone (give or take a block) the lance falls on them, otherwise on the block
    // looked at. looking at a player's body used to drop it on the ground far behind them
    private void holyLance(ServerPlayer p) {
        ServerLevel level = p.level();
        HitResult aim = p.pick(100, 0, false);
        Vec3 eye = p.getEyePosition();
        Vec3 end = aim.getType() == HitResult.Type.BLOCK ? aim.getLocation() : eye.add(p.getLookAngle().scale(100));
        Vec3 at = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(1), e -> e != p && e.isAlive() && !(e instanceof Player o && o.isSpectator()))) {
            java.util.Optional<Vec3> hit = e.getBoundingBox().inflate(1).clip(eye, end);
            if (hit.isPresent() && hit.get().distanceToSqr(eye) < best) {
                best = hit.get().distanceToSqr(eye);
                at = e.position();
            }
        }
        if (at == null && aim.getType() == HitResult.Type.BLOCK) at = Vec3.atBottomCenterOf(((BlockHitResult) aim).getBlockPos().above());
        if (at == null) {
            p.displayClientMessage(Fmt.c("&eLook at a block or player within 100 blocks."), true);
            return;
        }
        if (!ready(p, "holy_lance", "&6Holy Lance", 60, BossEvent.BossBarColor.YELLOW)) return;
        Config.Legendary cfg = cfg();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(3), e -> e.isAlive() && !shells.containsKey(e))) {
            float dmg = (float) cfg.holyLanceDamage;
            if (e instanceof ServerPlayer o && !Legends.hooks.species(o.getUUID()).equals("human")) dmg += (float) cfg.holyLanceBonusVsNonHumans;
            e.hurtServer(level, trueDamage(level, ABILITY, p, p), dmg);
            e.igniteForTicks(60);
        }
        for (int i = 0; i < 100; i++) {
            double t = i * Math.PI * 2 / 100;
            particles(level, ParticleTypes.FLAME, at.add(Math.cos(t) * 2, 0.1, Math.sin(t) * 2), 1, 0, 0);
        }
        for (int y = 0; y <= 35; y++) {
            for (int i = 0; i < 8; i++) {
                double t = i * Math.PI / 4;
                Vec3 pt = at.add(Math.cos(t) * 0.5, y, Math.sin(t) * 0.5);
                level.sendParticles(ParticleTypes.END_ROD, true, false, pt.x, pt.y, pt.z, 1, 0, 0, 0, 0);
            }
        }
        sound(level, at, SoundEvents.LIGHTNING_BOLT_IMPACT, 2, 1);
    }

    // an arc of holy fire in front of the swing, filled from 1 to 5 blocks out so it reaches
    // whoever was just hit too
    private void scorchingBlade(ServerPlayer p) {
        ServerLevel level = p.level();
        Vec3 origin = p.position().add(0, 1.2, 0);
        Vec3 forward = p.getLookAngle();
        Set<LivingEntity> hit = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            double angle = -Math.PI / 4 + Math.PI / 2 / 20 * i;
            Vec3 pt = origin.add(forward.yRot((float) angle).scale(5));
            particles(level, ParticleTypes.FLAME, pt, 15, 0.1, 0.02);
            particles(level, ParticleTypes.LAVA, pt, 2, 0.1, 0);
            hit.addAll(level.getEntitiesOfClass(LivingEntity.class, new AABB(origin.add(forward.yRot((float) angle)), pt).inflate(0.8, 1, 0.8), e -> e != p && e.isAlive() && !shells.containsKey(e)));
        }
        sound(level, origin, SoundEvents.FIRECHARGE_USE, 1, 1.3f);
        for (LivingEntity e : hit) {
            // the sword hit a tick ago left its target invulnerable, which would swallow the arc
            e.invulnerableTime = 0;
            e.hurtServer(level, trueDamage(level, ABILITY, p, p), (float) cfg().scorchingBladeDamage);
            e.igniteForTicks(60);
        }
    }

    // ── nightpiercer ─────────────────────────────────────────────

    private void crimsonBite(ServerPlayer p) {
        ServerLevel level = p.level();
        sound(level, p.position(), SoundEvents.EVOKER_FANGS_ATTACK, 1, 1);
        Vec3 eye = p.getEyePosition();
        Vec3 dir = p.getLookAngle();
        BlockParticleOption blood = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState());
        for (double i = -0.6; i <= 0.6; i += 0.15) {
            particles(level, blood, eye.add(dir.yRot((float) i).xRot((float) i).scale(4)), 5, 0.1, 0);
            particles(level, blood, eye.add(dir.yRot((float) -i).xRot((float) -i).scale(4)), 5, 0.1, 0);
        }
        int bitten = 0;
        long until = System.currentTimeMillis() + 10_000;
        for (ServerPlayer o : level.players()) {
            if (o == p || o.isSpectator() || !o.isAlive()) continue;
            Vec3 to = o.position().subtract(eye);
            if (to.length() > 6 || to.normalize().dot(dir) < 0.7) continue;
            bitten++;
            if (Legends.hooks.species(o.getUUID()).equals("vampire")) continue;
            maxHealth(o, BITE_LOSS, -4, until);
            o.sendSystemMessage(Fmt.p("&4You were bitten! &7You lose two hearts for 10 seconds."));
        }
        if (bitten > 0) {
            AttributeInstance inst = p.getAttribute(Attributes.MAX_HEALTH);
            AttributeModifier old = inst == null ? null : inst.getModifier(BITE_GAIN);
            maxHealth(p, BITE_GAIN, (old == null ? 0 : old.amount()) + 4 * bitten, until);
        }
    }

    // a max health change that wears off, kept transient so a crash or logout can't make it stick
    private void maxHealth(LivingEntity who, Identifier id, double amount, long until) {
        AttributeInstance inst = who.getAttribute(Attributes.MAX_HEALTH);
        if (inst == null) return;
        inst.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
        bites.removeIf(b -> b.who() == who && b.id().equals(id));
        bites.add(new Bite(who, id, until));
        if (who.getHealth() > who.getMaxHealth()) who.setHealth(who.getMaxHealth());
    }

    private void batDash(ServerPlayer p) {
        Dash d = new Dash();
        d.p = p;
        d.ticks = 60;
        for (int i = 0; i < 6; i++) {
            Bat bat = EntityType.BAT.create(p.level(), EntitySpawnReason.EVENT);
            if (bat == null) continue;
            bat.setNoAi(true);
            bat.setSilent(true);
            bat.setInvulnerable(true);
            bat.setNoGravity(true);
            bat.setPos(p.position().add(0, 1, 0));
            d.bats.add(spawnFx(p.level(), bat));
        }
        dashes.add(d);
        hide(p, 60);
        sound(p.level(), p.position(), SoundEvents.BAT_LOOP, 1, 1);
    }

    private void tickDashes() {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (Iterator<Dash> it = dashes.iterator(); it.hasNext(); ) {
            Dash d = it.next();
            ServerPlayer p = d.p;
            if (--d.ticks <= 0 || p.isRemoved() || !p.isAlive()) {
                d.bats.forEach(this::dropFx);
                it.remove();
                continue;
            }
            p.setDeltaMovement(p.getLookAngle().scale(0.8));
            p.hurtMarked = true;
            p.resetFallDistance();
            particles(p.level(), ParticleTypes.SMOKE, p.position().add(0, 1, 0), 6, 0.3, 0);
            for (Bat bat : d.bats) {
                if (bat.level() != p.level()) continue;
                bat.setPos(p.getX() + rng.nextDouble() - 0.5, p.getY() + 0.5 + rng.nextDouble(), p.getZ() + rng.nextDouble() - 0.5);
            }
        }
    }

    // ── shadow blade ─────────────────────────────────────────────

    // a second and a half as an untouchable shadow, gliding where you look. walls still stop it
    private void shadowLeap(ServerPlayer p) {
        Leap l = new Leap();
        l.p = p;
        leaps.add(l);
        ghosts.add(p.getUUID());
        hide(p, l.ticks);
        sound(p.level(), p.position(), SoundEvents.ENDERMAN_TELEPORT, 1, 0.6f);
    }

    // a vulcan arrow hits whatever its path passes within about a block of (1.8 wide instead of
    // the usual 1.2): three thin arrows at range are a lot to ask for a sword fighter
    private void tickVulcanArrows() {
        vulcanArrows.keySet().removeIf(a -> a.isRemoved() || a.tickCount > 100);
        Map<Arrow, LivingEntity> hits = new java.util.IdentityHashMap<>();
        for (Arrow a : vulcanArrows.keySet()) {
            Vec3 from = a.position();
            Vec3 to = from.add(a.getDeltaMovement());
            Entity owner = a.getOwner();
            for (LivingEntity e : a.level().getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(1.5),
                    e -> e != owner && e.isAlive() && !(e instanceof Player o && o.isSpectator()))) {
                if (e.getBoundingBox().inflate(0.6).clip(from, to).isPresent() || e.getBoundingBox().inflate(0.6).contains(from)) {
                    hits.put(a, e);
                    break;
                }
            }
        }
        hits.forEach((a, e) -> landed(a, new EntityHitResult(e)));
    }

    private void tickLeaps() {
        for (Iterator<Leap> it = leaps.iterator(); it.hasNext(); ) {
            Leap l = it.next();
            ServerPlayer p = l.p;
            if (l.ticks-- <= 0 || p.isRemoved() || !p.isAlive()) {
                it.remove();
                // surface now, not when the hide would have run out
                if (hidden.containsKey(p.getUUID())) hidden.put(p.getUUID(), 0L);
                softLanding.put(p.getUUID(), System.currentTimeMillis() + 5000);
                sound(p.level(), p.position(), SoundEvents.ENDERMAN_TELEPORT, 1, 1);
                particles(p.level(), ParticleTypes.LARGE_SMOKE, p.position().add(0, 1, 0), 30, 0.4, 0.02);
                continue;
            }
            p.setDeltaMovement(p.getLookAngle());
            p.hurtMarked = true;
            p.resetFallDistance();
            particles(p.level(), ParticleTypes.SQUID_INK, p.position().add(0, 1, 0), 4, 0.3, 0);
            particles(p.level(), ParticleTypes.SMOKE, p.position().add(0, 0.5, 0), 6, 0.3, 0);
        }
    }

    private void daggers(ServerPlayer p) {
        for (int i = -1; i <= 1; i++) {
            ItemStack blade = new ItemStack(Items.NETHERITE_SWORD);
            blade.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("moneysmp", "shadow_blade"));
            Snowball sb = new Snowball(p.level(), p, blade);
            sb.shootFromRotation(p, p.getXRot(), p.getYRot() + i * 6, 0, 2.5f, 0);
            sb.addTag("moneysmp_dagger");
            p.level().addFreshEntity(sb);
        }
        sound(p.level(), p.position(), SoundEvents.TRIDENT_THROW.value(), 1, 1.4f);
    }

    // ── windweaver ───────────────────────────────────────────────

    private void windLeap(ServerPlayer p) {
        Vec3 v = p.getLookAngle().scale(1.4);
        p.setDeltaMovement(v.x, Math.max(v.y, 0.3), v.z);
        p.hurtMarked = true;
        p.addEffect(new MobEffectInstance(MobEffects.SPEED, 100, 3, true, false, true));
        softLanding.put(p.getUUID(), System.currentTimeMillis() + 8000);
        particles(p.level(), ParticleTypes.GUST, p.position(), 3, 0.3, 0);
        sound(p.level(), p.position(), SoundEvents.BREEZE_JUMP, 1, 1);
    }

    // the gust charges for as long as its owner keeps crouching, five seconds at most
    private void tickGusts() {
        for (Iterator<Gust> it = gusts.iterator(); it.hasNext(); ) {
            Gust g = it.next();
            ServerPlayer p = g.p;
            if (p.isRemoved() || !p.isAlive()) {
                it.remove();
                continue;
            }
            g.ticks++;
            if (p.isShiftKeyDown() && g.ticks < 100 && Legends.is(p.getMainHandItem(), "windweaver")) {
                int bars = g.ticks / 10;
                p.displayClientMessage(Fmt.c("&fGust &b" + "|".repeat(bars) + "&8" + "|".repeat(10 - bars)), true);
                double a = g.ticks * 0.4;
                particles(p.level(), ParticleTypes.CLOUD, p.position().add(Math.cos(a) * 1.2, 0.2 + (g.ticks % 20) / 10.0, Math.sin(a) * 1.2), 1, 0, 0);
                continue;
            }
            it.remove();
            gustBlast(p, g.ticks / 100.0);
        }
    }

    private void gustBlast(ServerPlayer p, double charge) {
        ServerLevel level = p.level();
        double r = 4 + 4 * charge;
        double push = 2 + 4 * charge;
        double up = 0.7 + 0.8 * charge;
        float dmg = (float) (cfg().windGustDamage * charge);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r), e -> e != p && e.isAlive())) {
            LivingEntity who = shells.containsKey(e) ? shells.get(e) : e;
            if (who == p || who.distanceTo(p) > r) continue;
            Vec3 away = who.position().subtract(p.position()).multiply(1, 0, 1).normalize();
            who.setDeltaMovement(away.x * push * 0.5, up, away.z * push * 0.5);
            who.hurtMarked = true;
            if (dmg >= 1) who.hurtServer(level, trueDamage(level, ABILITY, p, p), dmg);
        }
        particles(level, ParticleTypes.GUST_EMITTER_LARGE, p.position().add(0, 1, 0), 1, 0, 0);
        particles(level, ParticleTypes.GUST, p.position().add(0, 1, 0), (int) (10 + 20 * charge), r / 2, 0);
        sound(level, p.position(), SoundEvents.WIND_CHARGE_BURST.value(), 1.5f, 0.8f);
        p.displayClientMessage(Fmt.c("&fGust released at " + Math.round(charge * 100) + "%"), true);
    }

    // ── emerald set ──────────────────────────────────────────────

    // swap toggles 3x3 mining, crouch-swap switches Silk Touch for Fortune III and back
    private void pickaxeMode(ServerPlayer p, boolean sneak) {
        ItemStack pick = p.getMainHandItem();
        CompoundTag tag = Legends.tag(pick);
        if (!sneak) {
            boolean single = !tag.getBooleanOr("moneysmp_single", false);
            tag.putBoolean("moneysmp_single", single);
            p.displayClientMessage(Fmt.c("&aMining " + (single ? "1x1" : "3x3")), true);
        } else {
            boolean fortune = !tag.getBooleanOr("moneysmp_fortune", false);
            tag.putBoolean("moneysmp_fortune", fortune);
            var ench = p.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            Holder<Enchantment> silk = ench.getOrThrow(Enchantments.SILK_TOUCH);
            Holder<Enchantment> luck = ench.getOrThrow(Enchantments.FORTUNE);
            EnchantmentHelper.updateEnchantments(pick, m -> {
                m.set(silk, fortune ? 0 : 1);
                m.set(luck, fortune ? 3 : 0);
            });
            p.displayClientMessage(Fmt.c("&aNow " + (fortune ? "Fortune III" : "Silk Touch")), true);
        }
        Legends.putTag(pick, tag);
        Legends.lore(pick);
        sound(p.level(), p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1, 1.4f);
    }

    // PlayerBlockBreakEvents.AFTER: the emerald pickaxe takes the 3x3 around the block, across
    // the face the miner is looking at. each one goes through the normal break, so claims,
    // altars and drops all behave
    void mined(Level level, Player player, BlockPos pos) {
        if (mining || !(player instanceof ServerPlayer p) || p.isShiftKeyDown()) return;
        ItemStack pick = p.getMainHandItem();
        if (!Legends.is(pick, "emerald_pickaxe") || Legends.tag(pick).getBooleanOr("moneysmp_single", false)) return;
        Direction.Axis axis = Math.abs(p.getXRot()) > 50 ? Direction.Axis.Y : p.getDirection().getAxis();
        mining = true;
        try {
            for (int a = -1; a <= 1; a++) {
                for (int b = -1; b <= 1; b++) {
                    if (a == 0 && b == 0) continue;
                    BlockPos at = switch (axis) {
                        case X -> pos.offset(0, a, b);
                        case Y -> pos.offset(a, 0, b);
                        case Z -> pos.offset(a, b, 0);
                    };
                    BlockState st = level.getBlockState(at);
                    if (st.isAir() || !st.is(BlockTags.MINEABLE_WITH_PICKAXE) || st.getDestroySpeed(level, at) < 0) continue;
                    p.gameMode.destroyBlock(at);
                }
            }
        } finally {
            mining = false;
        }
    }

    private static boolean wearing(LivingEntity e, EquipmentSlot slot, String id) {
        return Legends.is(e.getItemBySlot(slot), id);
    }

    // hold crouch for three seconds in the helmet and every other player nearby lights up
    private void tickVision(ServerPlayer p) {
        if (!p.isShiftKeyDown() || !wearing(p, EquipmentSlot.HEAD, "emerald_helmet")) {
            crouching.remove(p.getUUID());
            return;
        }
        int n = crouching.merge(p.getUUID(), 1, Integer::sum);
        if (n != 60 || !ready(p, "emerald_vision", "&aEmerald Vision", 30, BossEvent.BossBarColor.GREEN)) return;
        int seen = 0;
        for (ServerPlayer o : p.level().players()) {
            if (o == p || o.isSpectator() || o.distanceToSqr(p) > 100 * 100) continue;
            o.addEffect(new MobEffectInstance(MobEffects.GLOWING, 300, 0, false, false, false));
            seen++;
        }
        sound(p.level(), p.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1, 1);
        p.displayClientMessage(Fmt.c("&aEmerald Vision &7shows " + seen + " player" + (seen == 1 ? "" : "s")), true);
    }

    // eight bolts around the wearer that only light the sky; the damage is dealt here, to
    // everyone near them but the wearer
    private void lightningRing(ServerPlayer p) {
        ServerLevel level = p.level();
        for (int i = 0; i < 8; i++) {
            double t = i * Math.PI / 4;
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.EVENT);
            if (bolt == null) continue;
            bolt.setVisualOnly(true);
            bolt.setPos(p.getX() + Math.cos(t) * 5, p.getY(), p.getZ() + Math.sin(t) * 5);
            level.addFreshEntity(bolt);
        }
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(6), e -> e != p && e.isAlive() && !shells.containsKey(e))) {
            e.hurtServer(level, trueDamage(level, ABILITY, p, p), (float) cfg().lightningRingDamage);
        }
    }

    // the leggings turn a hard landing into a slam: the further the fall, the harder it hits
    private void shockwave(ServerPlayer p, float fell) {
        ServerLevel level = p.level();
        float dmg = 2 + fell / 5;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(5), e -> e != p && e.isAlive() && !shells.containsKey(e))) {
            e.hurtServer(level, trueDamage(level, ABILITY, p, p), dmg);
            Vec3 away = e.position().subtract(p.position()).multiply(1, 0, 1).normalize();
            e.setDeltaMovement(away.x * 0.8, 0.6, away.z * 0.8);
            e.hurtMarked = true;
        }
        BlockState ground = level.getBlockState(p.blockPosition().below());
        particles(level, new BlockParticleOption(ParticleTypes.BLOCK, ground), p.position(), 80, 2, 0.2);
        particles(level, ParticleTypes.EXPLOSION, p.position(), 1, 0, 0);
        sound(level, p.position(), SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.5f, 1);
    }

    // ── crazy slots ──────────────────────────────────────────────

    private void spin(ServerPlayer p) {
        String id = Legends.SLOT_WEAPONS.get(ThreadLocalRandom.current().nextInt(Legends.SLOT_WEAPONS.size()));
        ItemStack st = Legends.make(id);
        var ench = p.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        if (st.is(Items.CROSSBOW)) {
            st.enchant(ench.getOrThrow(Enchantments.QUICK_CHARGE), 3);
        } else {
            for (var e : List.of(Enchantments.SHARPNESS, Enchantments.FIRE_ASPECT, Enchantments.SWEEPING_EDGE, Enchantments.LOOTING)) {
                Holder<Enchantment> h = ench.getOrThrow(e);
                st.enchant(h, e == Enchantments.SHARPNESS ? 5 : e == Enchantments.FIRE_ASPECT ? 2 : 3);
            }
        }
        CompoundTag tag = Legends.tag(st);
        tag.putLong("moneysmp_slots", System.currentTimeMillis() + 30_000);
        Legends.putTag(st, tag);
        Legends.lore(st);
        p.setItemInHand(InteractionHand.MAIN_HAND, st);
        sound(p.level(), p.position(), SoundEvents.PLAYER_LEVELUP, 1, 1.5f);
        p.sendSystemMessage(Fmt.p("&6Crazy Slots &7rolled " + Legends.DEFS.get(id).name() + "&7 for 30 seconds."));
    }

    // a Crazy Slots transformation back to the slots, wherever it turns up
    public static ItemStack revert(ItemStack st) {
        return Legends.slotsUntil(st) > 0 ? Legends.make("crazy_slots") : st;
    }

    // every transformed item a player holds, put back. on death (before the drops), and once
    // a second for the ones whose time is up
    void revertAll(ServerPlayer p, boolean all) {
        Inventory inv = p.getInventory();
        long now = System.currentTimeMillis();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            long until = Legends.slotsUntil(inv.getItem(i));
            if (until > 0 && (all || until <= now)) {
                inv.setItem(i, Legends.make("crazy_slots"));
                if (!all) p.sendSystemMessage(Fmt.p("&6Crazy Slots &7turned back."));
            }
        }
    }

    // ── wand of illusion ─────────────────────────────────────────

    private void disguise(ServerPlayer p, ItemStack wand) {
        CompoundTag tag = Legends.tag(wand);
        String type = tag.getStringOr("moneysmp_mob", "");
        if (type.isEmpty()) {
            p.displayClientMessage(Fmt.c("&5The wand holds nothing yet. Kill something with it."), true);
            return;
        }
        ServerLevel level = p.level();
        LivingEntity shell;
        double health;
        if (type.equals("minecraft:player")) {
            Mannequin m = EntityType.MANNEQUIN.create(level, EntitySpawnReason.EVENT);
            if (m == null) return;
            String who = tag.getStringOr("moneysmp_victim", "");
            String uuid = tag.getStringOr("moneysmp_victim_id", "");
            if (!uuid.isEmpty()) m.setComponent(DataComponents.PROFILE, ResolvableProfile.createUnresolved(UUID.fromString(uuid)));
            m.setHideDescription(true);
            m.setCustomName(net.minecraft.network.chat.Component.literal(who));
            m.setCustomNameVisible(true);
            shell = m;
            health = 20;
        } else {
            EntityType<?> et = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(type));
            if (!(et.create(level, EntitySpawnReason.EVENT) instanceof LivingEntity le)) {
                p.displayClientMessage(Fmt.c("&5That can't be worn."), true);
                return;
            }
            shell = le;
            @SuppressWarnings("unchecked")
            EntityType<? extends LivingEntity> lt = (EntityType<? extends LivingEntity>) et;
            health = DefaultAttributes.hasSupplier(lt) ? DefaultAttributes.getSupplier(lt).getValue(Attributes.MAX_HEALTH) : 20;
        }
        if (shell instanceof Mob mob) mob.setNoAi(true);
        shell.setSilent(true);
        shell.setNoGravity(true);
        shell.snapTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot());
        Disguise d = new Disguise();
        d.shell = shell;
        d.type = type;
        disguised.put(p.getUUID(), d);
        shells.put(shell, p);
        spawnFx(level, shell);
        // big mobs are capped at two rows of hearts, tiny ones at one heart
        AttributeInstance max = p.getAttribute(Attributes.MAX_HEALTH);
        if (max != null) max.addOrUpdateTransientModifier(new AttributeModifier(ILLUSION, Math.max(2, Math.min(40, health)) - 20, AttributeModifier.Operation.ADD_VALUE));
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
        retrack(p);
        sound(level, p.position(), SoundEvents.BLAZE_SHOOT, 1, 1);
        particles(level, ParticleTypes.SMOKE, p.position().add(0, 1, 0), 120, 0.5, 0.05);
        p.displayClientMessage(Fmt.c("&5You disguised! &7Crouch and right-click to drop it."), true);
    }

    void undisguise(ServerPlayer p, boolean loud) {
        Disguise d = disguised.remove(p.getUUID());
        if (d == null) return;
        shells.remove(d.shell);
        dropFx(d.shell);
        AttributeInstance max = p.getAttribute(Attributes.MAX_HEALTH);
        if (max != null) max.removeModifier(ILLUSION);
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
        retrack(p);
        if (!loud) return;
        sound(p.level(), p.position(), SoundEvents.BLAZE_SHOOT, 1, 1);
        particles(p.level(), ParticleTypes.SMOKE, p.position().add(0, 1, 0), 120, 0.5, 0.05);
        p.displayClientMessage(Fmt.c("&5You removed your disguise."), true);
    }

    private void mobAbility(ServerPlayer p, Disguise d) {
        Config.Legendary cfg = cfg();
        ServerLevel level = p.level();
        switch (d.type) {
            case "minecraft:warden" -> {
                if (!ready(p, "illusion_warden", "&5Sonic Boom", cfg.wardenBoomCooldown, BossEvent.BossBarColor.PURPLE)) return;
                sound(level, p.position(), SoundEvents.WARDEN_SONIC_BOOM, 2, 1);
                for (int i = 0; i < 20; i++) particles(level, ParticleTypes.SONIC_BOOM, p.position().add(0, 1, 0), 1, 3, 0);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(5), e -> e != p && e != d.shell && e.isAlive())) {
                    e.hurtServer(level, trueDamage(level, ABILITY, p, p), (float) cfg.wardenBoomDamage);
                    e.setDeltaMovement(e.position().subtract(p.position()).normalize().scale(1.5));
                    e.hurtMarked = true;
                }
            }
            case "minecraft:enderman" -> {
                if (!ready(p, "illusion_enderman", "&5Ender Pearl", cfg.endermanCooldown, BossEvent.BossBarColor.PURPLE)) return;
                particles(level, ParticleTypes.END_ROD, p.position().add(0, 1, 0), 20, 0.5, 0.02);
                ThrownEnderpearl pearl = new ThrownEnderpearl(level, p, new ItemStack(Items.ENDER_PEARL));
                pearl.shootFromRotation(p, p.getXRot(), p.getYRot(), 0, 1.5f, 1);
                level.addFreshEntity(pearl);
            }
            case "minecraft:elder_guardian" -> {
                if (!ready(p, "illusion_elder", "&5Curse", cfg.elderGuardianCooldown, BossEvent.BossBarColor.PURPLE)) return;
                sound(level, p.position(), SoundEvents.ELDER_GUARDIAN_CURSE, 1, 1);
                for (ServerPlayer o : level.players()) {
                    if (o == p || o.isSpectator() || o.distanceToSqr(p) > 100) continue;
                    o.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 150, 2));
                    level.sendParticles(o, ParticleTypes.ELDER_GUARDIAN, false, false, o.getX(), o.getY(), o.getZ(), 1, 0, 0, 0, 0);
                }
            }
            case "minecraft:ender_dragon" -> {
                if (!ready(p, "illusion_dragon", "&5Dragon Breath", cfg.enderDragonCooldown, BossEvent.BossBarColor.PURPLE)) return;
                particles(level, PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1f), p.position().add(0, 1, 0), 20, 0.5, 0.02);
                Vec3 look = p.getLookAngle();
                DragonFireball fb = new DragonFireball(level, p, look);
                fb.setPos(p.getEyePosition().add(look.scale(2)));
                fb.setDeltaMovement(look.scale(1.5));
                level.addFreshEntity(fb);
            }
            default -> p.displayClientMessage(Fmt.c("&5This form has no ability."), true);
        }
    }

    // the shell walks with its wearer, and some forms can't leave the water (or go in it)
    private void tickDisguises(boolean second) {
        for (Iterator<Map.Entry<UUID, Disguise>> it = disguised.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Disguise> e = it.next();
            ServerPlayer p = plugin.server.getPlayerList().getPlayer(e.getKey());
            LivingEntity shell = e.getValue().shell;
            if (p == null || shell.isRemoved() || shell.level() != p.level() || !p.isAlive()) {
                it.remove();
                shells.remove(shell);
                dropFx(shell);
                if (p != null) {
                    AttributeInstance max = p.getAttribute(Attributes.MAX_HEALTH);
                    if (max != null) max.removeModifier(ILLUSION);
                    retrack(p);
                }
                continue;
            }
            shell.snapTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot());
            shell.setYHeadRot(p.getYHeadRot());
            shell.yBodyRot = p.yBodyRot;
            shell.setDeltaMovement(Vec3.ZERO);
            shell.clearFire();
            if (!second) continue;
            boolean wet = p.isInWater();
            boolean fish = shell instanceof WaterAnimal || shell instanceof Guardian;
            if (fish && !wet) p.hurtServer(p.level(), p.level().damageSources().dryOut(), 1);
            if (!fish && shell.isSensitiveToWater() && p.isInWaterOrRain()) p.hurtServer(p.level(), p.level().damageSources().drown(), 1);
        }
    }

    // LivingEntityMixin: shells neither push nor get pushed, or they'd shove their own wearer
    public static boolean isShell(Entity e) {
        return hooks != null && hooks.shells.containsKey(e);
    }

    private void capture(ServerPlayer killer, ItemStack wand, LivingEntity dead) {
        CompoundTag tag = Legends.tag(wand);
        if (dead instanceof ServerPlayer victim) {
            tag.putString("moneysmp_mob", "minecraft:player");
            tag.putString("moneysmp_victim", victim.getScoreboardName());
            tag.putString("moneysmp_victim_id", victim.getUUID().toString());
        } else {
            tag.putString("moneysmp_mob", BuiltInRegistries.ENTITY_TYPE.getKey(dead.getType()).toString());
            tag.remove("moneysmp_victim");
            tag.remove("moneysmp_victim_id");
        }
        Legends.putTag(wand, tag);
        Legends.lore(wand);
        killer.displayClientMessage(Fmt.c("&5The wand now holds &f" + dead.getName().getString() + "&5."), true);
        // the old form goes if they were wearing one
        undisguise(killer, false);
    }

    // ── damage and deaths ────────────────────────────────────────

    private boolean forwarding;

    // LivingEntityMixin, before anything else in hurtServer: false cancels the hit
    public static boolean allowHurt(LivingEntity victim, ServerLevel level, DamageSource src, float amount) {
        Weapons w = hooks;
        if (w == null) return true;
        ServerPlayer wearer = w.shells.get(victim);
        if (wearer != null) {
            // blows meant for the form land on whoever wears it; the world's own damage
            // (suffocating, drowning, burning) reaches them directly anyway
            if (!w.forwarding && (src.getEntity() != null || src.getDirectEntity() != null) && src.getEntity() != wearer) {
                w.forwarding = true;
                try {
                    wearer.hurtServer(level, src, amount);
                } finally {
                    w.forwarding = false;
                }
            }
            return false;
        }
        if (victim instanceof ServerPlayer p && w.ghosts.contains(p.getUUID())) return false;
        if (src.getEntity() instanceof ServerPlayer a && w.ghosts.contains(a.getUUID())) return false;
        if (src.is(DamageTypeTags.IS_FIRE) && Legends.is(victim.getMainHandItem(), "hyperion")) return false;
        if (src.is(DamageTypes.LIGHTNING_BOLT) && wearing(victim, EquipmentSlot.CHEST, "emerald_chestplate")) return false;
        if (src.is(DamageTypeTags.IS_FALL) && victim instanceof ServerPlayer p) {
            Long soft = w.softLanding.remove(p.getUUID());
            if (wearing(p, EquipmentSlot.LEGS, "emerald_leggings")) {
                // fall damage is the drop less the three blocks that are free
                if (amount >= 2) w.later.add(() -> w.shockwave(p, amount + 3));
                return false;
            }
            if (soft != null && soft > System.currentTimeMillis()) return false;
        }
        if (src.getEntity() instanceof ServerPlayer a && victim instanceof ServerPlayer && a != victim && wearing(a, EquipmentSlot.CHEST, "emerald_chestplate")
            && w.ringHits.merge(a.getUUID(), 1, Integer::sum) % 7 == 0) {
            w.later.add(() -> w.lightningRing(a));
        }
        if (!src.is(DamageTypes.PLAYER_ATTACK) || !(src.getEntity() instanceof ServerPlayer a) || src.getDirectEntity() != a) return true;
        String weapon = Legends.id(a.getMainHandItem());
        if (weapon == null) return true;
        switch (weapon) {
            case "bloodlust" -> {
                if (victim instanceof ServerPlayer p && ThreadLocalRandom.current().nextDouble() < w.cfg().bleedChance) w.bleed(p, a);
            }
            case "frost_scythe" -> slash(level, a, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ICE.defaultBlockState()), 6);
            case "hyperion" -> {
                slash(level, a, ParticleTypes.FLAME, 1);
                if (victim instanceof ServerPlayer p && Legends.hooks.species(p.getUUID()).equals("vampire") && w.hallowed.add(p.getUUID())) {
                    p.sendSystemMessage(Fmt.p("&6You have been inflicted with hallowed flames."));
                }
                // after this hit has landed, so the arc doesn't eat its invulnerability frames
                if (w.scorching.remove(a.getUUID())) w.later.add(() -> w.scorchingBlade(a));
            }
            case "shadow_blade" -> {
                Vec3 facing = victim.getLookAngle().multiply(1, 0, 1).normalize();
                Vec3 toAttacker = a.position().subtract(victim.position()).multiply(1, 0, 1).normalize();
                // after the hit, or its knockback would undo the pull
                if (facing.dot(toAttacker) < -0.5 && ThreadLocalRandom.current().nextDouble() < 0.3) {
                    w.later.add(() -> {
                        victim.setDeltaMovement(a.position().subtract(victim.position()).normalize().scale(0.8));
                        victim.hurtMarked = true;
                        particles(level, ParticleTypes.SQUID_INK, victim.position().add(0, 1, 0), 15, 0.3, 0.02);
                    });
                }
            }
            default -> {}
        }
        return true;
    }

    private static void slash(ServerLevel level, ServerPlayer p, ParticleOptions type, int count) {
        Vec3 eye = p.getEyePosition();
        Vec3 dir = p.getLookAngle();
        for (double i = -0.6; i <= 0.6; i += 0.15) particles(level, type, eye.add(dir.yRot((float) i).scale(4)), count, 0.1, 0.05);
    }

    // LivingEntityMixin: the amount a hit actually deals
    public static float modifyHurt(LivingEntity victim, DamageSource src, float amount) {
        if (hooks == null || !(victim instanceof ServerPlayer p)) return amount;
        // pale rots stab twice as hard from behind
        if (src.is(DamageTypes.PLAYER_ATTACK) && src.getEntity() instanceof ServerPlayer a && Legends.hooks.species(a.getUUID()).equals("pale")) {
            Vec3 facing = p.getLookAngle().multiply(1, 0, 1).normalize();
            Vec3 toAttacker = a.position().subtract(p.position()).multiply(1, 0, 1).normalize();
            if (facing.dot(toAttacker) < -0.5) {
                amount *= 2;
                sound((ServerLevel) p.level(), p.position(), SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, 1, 1);
            }
        }
        return amount;
    }

    // Bloodlust counts its player kills, the wand takes what it kills
    void onDeath(LivingEntity dead, DamageSource src) {
        if (dead instanceof ServerPlayer p) {
            undisguise(p, false);
            bleeding.remove(p.getUUID());
            hallowed.remove(p.getUUID());
        }
        if (!(src.getEntity() instanceof ServerPlayer killer) || killer == dead || shells.containsKey(dead)) return;
        ItemStack main = killer.getMainHandItem();
        String id = Legends.id(main);
        if ("bloodlust".equals(id) && dead instanceof ServerPlayer) {
            CompoundTag tag = Legends.tag(main);
            int kills = tag.getIntOr("moneysmp_kills", 0) + 1;
            tag.putInt("moneysmp_kills", kills);
            Legends.putTag(main, tag);
            Legends.lore(main);
            killer.sendSystemMessage(Fmt.p("&cBloodlust is on " + kills + " kill" + (kills == 1 ? "" : "s") + "."));
        } else if ("wand_of_illusion".equals(id)) {
            capture(killer, main, dead);
        }
    }

    void leave(ServerPlayer p) {
        UUID uid = p.getUUID();
        undisguise(p, false);
        Map<String, Cd> mine = cooldowns.remove(uid);
        if (mine != null) mine.values().forEach(cd -> cd.bar().removeAllPlayers());
        hidden.remove(uid);
        ghosts.remove(uid);
        lastPos.remove(uid);
        bleeding.remove(uid);
        dashes.removeIf(d -> {
            if (d.p != p) return false;
            d.bats.forEach(this::dropFx);
            return true;
        });
        hovering.removeIf(h -> {
            if (h.owner != p) return false;
            dropFx(h.ice);
            return true;
        });
        leaps.removeIf(l -> l.p == p);
        gusts.removeIf(g -> g.p == p);
        softLanding.remove(uid);
        crouching.remove(uid);
        ringHits.remove(uid);
    }

    // ── ticking ──────────────────────────────────────────────────

    void tick() {
        tick++;
        if (!later.isEmpty()) {
            List<Runnable> run = new ArrayList<>(later);
            later.clear();
            run.forEach(Runnable::run);
        }
        boolean second = tick % 20 == 0;
        tickHover();
        tickStuns();
        tickDashes();
        tickDisguises(second);
        tickLeaps();
        tickVulcanArrows();
        craters.removeIf(c -> {
            if (c.at() > tick) return false;
            fill(c);
            return true;
        });
        tickGusts();
        for (ServerPlayer p : plugin.server.getPlayerList().getPlayers()) tickVision(p);
        if (tick % 2 == 0) trails();
        if (tick % 5 == 0) drainBars();
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<UUID, Long>> it = hidden.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> e = it.next();
            if (e.getValue() > now) continue;
            it.remove();
            ghosts.remove(e.getKey());
            lastPos.remove(e.getKey());
            ServerPlayer p = plugin.server.getPlayerList().getPlayer(e.getKey());
            if (p != null) retrack(p);
        }
        if (!second) return;
        bites.removeIf(b -> {
            if (b.until() > now && !b.who().isRemoved()) return false;
            AttributeInstance inst = b.who().getAttribute(Attributes.MAX_HEALTH);
            if (inst != null) inst.removeModifier(b.id());
            if (b.who().getHealth() > b.who().getMaxHealth()) b.who().setHealth(b.who().getMaxHealth());
            return true;
        });
        for (ServerPlayer p : plugin.server.getPlayerList().getPlayers()) held(p);
        bleedTick();
        hallowedTick();
    }

    // passives of whatever is in the main hand or worn, once a second
    private void held(ServerPlayer p) {
        revertAll(p, false);
        if (wearing(p, EquipmentSlot.HEAD, "emerald_helmet")) p.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 40, 0, false, false, true));
        if (wearing(p, EquipmentSlot.CHEST, "emerald_chestplate")) p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 40, 0, false, false, true));
        if (wearing(p, EquipmentSlot.FEET, "emerald_boots")) {
            p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 40, 0, false, false, true));
            boolean emerald = p.level().getBlockState(p.blockPosition().below()).is(Blocks.EMERALD_BLOCK);
            p.addEffect(new MobEffectInstance(MobEffects.SPEED, 40, emerald ? 2 : 1, false, false, true));
        }
        ItemStack main = p.getMainHandItem();
        String id = Legends.id(main);
        if (id == null) return;
        switch (id) {
            case "bloodlust" -> {
                int kills = Legends.tag(main).getIntOr("moneysmp_kills", 0);
                if (kills >= 1) p.addEffect(new MobEffectInstance(MobEffects.SPEED, 40, 1, false, false, true));
                if (kills >= 4) p.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 40, 0, false, false, true));
                if (kills >= 2 && tick % 100 == 0) track(p);
            }
            case "nightpiercer" -> {
                if (p.level().getDayTime() % 24000 > 12300) p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 0, false, false, true));
            }
            case "hyperion" -> p.clearFire();
            case "shadow_blade" -> p.addEffect(new MobEffectInstance(MobEffects.SPEED, 40, 1, false, false, true));
            default -> {}
        }
    }

    // a line of blood to every player within 30 blocks, drawn for the holder alone
    private void track(ServerPlayer p) {
        ServerLevel level = p.level();
        Vec3 from = p.getEyePosition();
        for (ServerPlayer o : level.players()) {
            if (o == p || o.isSpectator() || o.distanceToSqr(p) > 900) continue;
            Vec3 to = o.getEyePosition();
            Vec3 step = to.subtract(from).normalize().scale(0.5);
            int n = (int) (from.distanceTo(to) * 2);
            Vec3 at = from;
            for (int i = 0; i <= n; i++) {
                level.sendParticles(p, ParticleTypes.DAMAGE_INDICATOR, true, false, at.x, at.y, at.z, 1, 0, 0, 0, 0);
                at = at.add(step);
            }
        }
    }

    // a ghost leaves blood where it walks
    private void trails() {
        for (UUID uid : ghosts) {
            ServerPlayer p = plugin.server.getPlayerList().getPlayer(uid);
            if (p == null) continue;
            Vec3 was = lastPos.put(uid, p.position());
            if (was != null && was.distanceToSqr(p.position()) > 0.0025) {
                particles(p.level(), new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()), p.position(), 6, 0.1, 0.1);
            }
        }
    }

    private void bleedTick() {
        for (Iterator<Map.Entry<UUID, Bleed>> it = bleeding.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Bleed> e = it.next();
            ServerPlayer p = plugin.server.getPlayerList().getPlayer(e.getKey());
            if (p == null || !p.isAlive() || e.getValue().secondsLeft-- <= 0) {
                it.remove();
                continue;
            }
            p.hurtServer(p.level(), trueDamage(p.level(), BLEED, null, null), (float) cfg().bleedDamagePerSecond);
            sound(p.level(), p.position(), SoundEvents.MAGMA_CUBE_DEATH, 1, 1);
        }
    }

    // hallowed flames: a point of damage and a burn each second, a one in five chance to go out
    private void hallowedTick() {
        for (Iterator<UUID> it = hallowed.iterator(); it.hasNext(); ) {
            ServerPlayer p = plugin.server.getPlayerList().getPlayer(it.next());
            if (p == null || !p.isAlive()) {
                it.remove();
                continue;
            }
            p.hurtServer(p.level(), trueDamage(p.level(), BLEED, null, null), 1);
            p.setRemainingFireTicks(Math.max(p.getRemainingFireTicks(), 30));
            if (ThreadLocalRandom.current().nextInt(5) == 0) it.remove();
        }
    }
}
