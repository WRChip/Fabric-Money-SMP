package dev.flame.moneysmp;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class MoneySMP implements ModInitializer {
    static final Logger LOG = LoggerFactory.getLogger("MoneySMP");

    static final class Notify {
        final Component msg;
        int timer;

        Notify(String msg, int timer) {
            this.msg = Fmt.c(msg);
            this.timer = timer;
        }
    }

    final Map<UUID, Notify> notify = new HashMap<>();
    private record Status(double money, String team, Component msg) {}
    private final Map<UUID, Status> status = new HashMap<>();
    MinecraftServer server;
    Data data;
    Config config;
    final Auction auction = new Auction(this);
    private int tick;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            server = s;
            tick = 0;
            var dir = FabricLoader.getInstance().getConfigDir().resolve("moneysmp");
            config = Config.load(dir);
            data = new Data(dir, s);
            data.load();
            Teams.setup(s);
            for (ServerPlayer p : s.getPlayerList().getPlayers()) sync(p);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
            if (data != null) data.close();
            auction.running = false;
            notify.clear();
            status.clear();
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> onJoin(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> {
            notify.remove(handler.player.getUUID());
            status.remove(handler.player.getUUID());
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer victim) onDeath(victim);
        });
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            tick++;
            if (tick % 20 == 0) {
                actionBarTick();
                auction.tick();
            }
            if (tick % (20 * 60 * 5) == 0) data.save();
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) ->
            Commands.register(this, dispatcher));
    }

    void sync(ServerPlayer p) {
        Teams.sync(server, p, data.tier(p.getUUID()), data.team(p.getUUID()));
    }

    void notify(UUID uid, String msg, int secs) {
        notify.put(uid, new Notify(msg, secs));
    }

    private void onJoin(ServerPlayer p) {
        boolean fresh = !data.players.containsKey(p.getUUID());
        Data.PlayerData pd = data.get(p);
        if (fresh) pd.money = 100;
        sync(p);
    }

    // PvP only: attacker +$20, victim -$20. Mob kills give nothing.
    private void onDeath(ServerPlayer victim) {
        LivingEntity credit = victim.getKillCredit();
        if (!(credit instanceof ServerPlayer attacker) || attacker == victim) return;

        Data.PlayerData a = data.get(attacker);
        a.money += 20;
        String aMsg = "&a&l+ $20  &7Kill Reward!  &8|  &a$ &e" + Fmt.money(a.money);
        attacker.displayClientMessage(Fmt.c(aMsg), true);
        notify(attacker.getUUID(), aMsg, 6);
        data.log("KILL", attacker.getScoreboardName(), victim.getScoreboardName(), 20, "Kill reward");

        Data.PlayerData v = data.get(victim);
        v.money -= 20;
        String vMsg = "&c&l- $20  &7Killed by &f" + attacker.getScoreboardName() + "&7!  &8|  &a$ &e" + Fmt.money(v.money);
        victim.displayClientMessage(Fmt.c(vMsg), true);
        notify(victim.getUUID(), vMsg, 6);
        data.log("DEATH", victim.getScoreboardName(), attacker.getScoreboardName(), 20, "Death penalty");
    }

    private void actionBarTick() {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            UUID uid = p.getUUID();
            Notify n = notify.get(uid);
            if (n != null) {
                n.timer--;
                if (n.timer > 0) {
                    p.displayClientMessage(n.msg, true);
                    continue;
                }
                notify.remove(uid);
            }
            String team = data.team(uid);
            double money = data.money(uid);
            Status cached = status.get(uid);
            if (cached == null || cached.money() != money || !Objects.equals(cached.team(), team)) {
                String label = team == null ? "&7No Team" : Teams.color(team) + team;
                cached = new Status(money, team, Fmt.c("&a&l$ &e" + Fmt.money(money) + "  &8|  &7Team: " + label));
                status.put(uid, cached);
            }
            p.displayClientMessage(cached.msg(), true);
        }
    }
}
