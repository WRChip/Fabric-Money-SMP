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
        } catch (IOException | RuntimeException e) {
            MoneySMP.LOG.error("could not read config.json, using defaults", e);
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
