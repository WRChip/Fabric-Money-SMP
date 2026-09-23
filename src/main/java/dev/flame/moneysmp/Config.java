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
    // per captured control point: team points, and money for every member of the team
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
    // per unlockout point earned (goal, line or full board), paid to every member of the team
    double unlockoutMoneyPerPoint = 10;
    // why the file couldn't be read, so /moneysmp reload can keep the old settings instead
    transient String error;

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
            if (y.has("unlockoutMoneyPerPoint")) c.unlockoutMoneyPerPoint = y.get("unlockoutMoneyPerPoint").getAsDouble();
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
