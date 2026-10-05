package dev.flame.moneysmp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

// config.json next to data.json. written with defaults the first time so admins can edit it
final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    double minBidIncrease = 5;
    int bidSeconds = 20;
    final Map<String, Double> tierMinimum = new LinkedHashMap<>();
    // per captured control point: team points, and money split between the team's online members
    int controlPointPoints = 10;
    double controlPointMoney = 100;
    double controlPointSuperMoney = 100;
    // capture progress one player earns per 30s; five players at 5 take two minutes
    double controlPointPercentPer30s = 5;
    // ring radius, and how far above the point still counts as inside
    int controlPointRadius = 5;
    int controlPointHeight = 10;
    // share of points picked as super each event, and how many times slower they capture
    double controlPointSuperPercent = 25;
    double controlPointSuperSlowdown = 4;
    // altar event: how long the ritual runs, and how long after first picking up a
    // fragment logging out still drops it
    int altarRitualMinutes = 30;
    int altarLogoutMinutes = 30;
    // a carrier has to be online this long out of every "day", or the fragment goes back to its spot
    int altarDailyPlayMinutes = 60;
    int altarDayMinutes = 1440;
    // how often every carrier's position is broadcast
    int altarLocateOnlineMinutes = 15;
    int altarLocateOfflineMinutes = 360;
    Legendary legendary = new Legendary();
    // why the file couldn't be read, so /moneysmp reload can keep the old settings instead
    transient String error;

    // the Altar SMP legendaries. cost is what each altar takes from the crafter's balance on top
    // of its items; damage is in health points (a heart is 2)
    static final class Legendary {
        Map<String, Double> cost = new LinkedHashMap<>();
        double bleedChance = 0.15;
        int bleedSeconds = 4;
        double bleedDamagePerSecond = 1;
        double vulcanArrowDamage = 6;
        double vulcanWrathDamage = 10;
        // ability damage is all true damage: armour, enchantments and effects don't lower it
        double scytheThrowDamage = 6;
        double scytheIceDamage = 5;
        double wardenBoomDamage = 10;
        int wardenBoomCooldown = 60;
        int elderGuardianCooldown = 120;
        int endermanCooldown = 30;
        int enderDragonCooldown = 60;
        double scorchingBladeDamage = 3;
        double holyLanceDamage = 8;
        double holyLanceBonusVsNonHumans = 2;
        double paleShotDamage = 5;
        double paleBigShotDamage = 10;
        boolean vampireFireResistance = true;
        boolean paleWeaknessInRain = true;
        int contagionSeconds = 300;
        int contagionIntegrity = 40;
        double shadowDaggerDamage = 3;
        double windGustDamage = 6;
        double lightningRingDamage = 5;

        Legendary() {
            String[] ids = {"bone_blade", "bloodlust", "frost_scythe", "vulcans_crossbow", "wand_of_illusion",
                "hyperion_shard", "nightpiercer_shard", "pale_shard", "hyperion", "nightpiercer", "pale_cannon", "crazy_slots", "contagion",
                "shadow_blade", "windweaver", "emerald_helmet", "emerald_chestplate", "emerald_leggings", "emerald_boots", "emerald_pickaxe"};
            double[] dollars = {500, 500, 500, 500, 750, 50, 50, 50, 300, 300, 300, 1000, 500, 500, 500, 250, 250, 250, 250, 250};
            for (int i = 0; i < ids.length; i++) cost.put(ids[i], dollars[i]);
        }

        double cost(String id) {
            Double v = cost.get(id);
            return v == null ? 0 : Math.max(0, v);
        }
    }

    Config() {
        double[] mins = {100, 70, 50, 40, 35, 30, 30};
        for (int i = 0; i < mins.length; i++) tierMinimum.put(Tiers.NAMES.get(i), mins[i]);
    }

    double minimum(String tier) {
        Double v = tierMinimum.get(tier);
        return v == null ? 0 : v;
    }

    static Config load(Path dir) {
        Config c = new Config();
        Path file = dir.resolve("config.json");
        if (!Files.exists(file)) {
            c.write(file);
            return c;
        }
        try {
            JsonObject y = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if (y.has("minBidIncrease")) c.minBidIncrease = y.get("minBidIncrease").getAsDouble();
            if (y.has("bidSeconds")) c.bidSeconds = Math.max(1, y.get("bidSeconds").getAsInt());
            if (y.has("tierMinimum")) {
                JsonObject m = y.getAsJsonObject("tierMinimum");
                for (String t : Tiers.NAMES) if (m.has(t)) c.tierMinimum.put(t, m.get(t).getAsDouble());
            }
            if (y.has("controlPointPoints")) c.controlPointPoints = y.get("controlPointPoints").getAsInt();
            if (y.has("controlPointMoney")) c.controlPointMoney = y.get("controlPointMoney").getAsDouble();
            if (y.has("controlPointSuperMoney")) c.controlPointSuperMoney = y.get("controlPointSuperMoney").getAsDouble();
            // zero or less would make points uncapturable, so keep it above nothing
            if (y.has("controlPointPercentPer30s")) c.controlPointPercentPer30s = Math.max(0.01, y.get("controlPointPercentPer30s").getAsDouble());
            if (y.has("controlPointRadius")) c.controlPointRadius = Math.max(1, y.get("controlPointRadius").getAsInt());
            if (y.has("controlPointHeight")) c.controlPointHeight = Math.max(1, y.get("controlPointHeight").getAsInt());
            if (y.has("controlPointSuperPercent")) c.controlPointSuperPercent = Math.max(0, Math.min(100, y.get("controlPointSuperPercent").getAsDouble()));
            if (y.has("controlPointSuperSlowdown")) c.controlPointSuperSlowdown = Math.max(0.01, y.get("controlPointSuperSlowdown").getAsDouble());
            if (y.has("altarRitualMinutes")) c.altarRitualMinutes = Math.max(1, y.get("altarRitualMinutes").getAsInt());
            if (y.has("altarLogoutMinutes")) c.altarLogoutMinutes = Math.max(1, y.get("altarLogoutMinutes").getAsInt());
            if (y.has("altarDailyPlayMinutes")) c.altarDailyPlayMinutes = Math.max(1, y.get("altarDailyPlayMinutes").getAsInt());
            if (y.has("altarDayMinutes")) c.altarDayMinutes = Math.max(1, y.get("altarDayMinutes").getAsInt());
            if (y.has("altarLocateOnlineMinutes")) c.altarLocateOnlineMinutes = Math.max(1, y.get("altarLocateOnlineMinutes").getAsInt());
            if (y.has("altarLocateOfflineMinutes")) c.altarLocateOfflineMinutes = Math.max(1, y.get("altarLocateOfflineMinutes").getAsInt());
            // anything missing keeps its default; costs left out of the file keep theirs too
            if (y.has("legendary")) {
                Legendary defaults = new Legendary();
                c.legendary = GSON.fromJson(y.get("legendary"), Legendary.class);
                defaults.cost.forEach(c.legendary.cost::putIfAbsent);
                c.legendary.contagionSeconds = Math.max(10, c.legendary.contagionSeconds);
                c.legendary.contagionIntegrity = Math.max(1, c.legendary.contagionIntegrity);
            }
        } catch (IOException | RuntimeException e) {
            MoneySMP.LOG.error("could not read config.json", e);
            c.error = e.getMessage();
        }
        return c;
    }

    private void write(Path file) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(this));
        } catch (IOException e) {
            MoneySMP.LOG.error("could not write config.json", e);
        }
    }
}
