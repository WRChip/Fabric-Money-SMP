package dev.flame.moneysmp;

import net.minecraft.world.level.material.MapColor;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// draws one team's view of the unlockout board as 128x128 map pixels. cells are 25px
// apart with a 24px interior: a row of dots on top for every team that has the goal (in
// the order they got it), three lines of label and a progress bar along the bottom
final class UnlockoutMap {
    private static final int PITCH = 25;
    private static final byte BG = MapColor.COLOR_BLACK.getPackedId(MapColor.Brightness.LOW);
    private static final byte GRID = MapColor.COLOR_GRAY.getPackedId(MapColor.Brightness.LOW);
    private static final byte TEXT = MapColor.SNOW.getPackedId(MapColor.Brightness.HIGH);
    private static final byte DARK_TEXT = MapColor.COLOR_BLACK.getPackedId(MapColor.Brightness.NORMAL);
    private static final byte BAR_BG = MapColor.COLOR_GRAY.getPackedId(MapColor.Brightness.LOWEST);

    private static final Map<String, MapColor> TEAM = Map.of(
        "Red", MapColor.COLOR_RED, "Blue", MapColor.COLOR_BLUE, "Purple", MapColor.COLOR_PURPLE,
        "Green", MapColor.EMERALD, "White", MapColor.SNOW, "Gold", MapColor.GOLD,
        "Yellow", MapColor.COLOR_YELLOW, "Aqua", MapColor.DIAMOND, "Pink", MapColor.COLOR_PINK);

    // 3x5 glyphs, rows top to bottom
    private static final Map<Character, String[]> FONT = new HashMap<>();
    static {
        String[] defs = {
            "A ### #.# ### #.# #.#", "B ##. #.# ##. #.# ##.", "C ### #.. #.. #.. ###", "D ##. #.# #.# #.# ##.",
            "E ### #.. ##. #.. ###", "F ### #.. ##. #.. #..", "G ### #.. #.# #.# ###", "H #.# #.# ### #.# #.#",
            "I ### .#. .#. .#. ###", "J ..# ..# ..# #.# ###", "K #.# #.# ##. #.# #.#", "L #.. #.. #.. #.. ###",
            "M #.# ### ### #.# #.#", "N ##. #.# #.# #.# #.#", "O ### #.# #.# #.# ###", "P ### #.# ### #.. #..",
            "Q ### #.# #.# ### ..#", "R ### #.# ##. #.# #.#", "S ### #.. ### ..# ###", "T ### .#. .#. .#. .#.",
            "U #.# #.# #.# #.# ###", "V #.# #.# #.# #.# .#.", "W #.# #.# ### ### #.#", "X #.# #.# .#. #.# #.#",
            "Y #.# #.# .#. .#. .#.", "Z ### ..# .#. #.. ###", "0 ### #.# #.# #.# ###", "1 .#. ##. .#. .#. ###",
            "2 ### ..# ### #.. ###", "3 ### ..# ### ..# ###", "4 #.# #.# ### ..# ..#", "5 ### #.. ### ..# ###",
            "6 ### #.. ### #.# ###", "7 ### ..# ..# ..# ..#", "8 ### #.# ### #.# ###", "9 ### #.# ### ..# ###",
            "= ... ### ... ### ...", "+ ... .#. ### .#. ...", "- ... ... ### ... ...", ". ... ... ... ... .#.",
        };
        for (String d : defs) {
            String[] parts = d.split(" ");
            FONT.put(parts[0].charAt(0), Arrays.copyOfRange(parts, 1, 6));
        }
    }

    private UnlockoutMap() {}

    static byte[] draw(Unlockout u, String team) {
        byte[] px = new byte[128 * 128];
        Arrays.fill(px, BG);
        for (int i = 0; i <= 5; i++) {
            int at = 1 + PITCH * i;
            for (int k = 1; k <= 126; k++) {
                px[at * 128 + k] = GRID;
                px[k * 128 + at] = GRID;
            }
        }
        MapColor mine = TEAM.get(team);
        for (int g = 0; g < Unlockout.GOALS.size(); g++) {
            int ox = 2 + PITCH * (g % 5);
            int oy = 2 + PITCH * (g / 5);
            boolean have = mine != null && u.isDone(team, g);
            byte text = TEXT;
            if (have) {
                fill(px, ox, oy, 24, 24, mine.getPackedId(MapColor.Brightness.LOW));
                if (team.equals("White")) text = DARK_TEXT;
            }
            List<String> order = u.doneOrder(g);
            int step = order.size() > 8 ? 2 : 3;
            for (int i = 0; i < order.size(); i++) {
                MapColor c = TEAM.get(order.get(i));
                if (c != null) fill(px, ox + i * step, oy, 2, 2, c.getPackedId(MapColor.Brightness.NORMAL));
            }
            String[] lines = Unlockout.GOALS.get(g).label().split("\\|");
            for (int i = 0; i < lines.length; i++) text(px, ox, oy + 3 + i * 6, lines[i], text);
            if (mine != null) {
                fill(px, ox, oy + 21, 24, 3, BAR_BG);
                int w = (int) Math.round(u.fraction(team, g) * 24);
                if (w > 0) fill(px, ox, oy + 21, w, 3, mine.getPackedId(MapColor.Brightness.HIGH));
            }
        }
        return px;
    }

    private static void fill(byte[] px, int x, int y, int w, int h, byte color) {
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) px[(y + j) * 128 + x + i] = color;
        }
    }

    private static void text(byte[] px, int x, int y, String s, byte color) {
        for (char ch : s.toCharArray()) {
            String[] glyph = FONT.get(ch);
            if (glyph != null) {
                for (int r = 0; r < 5; r++) {
                    for (int c = 0; c < 3; c++) {
                        if (glyph[r].charAt(c) == '#') px[(y + r) * 128 + x + c] = color;
                    }
                }
            }
            x += 4;
        }
    }
}
