package dev.flame.moneysmp;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class Teams {
    static final List<String> NAMES = List.of("Red", "Blue", "Purple", "Green", "White", "Gold");

    private static final ChatFormatting[] MC_COLORS = {
        ChatFormatting.RED, ChatFormatting.BLUE, ChatFormatting.DARK_PURPLE,
        ChatFormatting.GREEN, ChatFormatting.WHITE, ChatFormatting.GOLD
    };

    private Teams() {}

    static String color(String team) {
        if (team == null) return "&7";
        return switch (team) {
            case "Red" -> "&c";
            case "Blue" -> "&9";
            case "Purple" -> "&5";
            case "Green" -> "&a";
            case "White" -> "&f";
            case "Gold" -> "&6";
            default -> "&7";
        };
    }

    // first `count` team slots, minus whichever of those are disabled
    static List<String> active(int count, Set<String> disabled) {
        List<String> out = new ArrayList<>();
        for (String t : NAMES.subList(0, count)) if (!disabled.contains(t)) out.add(t);
        return out;
    }

    // same capitalisation the script does: "rED" -> "Red"
    static String normalise(String in) {
        if (in.isEmpty()) return in;
        return Character.toUpperCase(in.charAt(0)) + in.substring(1).toLowerCase();
    }

    static void setup(MinecraftServer server) {
        ServerScoreboard sb = server.getScoreboard();
        for (String t : NAMES) ensure(sb, null, t);
    }

    // one scoreboard team per (tier, team) pair: the team gives the colour, the tier the prefix.
    // vanilla puts the prefix in chat, the tab list and the name tag for us
    private static PlayerTeam ensure(ServerScoreboard sb, String tier, String team) {
        String id = "msmp_" + (tier == null ? team : team == null ? "tier_" + tier : tier + "_" + team);
        PlayerTeam t = sb.getPlayerTeam(id);
        if (t == null) t = sb.addPlayerTeam(id);
        t.setColor(team == null ? ChatFormatting.RESET : MC_COLORS[NAMES.indexOf(team)]);
        t.setPlayerPrefix(tier == null ? Component.empty() : Fmt.c(Tiers.prefix(tier)));
        t.setAllowFriendlyFire(true);
        t.setNameTagVisibility(Team.Visibility.ALWAYS);
        return t;
    }

    static void leave(MinecraftServer server, ServerPlayer p) {
        ServerScoreboard sb = server.getScoreboard();
        PlayerTeam t = sb.getPlayersTeam(p.getScoreboardName());
        if (t != null) sb.removePlayerFromTeam(p.getScoreboardName(), t);
    }

    static void sync(MinecraftServer server, ServerPlayer p, String tier, String team) {
        if (tier == null && team == null) {
            leave(server, p);
            return;
        }
        ServerScoreboard sb = server.getScoreboard();
        sb.addPlayerToTeam(p.getScoreboardName(), ensure(sb, tier, team));
    }
}
