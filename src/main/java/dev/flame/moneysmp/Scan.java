package dev.flame.moneysmp;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

// who is holding a legendary, offline players included. everyone is read from playerdata/*.dat
// after the online ones are flushed to disk, so there is one code path and shulkers and bundles
// are covered by the walk. loose items and containers in the world are not looked at.
final class Scan {
    record Hit(UUID uid, boolean online, String where, String at) {}

    private Scan() {}

    static List<Hit> find(MinecraftServer server, String id) {
        server.getPlayerList().saveAll();
        List<Hit> hits = new ArrayList<>();
        Path dir = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : files.filter(f -> f.getFileName().toString().endsWith(".dat")).toList()) {
                String file = f.getFileName().toString();
                UUID uid;
                try {
                    uid = UUID.fromString(file.substring(0, file.length() - 4));
                } catch (IllegalArgumentException e) {
                    continue;
                }
                try {
                    CompoundTag root = NbtIo.readCompressed(f, NbtAccounter.unlimitedHeap());
                    String where = where(root, id);
                    if (where != null) hits.add(new Hit(uid, server.getPlayerList().getPlayer(uid) != null, where, at(root)));
                } catch (IOException e) {
                    MoneySMP.LOG.warn("scan: couldn't read {}", file, e);
                }
            }
        } catch (IOException e) {
            MoneySMP.LOG.warn("scan: couldn't list {}", dir, e);
        }
        return hits;
    }

    private static String where(CompoundTag root, String id) {
        // armour, hands and the like live apart from the 36 slots in newer saves
        for (Map.Entry<String, Tag> e : root.getCompoundOrEmpty("equipment").entrySet()) {
            if (e.getValue() instanceof CompoundTag stack && depth(stack, id, 0) >= 0) return "worn";
        }
        for (String section : List.of("Inventory", "EnderItems")) {
            for (Tag t : root.getListOrEmpty(section)) {
                if (!(t instanceof CompoundTag slot)) continue;
                int depth = depth(slot, id, 0);
                if (depth < 0) continue;
                if (section.equals("EnderItems")) return depth > 0 ? "ender chest, inside a container" : "ender chest";
                if (depth > 0) return "inventory, inside a container";
                return "inventory";
            }
        }
        return null;
    }

    // how many item stacks deep the legendary sits (0 = the stack itself), -1 if it isn't in there
    private static int depth(CompoundTag stack, String id, int level) {
        CompoundTag comps = stack.getCompoundOrEmpty("components");
        if (id.equals(comps.getCompoundOrEmpty("minecraft:custom_data").getStringOr(Legends.KEY, ""))) return level;
        int best = -1;
        for (Map.Entry<String, Tag> e : comps.entrySet()) {
            if (e.getKey().equals("minecraft:custom_data")) continue;
            best = Math.max(best, inside(e.getValue(), id, level + 1));
        }
        return best;
    }

    // containers and bundles keep their stacks in a list of {item} or {slot, item} entries, so
    // look for compounds that carry a stack (an "id") anywhere under the component
    private static int inside(Tag t, String id, int level) {
        int best = -1;
        if (t instanceof CompoundTag c) {
            if (c.contains("id") && c.contains("count")) return depth(c, id, level);
            for (Tag child : c.values()) best = Math.max(best, inside(child, id, level));
        } else if (t instanceof ListTag l) {
            for (Tag child : l) best = Math.max(best, inside(child, id, level));
        }
        return best;
    }

    private static String at(CompoundTag root) {
        ListTag pos = root.getListOrEmpty("Pos");
        if (pos.size() < 3) return "";
        return (int) pos.getDoubleOr(0, 0) + ", " + (int) pos.getDoubleOr(1, 0) + ", " + (int) pos.getDoubleOr(2, 0)
            + " in " + root.getStringOr("Dimension", "?").replace("minecraft:", "");
    }
}
