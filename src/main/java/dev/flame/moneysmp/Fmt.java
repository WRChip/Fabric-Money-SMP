package dev.flame.moneysmp;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

final class Fmt {
    static final String PREFIX = "&8[&a&lMoneySMP&8]&r";

    private Fmt() {}

    // legacy &-codes -> Component. a colour code resets decorations, like the Bukkit side
    static MutableComponent c(String s) {
        MutableComponent out = Component.empty();
        Style style = Style.EMPTY;
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '&' && i + 1 < s.length()) {
                ChatFormatting f = ChatFormatting.getByCode(s.charAt(i + 1));
                if (f != null) {
                    if (buf.length() > 0) {
                        out.append(Component.literal(buf.toString()).withStyle(style));
                        buf.setLength(0);
                    }
                    if (f == ChatFormatting.RESET) style = Style.EMPTY;
                    else if (f.isColor()) style = Style.EMPTY.withColor(f);
                    else style = style.applyFormat(f);
                    i++;
                    continue;
                }
            }
            buf.append(ch);
        }
        if (buf.length() > 0) out.append(Component.literal(buf.toString()).withStyle(style));
        return out;
    }

    static MutableComponent p(String s) {
        return c(PREFIX + " " + s);
    }

    static String money(double n) {
        return String.format("%,d", (long) Math.floor(n));
    }

    // 30s / 10m / 2h / 1d -> seconds, -1 if unparsable
    static long parseTime(String raw) {
        if (raw.length() < 2) return -1;
        long num;
        try {
            num = (long) Double.parseDouble(raw.substring(0, raw.length() - 1));
        } catch (NumberFormatException e) {
            return -1;
        }
        return switch (raw.charAt(raw.length() - 1)) {
            case 's' -> num;
            case 'm' -> num * 60;
            case 'h' -> num * 3600;
            case 'd' -> num * 86400;
            default -> -1;
        };
    }

    static String timeAgo(long total) {
        if (total < 60) return total + "s";
        if (total < 3600) return (total / 60) + "m " + (total % 60) + "s";
        if (total < 86400) return (total / 3600) + "h " + ((total % 3600) / 60) + "m";
        return (total / 86400) + "d " + ((total % 86400) / 3600) + "h";
    }
}
