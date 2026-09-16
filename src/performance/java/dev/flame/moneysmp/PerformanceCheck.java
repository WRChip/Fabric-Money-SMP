package dev.flame.moneysmp;

import com.google.gson.JsonParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public final class PerformanceCheck {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory(Path.of("build"), "data-check-");
        Data data = new Data(dir, null);
        UUID uid = UUID.randomUUID();
        Data.PlayerData player = data.get(uid);
        player.name = "test";
        player.money = 100;
        player.team = "Red";
        data.teamMax = 12;
        for (int i = 0; i < 100_000; i++) {
            data.transactions.add(new Data.Tx(i / 2, "PAY", "a", "b", 1, "test"));
        }
        require(data.firstTransaction(-1) == 0, "before history");
        require(data.firstTransaction(10) == 20, "duplicate timestamps");
        require(data.firstTransaction(100_000) == 100_000, "after history");

        var writerField = Data.class.getDeclaredField("writer");
        writerField.setAccessible(true);
        ExecutorService writer = (ExecutorService) writerField.get(data);
        CountDownLatch release = new CountDownLatch(1);
        writer.execute(() -> {
            try {
                if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("save did not return");
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        });
        try {
            long start = System.nanoTime();
            data.save();
            System.out.printf("100,000-transaction snapshot: %.3f ms%n", (System.nanoTime() - start) / 1_000_000.0);
            var pendingField = Data.class.getDeclaredField("pending");
            pendingField.setAccessible(true);
            CompletableFuture<?> pending = (CompletableFuture<?>) pendingField.get(data);
            require(!pending.isDone(), "save must return before disk write");
            player.money = 200;
            data.log("GIVE", "admin", "test", 100, "later");
            data.save();
            require(pendingField.get(data) == pending, "busy writer must not accumulate saves");
            release.countDown();
            pending.get(30, TimeUnit.SECONDS);
            var saved = JsonParser.parseString(Files.readString(dir.resolve("data.json"))).getAsJsonObject();
            require(saved.getAsJsonObject("players").getAsJsonObject(uid.toString()).get("money").getAsDouble() == 100,
                    "snapshot must not observe later balance changes");
            require(saved.getAsJsonArray("transactions").size() == 100_000, "snapshot transaction isolation");

            CountDownLatch finish = new CountDownLatch(1);
            writer.execute(() -> {
                try {
                    if (!finish.await(10, TimeUnit.SECONDS)) throw new AssertionError("shutdown blocked test");
                } catch (InterruptedException e) {
                    throw new AssertionError(e);
                }
            });
            data.save();
            player.money = 300;
            CompletableFuture<Void> closing = CompletableFuture.runAsync(data::close);
            try {
                require(!closing.isDone(), "shutdown must wait for writer");
            } finally {
                finish.countDown();
            }
            closing.get(30, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            if (!writer.isShutdown()) data.close();
        }

        Data loaded = new Data(dir, null);
        try {
            loaded.load();
            require(loaded.money(uid) == 300, "shutdown flush must include latest balance");
            require("Red".equals(loaded.team(uid)), "team round trip");
            require(loaded.teamMax == 12 && loaded.teamCount == Teams.NAMES.size(), "settings round trip");
            require(loaded.transactions.size() == 100_001, "history round trip");
            require(!Files.exists(dir.resolve("data.json.tmp")), "temporary save must be replaced");
        } finally {
            loaded.close();
        }
        System.out.println("MoneySMP performance checks passed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
