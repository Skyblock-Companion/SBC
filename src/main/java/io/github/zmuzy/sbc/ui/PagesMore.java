// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.ui;

import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.LegacyItems;
import io.github.zmuzy.sbc.core.Prices;
import io.github.zmuzy.sbc.core.Profile;
import io.github.zmuzy.sbc.core.Repo;
import io.github.zmuzy.sbc.core.SbTime;
import io.github.zmuzy.sbc.core.Store;
import io.github.zmuzy.sbc.nav.Graph;
import io.github.zmuzy.sbc.nav.Navigator;
import io.github.zmuzy.sbc.track.Tracker;
import io.github.zmuzy.sbc.ui.hud.Looks;
import io.github.zmuzy.sbc.ui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// profile viewer, navigate and stats pages
final class PagesMore {
    private static final MenuUi.Field lookup = new MenuUi.Field("Player name…", false);
    private static final MenuUi.Field itemSearch = new MenuUi.Field("Search their items…", false);
    private static final MenuUi.Field search = new MenuUi.Field("Search any NPC, warp or place (e.g. Fetchur, Elle, Bazaar)", false);
    private static final String[] SUB = {"Overview", "Networth", "Skills", "Slayers", "Dungeons", "Pets", "Items", "Accessories", "Collections", "More stats", "Trees"};
    static int sub() {
        return sub;
    }

    private static final int[] SUB_COLOR = {0xFF5CD8FF, 0xFFFFD166, 0xFF9CE37D, 0xFFFF5252, 0xFFFF5D73, 0xFFFFB454, 0xFF7FD4FF, 0xFFB388FF, 0xFFD8B26E, 0xFF90A4AE, 0xFFC77DFF};
    private static int sub;

    private static final int PROFILE = MenuUi.PURPLE, NETWORTH = 0xFFFFD166, DUNGEON = 0xFFFF5D73, NAV = 0xFF5CD8FF, STATS = 0xFF7FB3FF;
    private PagesMore() {}

    // --- level looks ---

    static String skillIcon(String skill) {
        return switch (skill) {
            case "Farming" -> "golden_hoe";
            case "Mining" -> "iron_pickaxe";
            case "Combat" -> "iron_sword";
            case "Foraging" -> "oak_sapling";
            case "Fishing" -> "fishing_rod";
            case "Enchanting" -> "enchanting_table";
            case "Alchemy" -> "brewing_stand";
            case "Taming" -> "bone";
            case "Carpentry" -> "crafting_table";
            case "Hunting" -> "lead";
            case "Runecrafting" -> "magma_cream";
            case "Social" -> "emerald";
            default -> "book";
        };
    }

    static int skillColor(String skill) {
        return switch (skill) {
            case "Farming" -> 0xFFFFD166;
            case "Mining" -> 0xFF9BE7FF;
            case "Combat" -> 0xFFFF6B6B;
            case "Foraging" -> 0xFF81C784;
            case "Fishing" -> 0xFF4FC3F7;
            case "Enchanting" -> 0xFFC77DFF;
            case "Alchemy" -> 0xFFFF80AB;
            case "Taming" -> 0xFFFFB454;
            case "Carpentry" -> 0xFFD8B26E;
            case "Hunting" -> 0xFFA5D6A7;
            case "Runecrafting" -> 0xFFB388FF;
            case "Social" -> 0xFF7FD4FF;
            default -> 0xFF5CD8FF;
        };
    }

    static int skillCap(String skill) {
        return switch (skill) {
            case "Farming", "Mining", "Combat", "Enchanting", "Taming" -> 60;
            case "Foraging" -> 57;
            case "Runecrafting", "Social" -> 25;
            default -> 50;
        };
    }

    private static String slayerIcon(String name) {
        return switch (name) {
            case "Revenant" -> "rotten_flesh";
            case "Tarantula" -> "string";
            case "Sven" -> "bone";
            case "Voidgloom" -> "ender_pearl";
            case "Inferno" -> "blaze_powder";
            case "Riftstalker" -> "redstone";
            default -> "iron_sword";
        };
    }

    private static int slayerColor(String name) {
        return switch (name) {
            case "Revenant" -> 0xFF9CCC65;
            case "Tarantula" -> 0xFFB388FF;
            case "Sven" -> 0xFF90CAF9;
            case "Voidgloom" -> 0xFFC77DFF;
            case "Inferno" -> 0xFFFF9F45;
            case "Riftstalker" -> 0xFFFF5252;
            default -> 0xFFFF5252;
        };
    }

    static String classIcon(String name) {
        return switch (name) {
            case "Healer" -> "potion";
            case "Mage" -> "blaze_rod";
            case "Berserk" -> "iron_sword";
            case "Archer" -> "bow";
            case "Tank" -> "iron_chestplate";
            default -> "skeleton_skull";
        };
    }

    static int classColor(String name) {
        return switch (name) {
            case "Healer" -> 0xFFFF80AB;
            case "Mage" -> 0xFF7FB3FF;
            case "Berserk" -> 0xFFFF6B6B;
            case "Archer" -> 0xFF9CE37D;
            case "Tank" -> 0xFF90A4AE;
            default -> DUNGEON;
        };
    }

    // xp inside the current level
    static double have(Profile.Level l) {
        return need(l) - l.toNext();
    }

    // xp this level takes in total
    static double need(Profile.Level l) {
        return l.progress() >= 1 ? 0 : l.toNext() / (1 - l.progress());
    }

    // === PROFILE ===

    static int profile(MenuUi ui, int x, int y, int w) {
        int y0 = y, gap = MenuUi.GAP;
        Profile.Data d = Profile.viewed != null ? Profile.viewed : Profile.current;
        // other players need the api. reading from the game you only have your own profile
        if (d != null && d.fromGame) return ProfileFromGame.page(ui, x, y, w, d);
        if (d == null && !Profile.serverUp()) return observed(ui, x, y, w);
        lookup.onEnter = v -> {
            if (!v.isBlank()) Profile.lookup(v);
        };
        ui.input(lookup, x, y, 170);
        ui.button("pl", x + 174, y, 56, 18, "Look up", Ui.SOFT, () -> {
            if (!lookup.value.isBlank()) Profile.lookup(lookup.value);
        });
        ui.button("pme", x + 234, y, 66, 18, "My profile", Ui.PRIMARY, Profile::loadSelf);
        int right = x + w;
        if (d != null && d.profiles.size() > 1) {
            final Profile.Data from = d;
            for (int i = d.profiles.size() - 1; i >= 0; i--) {
                Profile.Ref ref = d.profiles.get(i);
                String label = (ref.mode().isEmpty() ? "" : "♲ ") + ref.name();
                int bw = ui.width(label) + 10;
                right -= bw;
                ui.button("prof" + i, right, y, bw, 18, label, ref.shown() ? Ui.SOFT : Ui.GHOST, () -> {
                    if (!ref.shown()) Profile.switchTo(from, ref.id());
                });
                right -= 3;
            }
        }
        if (!Profile.status.isEmpty()) ui.text(ui.ellipsize(Profile.status, right - x - 310), x + 306, y + 5, Ui.ORANGE);
        y += 18 + gap;

        if (d == null) return observed(ui, x, y, w) + (y - y0);

        // header card
        int h = 60;
        ui.edgeCard(x, y, w, h, PROFILE);
        ui.gradH(x + 2, y + 1, w / 2, h - 2, Ui.alpha(MenuUi.tone(PROFILE), 0.14f), Ui.alpha(MenuUi.tone(PROFILE), 0.0f));
        final Profile.Data shown = d;
        final net.minecraft.world.item.ItemStack head = headOf(d);
        ui.paint(x + 3, y + 2, 46, h - 4, (u, px, py, pw, ph) -> {
            if (!PlayerModel.draw(u, shown, px, py, pw, ph)) u.icon(head, px + 4, py + 9, 2.4f);
        });
        ui.textScaled(d.player, x + 52, y + 5, 1.5f, Ui.TEXT, true);
        ui.text(d.profileName + (d.gameMode.isEmpty() ? "" : " · " + Profile.cap(d.gameMode)), x + 52, y + 18, Ui.MUTED);
        int lvl = (int) (d.sbXp / 100), into = (int) (d.sbXp % 100);
        ui.text("Level " + lvl, x + 52, y + 29, Ui.CYAN);
        ui.text(into + "/100 XP · " + into + "%", x + 52 + ui.width("Level " + lvl) + 6, y + 29, Ui.MUTED);
        ui.meter(x + 52, y + 40, 166, 3, into / 100.0, 0xFF5CD8FF);
        if (d.firstJoin > 0) ui.text("Playing for " + SbTime.dur(System.currentTimeMillis() - d.firstJoin), x + 52, y + 47, Ui.DIM);
        int colW = 150, cr = x + w - 6, cl = cr - colW, ml = cl - 10 - colW;
        ui.row("Networth", Prices.coins(d.networth()), cl, y + 5, colW, Ui.GREEN);
        ui.row("Purse", Prices.coins(d.purse), cl, y + 18, colW, Ui.GOLD);
        ui.row("Bank", Prices.coins(d.bank), cl, y + 31, colW, Ui.GOLD);
        if (d.personalBank >= 0) ui.tip(cl, y + 30, colW, 11, "Bank", "§cPersonal: " + MenuUi.num(d.personalBank), "§6Co-op: " + MenuUi.num(d.bank - d.personalBank), "§eTotal: " + MenuUi.num(d.bank));
        ui.row("Magical Power", MenuUi.num(d.magicalPower), cl, y + 44, colW, MenuUi.tone(MenuUi.PURPLE));
        if (ml > x + 224) {
            ui.row("Secrets", MenuUi.num(d.secrets), ml, y + 44, colW, Ui.TEXT);
            ui.row("Skill average", String.format(Locale.ROOT, "%.2f", d.skillAverage), ml, y + 5, colW, Ui.TEXT);
            ui.row("Catacombs", d.catacombs == null ? "-" : d.catacombs.level() + " · " + MenuUi.pct(d.catacombs.progress()), ml, y + 18, colW, MenuUi.tone(DUNGEON));
            ui.row("Fairy souls", String.valueOf(d.fairySouls), ml, y + 31, colW, Ui.PINK);
        } else {
            ui.row("Skill avg", String.format(Locale.ROOT, "%.2f", d.skillAverage), cl - 86, y + 5, 80, Ui.TEXT);
            ui.row("Cata", d.catacombs == null ? "-" : String.valueOf(d.catacombs.level()), cl - 86, y + 18, 80, MenuUi.tone(DUNGEON));
            ui.row("Souls", String.valueOf(d.fairySouls), cl - 86, y + 31, 80, Ui.PINK);
        }
        y += h + gap;

        y += subTabs(ui, x, y, w) + gap;
        y += switch (sub) {
            case 1 -> networth(ui, x, y, w, d);
            case 2 -> skills(ui, x, y, w, d);
            case 3 -> slayers(ui, x, y, w, d);
            case 4 -> dungeons(ui, x, y, w, d);
            case 5 -> pets(ui, x, y, w, d);
            case 6 -> items(ui, x, y, w, d);
            case 7 -> accessories(ui, x, y, w, d);
            case 8 -> collections(ui, x, y, w, d);
            case 9 -> moreStats(ui, x, y, w, d);
            case 10 -> ProfileTrees.page(ui, x, y, w, d);
            default -> overview(ui, x, y, w, d);
        };
        return y - y0;
    }

    static int subTabs(MenuUi ui, int x, int y, int w) {
        int tx = x, n = SUB.length;
        int total = 0;
        for (int i = 0; i < n; i++) total += ui.width(SUB[i]);
        int extra = Math.max(6, (w - total - (n - 1) * 2) / n);
        for (int i = 0; i < n; i++) {
            int tw = ui.width(SUB[i]) + extra;
            boolean on = i == sub;
            int c = SUB_COLOR[i];
            ui.hoverRound(tx, y, tw, 16, 0, on ? Ui.alpha(c, 0.26f) : 0x12FFFFFF, on ? Ui.alpha(c, 0.26f) : 0x24FFFFFF);
            ui.rect(tx, y + 15, tw, 1, on ? c : Ui.alpha(c, 0.35f));
            ui.textCenter(SUB[i], tx + tw / 2, y + 4, on ? Ui.TEXT : Ui.MUTED);
            final int idx = i;
            ui.click(tx, y, tw, 16, () -> sub = idx);
            tx += tw + 2;
        }
        return 16;
    }

    private static net.minecraft.world.item.ItemStack headOf(Profile.Data d) {
        var mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getGameProfile().name().equalsIgnoreCase(d.player)) {
            var st = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PLAYER_HEAD);
            st.set(net.minecraft.core.component.DataComponents.PROFILE, net.minecraft.world.item.component.ResolvableProfile.createResolved(mc.player.getGameProfile()));
            return st;
        }
        if (d.uuid.length() == 32) {
            var st = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PLAYER_HEAD);
            st.set(net.minecraft.core.component.DataComponents.PROFILE, net.minecraft.world.item.component.ResolvableProfile.createUnresolved(com.mojang.util.UndashedUuid.fromStringLenient(d.uuid)));
            return st;
        }
        return LegacyItems.stack("player_head");
    }

    private static final int[] SLICE = {0xFFFFC857, 0xFF39D0FF, 0xFF8B6CFF, 0xFF7FE3A8, 0xFFFF6B9A, 0xFFFF9F43, 0xFF5EEAD4, 0xFFB98CFF,
            0xFFF472B6, 0xFF60A5FA, 0xFFA3E635, 0xFFFACC15};

    static int networth(MenuUi ui, int x, int y, int w, Profile.Data d) {
        int y0 = y, gap = MenuUi.GAP;
        Map<String, Double> parts = new java.util.LinkedHashMap<>(d.worth);
        List<Map.Entry<String, Double>> list = new ArrayList<>(parts.entrySet());
        list.removeIf(e -> e.getValue() <= 0);
        list.sort(Map.Entry.<String, Double>comparingByValue().reversed());
        double total = list.stream().mapToDouble(Map.Entry::getValue).sum();

        int rowH = 13, h = MenuUi.TOP + 34 + 14 + list.size() * rowH + 2;
        int top = ui.card(x, y, w, h, "Networth", "gold_ingot", null, NETWORTH);
        int ix = x + 6, iw = w - 12, ry = y + top;
        ui.textScaled(Prices.coins(d.networth()), ix, ry + 1, 2f, Ui.GOLD, true);
        String src = d.fromGame ? "what the game has shown so far (Bazaar + lowest BIN + upgrades)" : "counted by the mod (Bazaar + lowest BIN + upgrades)";
        ui.text(ui.ellipsize(src, iw), ix, ry + 20, Ui.DIM);
        ry += 33;
        // stacked bar across the card, then a row per source
        ui.rect(ix, ry, iw, 8, 0xC0050911);
        int cx = ix;
        for (int i = 0; i < list.size(); i++) {
            int sw = (int) Math.round(iw * list.get(i).getValue() / Math.max(1, total));
            if (sw <= 0) continue;
            ui.rect(cx, ry, Math.min(sw, ix + iw - cx), 8, MenuUi.tone(SLICE[i % SLICE.length]));
            ui.rect(cx, ry, Math.min(sw, ix + iw - cx), 1, 0x40FFFFFF);
            cx += sw;
        }
        ry += 13;
        for (int i = 0; i < list.size(); i++) {
            Map.Entry<String, Double> e = list.get(i);
            int c = MenuUi.tone(SLICE[i % SLICE.length]);
            double share = e.getValue() / Math.max(1, total);
            ui.rect(ix, ry + 1, 6, 6, c);
            ui.text(ui.ellipsize(e.getKey(), 130), ix + 10, ry, Ui.TEXT);
            ui.meter(ix + 150, ry + 2, iw - 150 - 130, 3, share, c);
            ui.textRight(String.format(Locale.ROOT, "%.1f%%", share * 100), ix + iw - 62, ry, Ui.MUTED);
            ui.textRight(Prices.coins(e.getValue()), ix + iw, ry, Ui.GOLD);
            ry += rowH;
        }
        y += h + gap;

        // most valuable items + pets
        List<Object[]> top15 = new ArrayList<>();
        for (Map.Entry<String, List<Profile.Item>> inv : d.inventories.entrySet())
            for (Profile.Item it : inv.getValue()) if (it != null && it.value() > 0) top15.add(new Object[]{it, inv.getKey()});
        for (Profile.Pet p : d.pets) if (p.value() > 0) top15.add(new Object[]{p, "Pets"});
        top15.sort((a, b) -> Double.compare(valueOf(b[0]), valueOf(a[0])));
        if (top15.isEmpty() && d.fromGame) return y - y0; // dont show a real looking card with nothing in it
        List<Object[]> shown = top15.stream().limit(15).toList();
        int itemH = 18, ih = MenuUi.TOP + Math.max(1, shown.size()) * itemH + 2;
        int itop = ui.card(x, y, w, ih, "Most valuable items", "diamond", shown.size() + " of " + top15.size(), NETWORTH);
        int iy = y + itop;
        int n = 0;
        for (Object[] o : shown) {
            if (n % 2 == 0) ui.rect(x + 2, iy - 1, w - 4, itemH - 1, 0x0CFFFFFF);
            String name;
            if (o[0] instanceof Profile.Item it) {
                ui.icon(it.stack(), x + 5, iy + 1, 0.875f);
                name = GameState.strip(it.name());
            } else {
                Profile.Pet p = (Profile.Pet) o[0];
                ui.icon(io.github.zmuzy.sbc.core.ItemIcons.pet(p.type(), p.tier()), x + 5, iy + 1, 0.875f);
                name = "[Lvl " + p.level() + "] " + p.name();
            }
            ui.text(ui.ellipsize(name, w / 2), x + 22, iy + 4, Ui.TEXT);
            ui.text((String) o[1], x + w / 2 + 28, iy + 4, Ui.DIM);
            double value = valueOf(o[0]);
            ui.textRight(MenuUi.pct(value / Math.max(1, d.networth())), x + w - 62, iy + 4, Ui.MUTED);
            ui.textRight(Prices.coins(value), x + w - 6, iy + 4, Ui.GOLD);
            iy += itemH;
            n++;
        }
        y += ih + gap;
        return y - y0;
    }

    private static double valueOf(Object o) {
        return o instanceof Profile.Item it ? it.value() : ((Profile.Pet) o).value();
    }

    // one group of rows in a card column, returns height
    static int statCard(MenuUi ui, int x, int y, int w, String title, String icon, int accent, List<String[]> rows) {
        int h = MenuUi.TOP + rows.size() * 11 + 2;
        int top = ui.card(x, y, w, h, title, icon, null, accent);
        int ry = y + top - 1;
        for (String[] r : rows) {
            ui.row(r[0], r[1], x + 6, ry, w - 12, r.length > 2 ? Integer.parseInt(r[2]) : Ui.TEXT);
            ry += 11;
        }
        return h;
    }

    static String[] stat(String label, String value, int color) {
        return new String[]{label, value, String.valueOf(color)};
    }

    private static int moreStats(MenuUi ui, int x, int y, int w, Profile.Data d) {
        int gap = MenuUi.GAP, col = (w - gap) / 2;
        int gold = Ui.GOLD, aqua = Ui.CYAN;
        List<String[]> general = new ArrayList<>();
        if (d.firstJoin > 0) general.add(stat("Profile age", SbTime.dur(System.currentTimeMillis() - d.firstJoin), Ui.TEXT));
        general.add(stat("Skill average", String.format(Locale.ROOT, "%.2f", d.skillAverage), Ui.TEXT));
        general.add(stat("Magical Power", MenuUi.num(d.magicalPower), MenuUi.tone(MenuUi.PURPLE)));
        general.add(stat("Fairy souls", String.valueOf(d.fairySouls), Ui.PINK));
        general.add(stat("Unique minions crafted", String.valueOf(d.uniqueMinions), Ui.TEXT));

        List<String[]> combat = new ArrayList<>();
        combat.add(stat("Kills", MenuUi.num(d.kills), Ui.TEXT));
        combat.add(stat("Deaths", MenuUi.num(d.deaths), Ui.TEXT));
        if (d.deaths > 0) combat.add(stat("Kills per death", String.format(Locale.ROOT, "%.1f", (double) d.kills / d.deaths), Ui.GREEN));
        if (d.highestCrit > 0) combat.add(stat("Highest crit", Prices.coins(d.highestCrit), Ui.RED));
        combat.add(stat("Dojo points", MenuUi.num(d.dojoPoints), Ui.TEXT));
        combat.add(stat("Kuudra completions", MenuUi.num(d.kuudraKills), Ui.ORANGE));

        List<String[]> mining = new ArrayList<>();
        mining.add(stat("HOTM", d.hotm == null ? "-" : "Tier " + d.hotm.level() + "  (" + Prices.coins(d.hotm.xp()) + " XP)", aqua));
        mining.add(stat("HOTF", d.hotfXp <= 0 ? "-" : "Tier " + d.hotf.level() + "  (" + Prices.coins(d.hotfXp) + " XP)", Ui.GREEN));
        mining.add(stat("Mithril powder", Prices.coins(d.mithrilPowder), MenuUi.tone(0xFF5FEA8C)));
        mining.add(stat("Gemstone powder", Prices.coins(d.gemstonePowder), MenuUi.tone(0xFFEC7DD8)));
        mining.add(stat("Glacite powder", Prices.coins(d.glacitePowder), MenuUi.tone(0xFF9BE7FF)));
        mining.add(stat("Crystals found", d.crystalsFound + " types", Ui.TEXT));

        List<String[]> world = new ArrayList<>();
        world.add(stat("Faction", d.faction.isEmpty() ? "none" : d.faction, Ui.ORANGE));
        world.add(stat("Mage reputation", MenuUi.num(d.magesRep), MenuUi.tone(0xFF7FB3FF)));
        world.add(stat("Barbarian reputation", MenuUi.num(d.barbRep), MenuUi.tone(0xFFFF6B4A)));
        world.add(stat("Rift motes", Prices.coins(d.motes), Ui.PINK));
        world.add(stat("Enigma souls", String.valueOf(d.enigmaSouls), MenuUi.tone(MenuUi.PURPLE)));
        int fish = d.trophyFish.values().stream().mapToInt(Integer::intValue).sum();
        if (fish > 0) world.add(stat("Trophy fish caught", Prices.coins(fish) + " (" + d.trophyFish.size() + " kinds)", MenuUi.tone(0xFF4FC3F7)));

        List<String[]> economy = new ArrayList<>();
        economy.add(stat("Copper", Prices.coins(d.copper), MenuUi.tone(0xFFE0904A)));
        if (!d.medals.isEmpty()) economy.add(stat("Jacob medals", d.medals.entrySet().stream().map(e -> e.getValue() + " " + e.getKey()).reduce((a, b) -> a + ", " + b).orElse(""), gold));
        if (d.auctionsEarned + d.auctionsSpent > 0) {
            economy.add(stat("Auctions earned", Prices.coins(d.auctionsEarned), gold));
            economy.add(stat("Auctions spent", Prices.coins(d.auctionsSpent), gold));
            long net = d.auctionsEarned - d.auctionsSpent;
            economy.add(stat("Auctions net", (net >= 0 ? "+" : "") + Prices.coins(net), net >= 0 ? Ui.GREEN : Ui.RED));
        }

        List<String[]> essence = new ArrayList<>();
        for (Map.Entry<String, Long> e : d.essence.entrySet()) essence.add(stat(e.getKey() + " essence", Prices.coins(e.getValue()), MenuUi.tone(0xFFB388FF)));

        int y1 = y, y2 = y;
        y1 += statCard(ui, x, y1, col, "Profile", "player_head", PROFILE, general) + gap;
        y2 += statCard(ui, x + col + gap, y2, w - col - gap, "Combat", "iron_sword", 0xFFFF6B6B, combat) + gap;
        y1 += statCard(ui, x, y1, col, "Mining and forest", "iron_pickaxe", 0xFF4FD8C4, mining) + gap;
        y2 += statCard(ui, x + col + gap, y2, w - col - gap, "Crimson Isle, Rift and fishing", "blaze_powder", 0xFFFF6B4A, world) + gap;
        y1 += statCard(ui, x, y1, col, "Economy", "gold_ingot", NETWORTH, economy) + gap;
        if (!essence.isEmpty()) y2 += statCard(ui, x + col + gap, y2, w - col - gap, "Essence", "nether_star", 0xFFB388FF, essence) + gap;
        return Math.max(y1, y2) - y;
    }

    private static int observed(MenuUi ui, int x, int y, int w) {
        int y0 = y, gap = MenuUi.GAP;
        int top = ui.card(x, y, w, 40, "Your profile", "player_head", null, PROFILE);
        ui.text("It fills in from what the game shows, once you are in SkyBlock.", x + 6, y + top, Ui.TEXT);
        y += 40 + gap;
        int rows = (Store.I.observedSkills.size() + 1) / 2;
        int h = MenuUi.TOP + Math.max(1, rows) * 28 + 2;
        int stop = ui.card(x, y, w, h, "What the mod sees right now", "spyglass", null, 0xFF9CE37D);
        int col = (w - 8 - 3) / 2, i = 0;
        for (Map.Entry<String, double[]> e : Store.I.observedSkills.entrySet()) {
            int cx = x + 4 + (i % 2) * (col + 3), cy = y + stop + (i / 2) * 28;
            String sk = e.getKey();
            double p = e.getValue()[1] / 100;
            ui.rect(cx, cy, col, 26, 0x12FFFFFF);
            ui.rect(cx, cy, 2, 26, MenuUi.tone(skillColor(sk)));
            ui.icon(skillIcon(sk), cx + 5, cy + 5, 0.75f);
            ui.text(sk + " " + (int) e.getValue()[0], cx + 20, cy + 4, Ui.TEXT);
            ui.textRight(MenuUi.pct(p), cx + col - 4, cy + 4, Ui.CYAN);
            ui.meter(cx + 20, cy + 16, col - 24, 3, p, skillColor(sk));
            i++;
        }
        if (i == 0) ui.text("Enable the Skills widget in your tab list (/tab) so the mod can read skill levels.", x + 6, y + stop + 8, Ui.DIM);
        y += h + gap;
        return y - y0;
    }

    private static int overview(MenuUi ui, int x, int y, int w, Profile.Data d) {
        int y0 = y, gap = MenuUi.GAP;
        int col = (w - 2 * gap) / 3, h = 66;
        int x2 = x + col + gap, x3 = x2 + col + gap, c3 = w - 2 * (col + gap);

        int t = ui.card(x, y, col, h, "Heart of the Mountain", "iron_pickaxe", null, 0xFF4FD8C4);
        heroLevel(ui, x, y + t, col, d.hotm, "Tier ", 0xFF4FD8C4);
        ui.row("Mithril", Prices.coins(d.mithrilPowder), x + 6, y + t + 17, col - 12, MenuUi.tone(0xFF5FEA8C));
        ui.row("Gemstone", Prices.coins(d.gemstonePowder), x + 6, y + t + 27, col - 12, MenuUi.tone(0xFFEC7DD8));
        ui.row("Glacite", Prices.coins(d.glacitePowder), x + 6, y + t + 37, col - 12, MenuUi.tone(0xFF9BE7FF));

        t = ui.card(x2, y, col, h, "Heart of the Forest", "oak_sapling", null, 0xFF81C784);
        if (d.hotfXp <= 0) ui.textScaled("Not started", x2 + 6, y + t, 1.25f, Ui.DIM, true);
        else heroLevel(ui, x2, y + t, col, d.hotf, "Tier ", 0xFF81C784);
        ui.row("HOTF XP", Prices.coins(d.hotfXp), x2 + 6, y + t + 17, col - 12, Ui.TEXT);
        if (d.hotfXp > 0 && d.hotf.toNext() > 0) ui.row("To next tier", Prices.coins(d.hotf.toNext()), x2 + 6, y + t + 27, col - 12, Ui.MUTED);

        t = ui.card(x3, y, c3, h, "Crimson Isle", "blaze_powder", null, 0xFFFF6B4A);
        ui.textScaled(d.faction.isEmpty() ? "No faction" : d.faction, x3 + 6, y + t, 1.25f, Ui.ORANGE, true);
        ui.row("Mage rep", MenuUi.num(d.magesRep), x3 + 6, y + t + 17, c3 - 12, MenuUi.tone(0xFF7FB3FF));
        ui.row("Barbarian rep", MenuUi.num(d.barbRep), x3 + 6, y + t + 27, c3 - 12, MenuUi.tone(0xFFFF6B4A));
        y += h + gap;

        int mp = d.magicalPower;
        t = ui.card(x, y, col, h, "Magical Power", "enchanted_book", d.selectedPower.isEmpty() ? null : d.selectedPower, MenuUi.PURPLE);
        ui.textScaled(MenuUi.num(mp), x + 6, y + t, 1.5f, MenuUi.tone(MenuUi.PURPLE), true);
        ui.text("Highest MP on this profile", x + 6, y + t + 17, Ui.DIM);

        t = ui.card(x2, y, col, h, "Minions", "hopper", null, 0xFFFFB454);
        ui.textScaled(d.uniqueMinions + " crafted", x2 + 6, y + t, 1.25f, Ui.TEXT, true);
        ui.text("Unique minion tiers", x2 + 6, y + t + 17, Ui.DIM);
        ui.row("Tracked by the mod", String.valueOf(Store.minions().size()), x2 + 6, y + t + 29, col - 12, Ui.MUTED);

        double petWorth = d.pets.stream().mapToDouble(Profile.Pet::value).sum();
        t = ui.card(x3, y, c3, h, "Pets", "bone", null, 0xFFFFB454);
        ui.textScaled(d.pets.size() + " pets", x3 + 6, y + t, 1.25f, Ui.TEXT, true);
        Profile.Pet active = d.pets.stream().filter(Profile.Pet::active).findFirst().orElse(d.pets.isEmpty() ? null : d.pets.get(0));
        if (active != null) ui.row("Active", "[" + active.level() + "] " + active.name(), x3 + 6, y + t + 17, c3 - 12, Ui.MUTED, MenuUi.tone(PagesMore.rarity(active.tier())));
        ui.row("Worth", Prices.coins(petWorth), x3 + 6, y + t + 29, c3 - 12, Ui.GOLD);
        y += h + gap;

        t = ui.card(x, y, col, h, "Combat", "iron_sword", null, 0xFFFF6B6B);
        ui.textScaled(Prices.coins(d.kills) + " kills", x + 6, y + t, 1.25f, Ui.TEXT, true);
        ui.row("Deaths", Prices.coins(d.deaths), x + 6, y + t + 17, col - 12, Ui.RED);
        if (d.deaths > 0) ui.row("Kills per death", String.format(Locale.ROOT, "%.1f", (double) d.kills / d.deaths), x + 6, y + t + 27, col - 12, Ui.GREEN);
        if (d.highestCrit > 0) ui.row("Highest crit", Prices.coins(d.highestCrit), x + 6, y + t + 37, col - 12, Ui.TEXT);

        t = ui.card(x2, y, col, h, "Slayers", "rotten_flesh", null, 0xFFFF5252);
        ui.textScaled(d.slayers.values().stream().map(l -> String.valueOf(l.level())).reduce((a, b) -> a + "/" + b).orElse("-"), x2 + 6, y + t, 1.25f, Ui.TEXT, true);
        ui.text(ui.ellipsize("Rev/Tara/Sven/Void/Inferno/Rift", col - 12), x2 + 6, y + t + 17, Ui.DIM);
        ui.tip(x2, y + t + 16, col, 10, "Revenant/Tara/Sven/Void/Inferno/Rift");
        double slayerXp = d.slayers.values().stream().mapToDouble(Profile.Level::xp).sum();
        ui.row("Total slayer XP", Prices.coins(slayerXp), x2 + 6, y + t + 29, col - 12, Ui.TEXT);

        t = ui.card(x3, y, c3, h, "Networth", "gold_ingot", null, NETWORTH);
        ui.textScaled(Prices.coins(d.networth()), x3 + 6, y + t, 1.5f, Ui.GOLD, true);
        ui.text("Items, pets, sacks, essence, coins", x3 + 6, y + t + 17, Ui.DIM);
        double coins = d.purse + d.bank;
        if (d.networth() > 0) ui.row("In coins", Prices.coins(coins) + " · " + MenuUi.pct(coins / d.networth()), x3 + 6, y + t + 29, c3 - 12, Ui.GOLD);
        y += h + gap;

        y += levelGrid(ui, x, y, w, d.skills, true);
        return y - y0;
    }

    // big "Tier 8 · 39%" + meter under it
    static void heroLevel(MenuUi ui, int x, int y, int w, Profile.Level l, String prefix, int accent) {
        if (l == null) {
            ui.textScaled("-", x + 6, y, 1.25f, Ui.DIM, true);
            return;
        }
        if (l.progress() < 0) {
            ui.textScaled(prefix + l.level(), x + 6, y, 1.25f, Ui.TEXT, true);
            return;
        }
        ui.textScaled(prefix + l.level(), x + 6, y, 1.25f, Ui.TEXT, true);
        ui.textRight(l.toNext() <= 0 ? "MAX" : MenuUi.pct(l.progress()), x + w - 6, y + 1, l.toNext() <= 0 ? Ui.GOLD : Ui.CYAN);
        ui.meter(x + 6, y + 12, w - 12, 2, l.toNext() <= 0 ? 1 : l.progress(), accent);
    }

    // a maxed level: a warm gold card, a gold edge, and a soft light that runs across it every few seconds
    static void maxedBackground(MenuUi ui, int x, int y, int w, int h) {
        ui.hoverRound(x, y, w, h, 6, 0x2EFFB833, 0x40FFB833);
        ui.rect(x + 2, y, w - 4, 1, 0x66FFD27A);
        ui.rect(x + 2, y + h - 1, w - 4, 1, 0x40FFD27A);
        long t = System.currentTimeMillis() % 3200;
        if (t > 1400) return;
        int band = 12, at = x - band + (int) ((w + band) * t / 1400);
        int from = Math.max(x + 2, at), to = Math.min(x + w - 2, at + band);
        if (to > from) ui.rect(from, y + 1, to - from, h - 2, 0x1CFFF1C4);
    }

    // name, how far into the level (numbers + %), whats left
    static void levelTile(MenuUi ui, int x, int y, int w, String name, Profile.Level l, int cap, String icon, int accent) {
        boolean unread = l.progress() < 0; // from the game: we got the level but not how far into it
        boolean maxed = l.toNext() <= 0 && !unread;
        if (maxed) maxedBackground(ui, x, y, w, 32);
        else ui.hoverRound(x, y, w, 32, 6, 0x12FFFFFF, 0x22FFFFFF);
        ui.rect(x, y, 2, 32, maxed ? 0xFFFFC14D : MenuUi.tone(accent));
        ui.icon(icon, x + 5, y + 4, 1f);
        ui.text(name, x + 24, y + 3, Ui.TEXT);
        String levelText = l.level() + (cap > 0 ? "/" + cap : "");
        ui.textRight(levelText, x + w - 4, y + 3, maxed ? Ui.GOLD : Ui.CYAN);
        // own table, percent only: runecrafting + social from the tab list
        boolean percentOnly = l.xp() == 0 && l.toNext() == 1;
        String nums = unread ? "" : maxed ? "§6Max level" : percentOnly ? "§b" + MenuUi.pct(l.progress()) : "§e" + MenuUi.num(have(l)) + "§7/§e" + MenuUi.num(need(l)) + "  §b" + MenuUi.pct(l.progress());
        ui.text(nums, x + 24, y + 13, Ui.MUTED);
        if (!unread && !percentOnly) ui.textRight(maxed ? Prices.coins(l.xp()) + " XP" : Prices.coins(l.toNext()) + " to next", x + w - 4, y + 13, Ui.DIM);
        ui.meter(x + 24, y + 25, w - 28, 3, maxed ? 1 : Math.max(0, l.progress()), accent);
        if (unread) ui.tip(x, y, w, 32, name + " " + l.level(), "§7How far into this level has not been read yet.");
        else ui.tip(x, y, w, 32, name + " " + l.level(), "§7Total XP: " + Prices.coins(l.xp()), l.toNext() > 0 ? "§7To next level: " + Prices.coins(l.toNext()) : "§6Max level");
    }

    // the game can show a level above the api cap, so count to the games cap
    private static int shownCap(String skill) {
        Profile.Data d = Profile.viewed != null ? Profile.viewed : Profile.current;
        return d != null && d.fromGame ? io.github.zmuzy.sbc.core.game.GameProfile.skillCap(skill) : skillCap(skill);
    }

    static int levelGrid(MenuUi ui, int x, int y, int w, Map<String, Profile.Level> map, boolean skills) {
        int rows = (map.size() + 1) / 2, h = MenuUi.TOP + Math.max(1, rows) * 35 + 1;
        int top = ui.card(x, y, w, h, skills ? "Skills" : "Slayers", skills ? "experience_bottle" : "iron_sword", null, skills ? 0xFF9CE37D : 0xFFFF5252);
        int col = (w - 8 - 3) / 2, i = 0;
        for (Map.Entry<String, Profile.Level> e : map.entrySet()) {
            int cx = x + 4 + (i % 2) * (col + 3), cy = y + top + (i / 2) * 35;
            String n = e.getKey();
            if (skills) levelTile(ui, cx, cy, col, n, e.getValue(), shownCap(n), skillIcon(n), skillColor(n));
            else levelTile(ui, cx, cy, col, n, e.getValue(), n.equals("Riftstalker") ? 5 : 9, slayerIcon(n), slayerColor(n));
            i++;
        }
        return h + MenuUi.GAP;
    }

    static int skills(MenuUi ui, int x, int y, int w, Profile.Data d) {
        int y0 = y, gap = MenuUi.GAP;
        long maxed = d.skills.values().stream().filter(l -> l.toNext() <= 0).count();
        double total = d.skills.values().stream().mapToDouble(Profile.Level::xp).sum();
        var best = d.skills.entrySet().stream().max(Comparator.comparingDouble(e -> e.getValue().level() + Math.max(0, e.getValue().progress()))).orElse(null);
        int tg = 3, tw = (w - 3 * tg) / 4;
        ui.tile(x, y, tw, 40, "Skill average", d.skillAverage < 0 ? "?" : String.format(Locale.ROOT, "%.2f", d.skillAverage), null, Ui.TEXT, -1, 0);
        ui.tile(x + tw + tg, y, tw, 40, "Maxed", maxed + "/" + d.skills.size(), MenuUi.pct(d.skills.isEmpty() ? 0 : (double) maxed / d.skills.size()), Ui.GOLD, d.skills.isEmpty() ? -1 : (double) maxed / d.skills.size(), 0xFFFFD166);
        ui.tile(x + 2 * (tw + tg), y, tw, 40, "Total XP", Prices.coins(total), null, Ui.CYAN, -1, 0);
        ui.tile(x + 3 * (tw + tg), y, w - 3 * (tw + tg), 40, "Highest", best == null ? "-" : best.getKey() + " " + best.getValue().level(), null, Ui.TEXT, -1, 0);
        y += 40 + gap;
        y += levelGrid(ui, x, y, w, d.skills, true);
        return y - y0;
    }

    static int slayers(MenuUi ui, int x, int y, int w, Profile.Data d) {
        int y0 = y, gap = MenuUi.GAP;
        double total = d.slayers.values().stream().mapToDouble(Profile.Level::xp).sum();
        long maxed = d.slayers.values().stream().filter(l -> l.toNext() <= 0).count();
        var best = d.slayers.entrySet().stream().max(Comparator.comparingDouble(e -> e.getValue().xp())).orElse(null);
        int tg = 3, tw = (w - 2 * tg) / 3;
        ui.tile(x, y, tw, 40, "Total slayer XP", Prices.coins(total), null, Ui.TEXT, -1, 0);
        ui.tile(x + tw + tg, y, tw, 40, "Maxed", maxed + "/" + d.slayers.size(), MenuUi.pct(d.slayers.isEmpty() ? 0 : (double) maxed / d.slayers.size()), Ui.GOLD, d.slayers.isEmpty() ? -1 : (double) maxed / d.slayers.size(), 0xFFFFD166);
        ui.tile(x + 2 * (tw + tg), y, w - 2 * (tw + tg), 40, "Most XP", best == null ? "-" : best.getKey(), best == null ? null : Prices.coins(best.getValue().xp()) + " XP", Ui.RED, -1, 0);
        y += 40 + gap;
        y += levelGrid(ui, x, y, w, d.slayers, false);
        y += slayerKills(ui, x, y, w, d);
        return y - y0;
    }

    // from skyblocker SlayerWidget: kills per tier, whole row not just the top one
    private static int slayerKills(MenuUi ui, int x, int y, int w, Profile.Data d) {
        if (d.slayerKills.isEmpty()) return 0;
        long all = d.slayerKills.values().stream().mapToLong(k -> java.util.Arrays.stream(k).sum()).sum();
        int rowH = 11, h = MenuUi.TOP + 11 + d.slayerKills.size() * rowH + 2;
        int top = ui.card(x, y, w, h, "Boss kills", "rotten_flesh", MenuUi.num(all) + " kills", 0xFFFF5252);
        int left = x + 6, right = x + w - 6, col = Math.max(40, (w - 110) / 6), ry = y + top - 1;
        for (int t = 0; t < 5; t++) ui.textRight("Tier " + (t + 1), right - (5 - t) * col, ry, Ui.DIM);
        ui.textRight("All", right, ry, Ui.DIM);
        ry += 11;
        for (Map.Entry<String, long[]> e : d.slayerKills.entrySet()) {
            ui.text(e.getKey(), left, ry, MenuUi.tone(slayerColor(e.getKey())));
            long sum = 0;
            for (int t = 0; t < 5; t++) {
                sum += e.getValue()[t];
                ui.textRight(MenuUi.num(e.getValue()[t]), right - (5 - t) * col, ry, e.getValue()[t] > 0 ? Ui.TEXT : Ui.DIM);
            }
            ui.textRight(MenuUi.num(sum), right, ry, Ui.GOLD);
            ry += rowH;
        }
        return h + MenuUi.GAP;
    }

    // from skyblocker CroesusWidget + DailyRunsWidget
    private static int dungeonChests(MenuUi ui, int x, int y, int w, Profile.Data d) {
        if (d.croesusRuns < 0 && d.dailyRuns < 0) return 0;
        int tg = 3, tw = (w - tg) / 2;
        double croesus = Math.min(1, d.croesusRuns / 60.0), daily = Math.max(0, d.dailyRuns) / 5.0;
        ui.tile(x, y, tw, 40, "Croesus", d.croesusRuns + "/60 runs", d.croesusUnclaimed + " unclaimed", d.croesusUnclaimed > 0 ? Ui.GOLD : Ui.TEXT, croesus, 0xFFFFD166);
        long resets = java.time.Duration.between(java.time.Instant.now(), java.time.LocalDate.now(java.time.ZoneOffset.UTC).plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant()).toMillis();
        ui.tile(x + tw + tg, y, w - tw - tg, 40, "Daily runs", d.dailyRuns + "/5", "Resets in " + SbTime.dur(resets), d.dailyRuns >= 5 ? Ui.GREEN : Ui.TEXT, daily, 0xFF5CD8FF);
        return 40 + MenuUi.GAP;
    }

    private static int dungeons(MenuUi ui, int x, int y, int w, Profile.Data d) {
        int y0 = y, gap = MenuUi.GAP;
        if (d.catacombs != null) {
            Profile.Level c = d.catacombs;
            boolean maxed = c.toNext() <= 0;
            int h = 62;
            int top = ui.card(x, y, w, h, "The Catacombs", "skeleton_skull", maxed ? "MAX" : Prices.coins(c.toNext()) + " to " + (c.level() + 1), DUNGEON);
            ui.textScaled("Level " + c.level(), x + 6, y + top, 2f, Ui.TEXT, true);
            String nums = maxed ? "§6Max level" : "§e" + MenuUi.num(have(c)) + "§7/§e" + MenuUi.num(need(c)) + " XP  §b" + MenuUi.pct(c.progress());
            ui.textRight(nums, x + w - 6, y + top + 1, Ui.MUTED);
            ui.textRight("Total " + Prices.coins(c.xp()) + " XP", x + w - 6, y + top + 11, Ui.DIM);
            ui.meter(x + 6, y + top + 21, w - 12, 4, maxed ? 1 : c.progress(), DUNGEON);
            double avg = d.classes.isEmpty() ? 0 : d.classes.values().stream().mapToDouble(l -> l.level() + l.progress()).average().orElse(0);
            ui.text("Class average " + String.format(Locale.ROOT, "%.1f", avg), x + 6, y + top + 29, Ui.MUTED);
            y += h + gap;
        }
        int normalRuns = d.floorCompletions.entrySet().stream().filter(e -> !e.getKey().startsWith("M")).mapToInt(Map.Entry::getValue).sum();
        int masterRuns = d.floorCompletions.entrySet().stream().filter(e -> e.getKey().startsWith("M")).mapToInt(Map.Entry::getValue).sum();
        int tg = 3, tw = (w - 3 * tg) / 4;
        ui.tile(x, y, tw, 40, "Secrets", MenuUi.num(d.secrets), null, Ui.TEXT, -1, 0);
        ui.tile(x + tw + tg, y, tw, 40, "Secrets per run", normalRuns + masterRuns > 0 ? String.format(Locale.ROOT, "%.2f", (double) d.secrets / (normalRuns + masterRuns)) : "-", null, Ui.CYAN, -1, 0);
        ui.tile(x + 2 * (tw + tg), y, tw, 40, "Normal runs", MenuUi.num(normalRuns), null, MenuUi.tone(0xFF5CD8FF), -1, 0);
        ui.tile(x + 3 * (tw + tg), y, w - 3 * (tw + tg), 40, "Master Mode runs", MenuUi.num(masterRuns), null, MenuUi.tone(0xFFFF5252), -1, 0);
        y += 40 + gap;
        y += dungeonChests(ui, x, y, w, d);
        int rows = (d.classes.size() + 1) / 2, ch = MenuUi.TOP + Math.max(1, rows) * 35 + 1;
        int ctop = ui.card(x, y, w, ch, "Classes", "bow", d.classes.size() + " classes", 0xFF7FB3FF);
        int col = (w - 8 - 3) / 2, i = 0;
        for (Map.Entry<String, Profile.Level> e : d.classes.entrySet()) {
            levelTile(ui, x + 4 + (i % 2) * (col + 3), y + ctop + (i / 2) * 35, col, e.getKey(), e.getValue(), 50, classIcon(e.getKey()), classColor(e.getKey()));
            i++;
        }
        y += ch + gap;

        List<Map.Entry<String, Integer>> floors = new ArrayList<>(d.floorCompletions.entrySet());
        List<Map.Entry<String, Integer>> normal = floors.stream().filter(e -> !e.getKey().startsWith("M")).toList();
        List<Map.Entry<String, Integer>> master = floors.stream().filter(e -> e.getKey().startsWith("M")).toList();
        int total = floors.stream().mapToInt(Map.Entry::getValue).sum();
        int perRow = Math.max(4, (w - 8) / 72), tileW = (w - 8 - (perRow - 1) * 3) / perRow;
        int fh = MenuUi.TOP + floorRows(normal.size(), perRow) * 29 + (master.isEmpty() ? 0 : 12 + floorRows(master.size(), perRow) * 29) + 2;
        int ftop = ui.card(x, y, w, fh, "Floor completions", "chest", MenuUi.num(total) + " runs", DUNGEON);
        int fy = y + ftop;
        fy = floorGrid(ui, x + 4, fy, normal, perRow, tileW, 0xFF5CD8FF, total, d);
        if (!master.isEmpty()) {
            ui.section("Master Mode", x + 6, fy, w - 12, 0xFFFF5252);
            fy += 12;
            floorGrid(ui, x + 4, fy, master, perRow, tileW, 0xFFFF5252, total, d);
        }
        y += fh + gap;
        return y - y0;
    }

    private static int floorRows(int n, int perRow) {
        return Math.max(n == 0 ? 0 : 1, (n + perRow - 1) / perRow);
    }

    private static String runTime(long ms) {
        long seconds = ms / 1000;
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    private static int floorGrid(MenuUi ui, int x, int y, List<Map.Entry<String, Integer>> floors, int perRow, int tileW, int accent, int total, Profile.Data d) {
        int i = 0;
        for (Map.Entry<String, Integer> e : floors) {
            String label = e.getKey().replace("F0", "Entrance");
            double share = total <= 0 ? 0 : (double) e.getValue() / total;
            int fx = x + (i % perRow) * (tileW + 3), fy = y + (i / perRow) * 29;
            ui.tile(fx, fy, tileW, 26, label, MenuUi.num(e.getValue()) + " · " + MenuUi.pct(share), null, MenuUi.tone(accent), share, accent);
            Profile.Best best = d.bests.get(e.getKey());
            if (best != null) {
                if (best.sPlus() > 0) ui.textRight(runTime(best.sPlus()), fx + tileW - 4, fy + 3, Ui.GOLD);
                List<String> tip = new ArrayList<>();
                tip.add(label + " personal bests");
                if (best.sPlus() > 0) tip.add("§6S+ run: " + runTime(best.sPlus()));
                if (best.s() > 0) tip.add("§6S run: " + runTime(best.s()));
                if (best.any() > 0) tip.add("§7Completion: " + runTime(best.any()));
                if (best.score() > 0) tip.add("§7Best score: " + best.score());
                ui.tip(fx, fy, tileW, 26, tip.toArray(new String[0]));
            }
            i++;
        }
        return y + floorRows(floors.size(), perRow) * 29;
    }

    static int pets(MenuUi ui, int x, int y, int w, Profile.Data d) {
        int y0 = y, gap = MenuUi.GAP;
        double sum = d.pets.stream().mapToDouble(Profile.Pet::value).sum();
        int rowsCount = Math.min(150, d.pets.size());
        int col = (w - 8 - 2 * 3) / 3, rows = (rowsCount + 2) / 3, h = MenuUi.TOP + 12 + Math.max(1, rows) * 29 + 1;
        int top = ui.card(x, y, w, h, d.pets.size() + " pets", "bone", Prices.coins(sum), 0xFFFFB454);
        ui.button("pet_menu", x + w - 150, y + 1, 70, 12, "Pet menu", Ui.GHOST, () -> net.minecraft.client.Minecraft.getInstance().gui.setScreen(new PetsScreen(net.minecraft.client.Minecraft.getInstance().gui.screen())));
        // how many of each rarity, in rarity colors
        int cx = x + 6;
        for (String tier : new String[]{"MYTHIC", "LEGENDARY", "EPIC", "RARE", "UNCOMMON", "COMMON"}) {
            long n = d.pets.stream().filter(p -> tier.equals(p.tier())).count();
            if (n == 0) continue;
            String chip = n + " " + Profile.cap(tier);
            cx += ui.chip(cx, y + top - 1, chip, rarity(tier)) + 3;
        }
        ui.textRight("lowest BIN, scaled by level", x + w - 6, y + top, Ui.DIM);
        int gy = y + top + 12, i = 0;
        for (Profile.Pet p : d.pets) {
            int px = x + 4 + (i % 3) * (col + 3), py = gy + (i / 3) * 29;
            int rc = rarity(p.tier());
            ui.hoverRound(px, py, col, 27, 5, p.active() ? Ui.alpha(Ui.GREEN, 0.16f) : 0x12FFFFFF, p.active() ? Ui.alpha(Ui.GREEN, 0.26f) : 0x22FFFFFF);
            ui.rect(px, py, 2, 27, rc);
            ui.icon(io.github.zmuzy.sbc.core.ItemIcons.pet(p.type(), p.tier()), px + 4, py + 5, 1f);
            String v = p.value() > 0 ? Prices.coins(p.value()) : "";
            ui.text(ui.ellipsize("[" + p.level() + "] " + p.name(), col - 26), px + 22, py + 3, rc);
            ui.text(Profile.cap(p.tier()), px + 22, py + 14, Ui.DIM);
            if (p.active()) ui.chip(px + col - ui.chipWidth("ACTIVE") - 3, py + 13, "ACTIVE", Ui.GREEN);
            else ui.textRight(v, px + col - 4, py + 14, Ui.GOLD);
            ui.tip(px, py, col, 27, () -> {
                List<String> tip = new ArrayList<>(List.of(p.name(), "§7" + p.tier() + " · Level " + p.level(), "§7XP: " + Prices.coins(p.exp()),
                        p.heldItem().isEmpty() ? "§8No pet item" : "§7Item: " + io.github.zmuzy.sbc.core.ItemRepo.name(p.heldItem()), "§6Value: " + Prices.coins(p.value())));
                List<String> lore = io.github.zmuzy.sbc.core.PetLore.lines(p.type(), p.tier(), p.level());
                if (!lore.isEmpty()) tip.add("");
                tip.addAll(lore);
                return tip.toArray(new String[0]);
            });
            if (++i >= 150) break;
        }
        y += h + gap;
        return y - y0;
    }

    static int rarity(String tier) {
        return switch (tier) {
            case "COMMON" -> 0xFFE8E8E8;
            case "UNCOMMON" -> 0xFF5FEA8C;
            case "RARE" -> 0xFF6F9BFF;
            case "EPIC" -> 0xFFC77DFF;
            case "LEGENDARY" -> 0xFFFFB627;
            case "MYTHIC" -> 0xFFFF7BE5;
            default -> Ui.TEXT;
        };
    }

    private static boolean hit(Profile.Item it, String q) {
        if (it == null) return false;
        if (GameState.strip(it.name()).toLowerCase(Locale.ROOT).contains(q) || it.sbId() != null && it.sbId().toLowerCase(Locale.ROOT).contains(q)) return true;
        for (String line : it.lore()) if (GameState.strip(line).toLowerCase(Locale.ROOT).contains(q)) return true;
        return false;
    }

    static int items(MenuUi ui, int x, int y, int w, Profile.Data d) {
        int gap = MenuUi.GAP, cols = Math.max(1, Math.min(3, (w + gap) / 190)), colW = (w - (cols - 1) * gap) / cols;
        ui.input(itemSearch, x, y, 170);
        String q = itemSearch.value.trim().toLowerCase(Locale.ROOT);
        if (!q.isEmpty()) {
            int found = 0;
            double foundWorth = 0;
            for (List<Profile.Item> list : d.inventories.values())
                for (Profile.Item it : list)
                    if (hit(it, q)) {
                        found++;
                        foundWorth += it.value();
                    }
            ui.text(found + " found · " + Prices.coins(foundWorth), x + 176, y + 5, found > 0 ? Ui.GOLD : Ui.DIM);
        }
        int searchH = 18 + gap;
        y += searchH;
        int perRow = Math.max(1, Math.min(9, (colW - 8) / 20));
        int[] heights = new int[cols];
        boolean any = false;
        for (Map.Entry<String, List<Profile.Item>> inv : d.inventories.entrySet()) {
            List<Profile.Item> items = inv.getValue();
            if (items.stream().allMatch(it -> it == null)) continue;
            any = true;
            if (!q.isEmpty() && items.stream().noneMatch(it -> hit(it, q))) continue;
            int c = 0;
            for (int k = 1; k < cols; k++) if (heights[k] < heights[c]) c = k;
            int rows = (items.size() + perRow - 1) / perRow, h = MenuUi.TOP + rows * 20 + 2;
            int cx0 = x + c * (colW + gap), cy0 = y + heights[c];
            double worth = items.stream().filter(it -> it != null).mapToDouble(Profile.Item::value).sum();
            long used = items.stream().filter(it -> it != null).count();
            int top = ui.card(cx0, cy0, colW, h, inv.getKey(), "chest", used + "/" + items.size() + (worth > 0 ? "  " + Prices.coins(worth) : ""), Looks.colourFor(inv.getKey()));
            int i = 0;
            for (Profile.Item it : items) {
                int cx = cx0 + 4 + (i % perRow) * 20, cy = cy0 + top + (i / perRow) * 20;
                ui.rect(cx, cy, 19, 19, it == null ? (0x20000000) : 0x18FFFFFF);
                if (it != null) {
                    boolean match = q.isEmpty() || hit(it, q);
                    if (!q.isEmpty() && match) ui.rect(cx, cy, 19, 19, Ui.alpha(Ui.GOLD, 0.35f));
                    ui.icon(it.stack(), cx + 2, cy + 2, 0.9375f);
                    if (it.count() > 1) ui.textShadow(String.valueOf(it.count()), cx + 19 - ui.width(String.valueOf(it.count())), cy + 11, Ui.TEXT);
                    if (!match) ui.rect(cx, cy, 19, 19, 0xC00A0E14);
                    ui.tipItem(cx, cy, 19, 19, it::stack, () -> {
                        // shown alone when theres no world for the real tooltip, else after it
                        List<String> tip = new ArrayList<>();
                        if (net.minecraft.client.Minecraft.getInstance().level == null) {
                            tip.add(it.name());
                            tip.addAll(it.lore());
                        }
                        if (it.value() > 0) tip.add("§6Worth: " + Prices.coins(it.value()));
                        return tip.toArray(new String[0]);
                    });
                }
                i++;
            }
            ui.tip(cx0, cy0, colW, MenuUi.TOP - 4, inv.getKey(), "§7" + used + "/" + items.size() + " slots used · " + MenuUi.pct((double) used / items.size()), "§6Worth: " + Prices.coins(worth));
            heights[c] += h + gap;
        }
        if (!any) {
            ui.text("Inventory API is disabled on this profile.", x, y, Ui.DIM);
            return 14 + searchH;
        }
        int max = 0;
        for (int v : heights) max = Math.max(max, v);
        return max + searchH;
    }

    // --- accessories ---

    private static io.github.zmuzy.sbc.plan.AccessoryHelper.Plan accPlan;
    private static Profile.Data accFor;
    private static long accAt;

    private static double accCost(String id) {
        double v = io.github.zmuzy.sbc.core.AuctionPrices.value(id);
        return v > 0 ? v : Prices.sell(id);
    }

    // whats in the bag + what adds MP for the least coins
    static int accessories(MenuUi ui, int x, int y, int w, Profile.Data d) {
        int y0 = y, gap = MenuUi.GAP;
        List<Profile.Item> bag = d.inventories.get("Accessories");
        if (bag == null) {
            ui.text("Inventory API is disabled on this profile.", x, y, Ui.DIM);
            return 14;
        }
        java.util.Set<String> owned = new java.util.HashSet<>();
        for (Profile.Item it : bag) if (it != null && it.sbId() != null) owned.add(it.sbId());
        var repo = io.github.zmuzy.sbc.core.ItemRepo.accessories();
        long now = System.currentTimeMillis();
        if (accPlan == null || accFor != d || now - accAt > 5000) {
            accPlan = io.github.zmuzy.sbc.plan.AccessoryHelper.plan(io.github.zmuzy.sbc.plan.AccessoryHelper.families(repo, owned), PagesMore::accCost, Math.max(0, d.purse));
            accFor = d;
            accAt = now;
        }
        var plan = accPlan;
        int mp = d.magicalPower;
        int tg = 3, tw = (w - 3 * tg) / 4;
        ui.tile(x, y, tw, 40, "Magical Power", d.knows(io.github.zmuzy.sbc.core.game.GameProfile.MAGICAL_POWER) ? MenuUi.num(mp) : "?", null, MenuUi.tone(MenuUi.PURPLE), -1, 0);
        ui.tile(x + tw + tg, y, tw, 40, "Accessories", String.valueOf(owned.size()), null, Ui.TEXT, -1, 0);
        ui.tile(x + 2 * (tw + tg), y, tw, 40, "Purse buys", "+" + plan.affordableMp() + " MP", plan.affordable() + " for " + Prices.coins(plan.affordableCost()), Ui.GREEN, -1, 0);
        ui.tile(x + 3 * (tw + tg), y, w - 3 * (tw + tg), 40, "Everything left", "+" + plan.totalMp() + " MP", Prices.coins(plan.totalCost()), Ui.GOLD, -1, 0);
        y += 40 + gap;
        y += powerAndTuning(ui, x, y, w, d);

        // count per rarity, then the list
        int shownSteps = Math.min(16, plan.steps().size()), rowH = 18, h = MenuUi.TOP + 13 + Math.max(1, shownSteps) * rowH + 2;
        int top = ui.card(x, y, w, h, "Cheapest Magical Power next", "ender_eye", shownSteps + " of " + plan.steps().size(), 0xFFB388FF);
        int cx = x + 6;
        for (String tier : new String[]{"MYTHIC", "LEGENDARY", "EPIC", "RARE", "UNCOMMON", "COMMON", "SPECIAL", "VERY_SPECIAL"}) {
            long n = owned.stream().filter(id -> repo.containsKey(id) && tier.equalsIgnoreCase(repo.get(id)[1])).count();
            if (n == 0) continue;
            cx += ui.chip(cx, y + top - 1, n + " " + Profile.cap(tier), rarity(tier)) + 3;
        }
        int ry = y + top + 12;
        if (plan.steps().isEmpty()) ui.text(repo.isEmpty() ? "The accessory list is still loading." : "Nothing left with a price.", x + 6, ry + 4, Ui.DIM);
        for (int i = 0; i < shownSteps; i++) {
            var step = plan.steps().get(i);
            if (i % 2 == 0) ui.rect(x + 2, ry - 1, w - 4, rowH - 1, 0x0CFFFFFF);
            boolean canPay = i < plan.affordable();
            ui.rect(x + 2, ry - 1, 2, rowH - 1, canPay ? Ui.GREEN : Ui.alpha(Ui.TEXT, 0.15f));
            ui.icon(io.github.zmuzy.sbc.core.ItemInfo.stack(step.tier().id()), x + 6, ry + 1, 0.875f);
            String rarityName = io.github.zmuzy.sbc.plan.AccessoryHelper.RARITIES[step.tier().rarity()];
            ui.text(ui.ellipsize(step.tier().name(), w / 2 - 28), x + 24, ry + 4, rarity(rarityName));
            if (step.family().owned() >= 0) ui.text(ui.ellipsize("has " + step.family().tiers().get(step.family().owned()).name(), w / 4), x + w / 2 + 2, ry + 4, Ui.DIM);
            ui.textRight("+" + step.gain() + " MP", x + w - 124, ry + 4, MenuUi.tone(MenuUi.PURPLE));
            ui.textRight(Prices.coins(step.perMp()) + "/MP", x + w - 62, ry + 4, Ui.MUTED);
            ui.textRight(Prices.coins(step.cost()), x + w - 6, ry + 4, canPay ? Ui.GREEN : Ui.GOLD);
            ry += rowH;
        }
        y += h + gap;
        return y - y0;
    }

    // from skyblocker TuningSlotWidget + the power from AccessoryPowerWidget
    private static int powerAndTuning(MenuUi ui, int x, int y, int w, Profile.Data d) {
        if (d.tuning.isEmpty() && d.selectedPower.isEmpty()) return 0;
        int h = MenuUi.TOP + 12;
        int top = ui.card(x, y, w, h, "Stats Tuning", "nether_star", d.selectedPower.isEmpty() ? null : "Power: " + d.selectedPower, MenuUi.PURPLE);
        int cx = x + 6;
        if (d.tuning.isEmpty()) ui.text("No points given.", cx, y + top, Ui.DIM);
        for (Map.Entry<String, Double> e : d.tuning.entrySet()) {
            String amount = e.getValue() == Math.rint(e.getValue()) ? String.valueOf(e.getValue().longValue()) : String.format(Locale.ROOT, "%.1f", e.getValue());
            cx += ui.chip(cx, y + top - 1, "+" + amount + " " + e.getKey(), tuningColor(e.getKey())) + 3;
        }
        return h + MenuUi.GAP;
    }

    // AccessoryPowerCalculator colors
    private static int tuningColor(String stat) {
        return switch (stat) {
            case "Health", "Strength" -> Ui.RED;
            case "Defense" -> Ui.GREEN;
            case "Crit Chance", "Crit Damage" -> 0xFF7FB3FF;
            case "Attack Speed" -> Ui.GOLD;
            case "Intelligence" -> Ui.CYAN;
            default -> Ui.TEXT;
        };
    }

    private static String roman(int n) {
        String[] tens = {"", "X", "XX", "XXX", "XL", "L"}, ones = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"};
        return n <= 0 || n >= 60 ? String.valueOf(n) : tens[n / 10] + ones[n % 10];
    }

    // misc.json "ACACIA_GENERATOR": 12 = top tier of every minion
    private static Map<String, Integer> minionTiers;

    private static Map<String, Integer> minionTiers() {
        if (minionTiers != null) return minionTiers;
        Map<String, Integer> out = new java.util.TreeMap<>();
        var misc = io.github.zmuzy.sbc.core.data.DataFiles.object(io.github.zmuzy.sbc.core.data.DataFiles.Source.NEU, "misc.json");
        if (misc != null && misc.has("minions")) {
            for (var e : misc.getAsJsonObject("minions").entrySet())
                if (e.getKey().endsWith("_GENERATOR")) out.put(e.getKey().substring(0, e.getKey().length() - "_GENERATOR".length()), e.getValue().getAsInt());
        }
        minionTiers = out;
        return out;
    }

    // from skyblocker CollectionsPage.addMinionItems + MinionWidget: every minion at its top crafted tier, all
    // tiers on hover
    private static int minions(MenuUi ui, int x, int y, int w, Profile.Data d) {
        if (d.craftedMinions.isEmpty()) return 0;
        Map<String, Integer> tiers = minionTiers();
        if (tiers.isEmpty()) return 0;
        int unlocked = 0, maxed = 0;
        for (Map.Entry<String, Integer> e : tiers.entrySet()) {
            java.util.Set<Integer> have = d.craftedMinions.getOrDefault(e.getKey(), java.util.Set.of());
            if (!have.isEmpty()) unlocked++;
            if (have.size() >= e.getValue()) maxed++;
        }
        int size = 19, perRow = Math.max(1, (w - 8) / (size + 1)), rows = (tiers.size() + perRow - 1) / perRow, h = MenuUi.TOP + rows * (size + 1) + 2;
        int top = ui.card(x, y, w, h, "Minions crafted", "hopper", unlocked + "/" + tiers.size() + " unlocked · " + maxed + " maxed", 0xFFFFB454);
        int i = 0;
        for (Map.Entry<String, Integer> e : tiers.entrySet()) {
            String id = e.getKey();
            int max = e.getValue(), cx = x + 4 + (i % perRow) * (size + 1), cy = y + top + (i / perRow) * (size + 1);
            java.util.Set<Integer> have = d.craftedMinions.getOrDefault(id, java.util.Set.of());
            int row = 0;
            while (have.contains(row + 1)) row++;
            boolean full = have.size() >= max;
            ui.rect(cx, cy, size, size, full ? Ui.alpha(Ui.GOLD, 0.28f) : 0x18FFFFFF);
            ui.icon(io.github.zmuzy.sbc.core.ItemIcons.of(id + "_GENERATOR_" + Math.max(row, 1)), cx + 2, cy + 2, 0.9375f);
            if (have.isEmpty()) ui.rect(cx, cy, size, size, 0xA00A0E14);
            else ui.textShadow(String.valueOf(row), cx + size - ui.width(String.valueOf(row)), cy + 11, full ? Ui.GOLD : Ui.TEXT);
            ui.tip(cx, cy, size, size, () -> {
                List<String> tip = new ArrayList<>();
                tip.add((full ? "§a" : have.isEmpty() ? "§c" : "§e") + GameState.pretty(id) + " Minion");
                for (int t = 1; t <= max; t++) tip.add((have.contains(t) ? "§a✔ Tier " : "§c✖ Tier ") + roman(t));
                return tip.toArray(new String[0]);
            });
            i++;
        }
        return h + MenuUi.GAP;
    }

    // co-op = counts the whole profile like the game does, skyblocker CollectionItemWidget shows each share on hover
    private static long collected(Profile.Data d, String id, long own) {
        return d.collectionsTotal.getOrDefault(id, own);
    }

    static int collections(MenuUi ui, int x, int y, int w, Profile.Data d) {
        int y0 = y;
        y += collectionList(ui, x, y, w, d);
        y += minions(ui, x, y, w, d);
        return y - y0;
    }

    private static final Map<String, Object[]> CATEGORY_LOOK = Map.of(
            "FARMING", new Object[]{"golden_hoe", 0xFF8FCB5A},
            "MINING", new Object[]{"iron_pickaxe", 0xFF9AB4C8},
            "COMBAT", new Object[]{"iron_sword", 0xFFE06A5C},
            "FORAGING", new Object[]{"oak_sapling", 0xFF5FB36A},
            "FISHING", new Object[]{"fishing_rod", 0xFF5AA7D8},
            "RIFT", new Object[]{"ender_eye", 0xFFB070E0});

    // from skyblocker CollectionsPage: section per category with unlocked + maxed. stuff the list doesnt know yet
    // stays in the plain list
    private static int collectionList(MenuUi ui, int x, int y, int w, Profile.Data d) {
        List<io.github.zmuzy.sbc.core.data.CollectionData.Category> categories = io.github.zmuzy.sbc.core.data.CollectionData.categories();
        if (categories.isEmpty() || (d.collections.isEmpty() && d.collectionsTotal.isEmpty())) return plainCollections(ui, x, y, w, d, java.util.Set.of());
        int y0 = y;
        java.util.Set<String> known = new java.util.HashSet<>();
        for (io.github.zmuzy.sbc.core.data.CollectionData.Category c : categories) {
            for (io.github.zmuzy.sbc.core.data.CollectionData.Item i : c.items()) known.add(i.id());
            y += categoryCard(ui, x, y, w, d, c);
        }
        boolean others = false;
        for (String id : d.collections.keySet()) others |= !known.contains(id);
        for (String id : d.collectionsTotal.keySet()) others |= !known.contains(id);
        if (others) y += plainCollections(ui, x, y, w, d, known);
        return y - y0;
    }

    private static int categoryCard(MenuUi ui, int x, int y, int w, Profile.Data d, io.github.zmuzy.sbc.core.data.CollectionData.Category c) {
        int unlocked = 0, maxed = 0;
        for (io.github.zmuzy.sbc.core.data.CollectionData.Item i : c.items()) {
            long shown = collected(d, i.id(), d.collections.getOrDefault(i.id(), 0L));
            if (shown > 0) unlocked++;
            if (i.maxed(shown)) maxed++;
        }
        Object[] look = CATEGORY_LOOK.getOrDefault(c.id(), new Object[]{"chest", 0xFFD8B26E});
        int accent = (Integer) look[1], cols = 3, rows = (c.items().size() + cols - 1) / cols, h = MenuUi.TOP + Math.max(1, rows) * 13 + 2;
        int top = ui.card(x, y, w, h, c.name(), (String) look[0], unlocked + "/" + c.items().size() + " unlocked · " + maxed + " maxed", accent);
        int col = (w - 12 - 2 * 10) / cols, n = 0;
        for (io.github.zmuzy.sbc.core.data.CollectionData.Item i : c.items()) {
            int cx = x + 6 + (n % cols) * (col + 10), cy = y + top - 1 + (n / cols) * 13;
            long own = d.collections.getOrDefault(i.id(), 0L), shown = collected(d, i.id(), own);
            int tier = i.tier(shown);
            boolean max = i.maxed(shown);
            long next = i.next(shown), from = tier == 0 ? 0 : i.tiers()[tier - 1];
            // CollectionItemWidget: ":" in the id becomes "-"
            String item = io.github.zmuzy.sbc.core.ItemRepo.get(i.id()) != null ? i.id() : i.id().replace(':', '-');
            ui.textRight(Prices.coins(shown), cx + col, cy, shown == 0 ? Ui.DIM : Ui.GOLD);
            ui.icon(io.github.zmuzy.sbc.core.ItemIcons.of(item), cx, cy, 0.5f);
            ui.text(ui.ellipsize(i.name() + " " + (tier == 0 ? "0" : roman(tier)), col - 54), cx + 10, cy, max ? Ui.GOLD : shown == 0 ? Ui.DIM : Ui.TEXT);
            ui.meter(cx, cy + 9, col, 1, max ? 1 : (shown - from) / (double) Math.max(1, next - from), max ? Ui.GOLD : accent);
            List<String> tip = new ArrayList<>();
            tip.add(i.name());
            if (shown != own) {
                tip.add("§6Personal: " + MenuUi.num(own));
                tip.add("§bCo-op: " + MenuUi.num(shown - own));
            }
            tip.add("§eCollection: " + MenuUi.num(shown));
            tip.add("§dCollection Tier: " + tier + "/" + i.maxTier());
            if (next >= 0) tip.add("§7Next tier: " + MenuUi.num(next - shown) + " more");
            ui.tip(cx, cy, col, 11, tip.toArray(new String[0]));
            n++;
        }
        return h + MenuUi.GAP;
    }

    private static int plainCollections(MenuUi ui, int x, int y, int w, Profile.Data d, java.util.Set<String> leaveOut) {
        List<Map.Entry<String, Long>> list = new ArrayList<>(d.collections.entrySet());
        for (Map.Entry<String, Long> e : d.collectionsTotal.entrySet()) if (!d.collections.containsKey(e.getKey())) list.add(Map.entry(e.getKey(), 0L));
        list.removeIf(e -> leaveOut.contains(e.getKey()));
        list.sort((a, b) -> Long.compare(collected(d, b.getKey(), b.getValue()), collected(d, a.getKey(), a.getValue())));
        int cols = 3, rows = (list.size() + cols - 1) / cols, h = MenuUi.TOP + Math.max(1, rows) * 13 + 2;
        long total = list.stream().mapToLong(e -> collected(d, e.getKey(), e.getValue())).sum();
        int top = ui.card(x, y, w, h, list.size() + " collections", "chest", list.isEmpty() ? null : Prices.coins(total) + " items", 0xFFD8B26E);
        int col = (w - 12 - 2 * 10) / cols, i = 0;
        double best = list.isEmpty() ? 1 : Math.max(1, collected(d, list.get(0).getKey(), list.get(0).getValue()));
        for (Map.Entry<String, Long> e : list) {
            int cx = x + 6 + (i % cols) * (col + 10), cy = y + top - 1 + (i / cols) * 13;
            long shown = collected(d, e.getKey(), e.getValue()), own = e.getValue();
            Integer tier = d.collectionTiers.get(e.getKey());
            // same ":" -> "-" thing
            String item = io.github.zmuzy.sbc.core.ItemRepo.get(e.getKey()) != null ? e.getKey() : e.getKey().replace(':', '-');
            String name = io.github.zmuzy.sbc.core.ItemRepo.get(item) != null ? GameState.strip(io.github.zmuzy.sbc.core.ItemRepo.name(item)) : GameState.pretty(e.getKey());
            ui.textRight(Prices.coins(shown), cx + col, cy, Ui.GOLD);
            ui.icon(io.github.zmuzy.sbc.core.ItemIcons.of(item), cx, cy, 0.5f);
            ui.text(ui.ellipsize(tier == null ? name : name + " " + roman(tier), col - 54), cx + 10, cy, Ui.TEXT);
            ui.meter(cx, cy + 9, col, 1, shown / best, 0xFFD8B26E);
            if (shown != own || tier != null) {
                List<String> tip = new ArrayList<>();
                tip.add(name);
                if (shown != own) {
                    tip.add("§6Personal: " + MenuUi.num(own));
                    tip.add("§bCo-op: " + MenuUi.num(shown - own));
                }
                tip.add("§eCollection: " + MenuUi.num(shown));
                if (tier != null) tip.add("§dCollection Tier: " + tier);
                ui.tip(cx, cy, col, 11, tip.toArray(new String[0]));
            }
            i++;
        }
        if (i == 0) ui.text("Collections API is disabled on this profile.", x + 6, y + top, Ui.DIM);
        return h + MenuUi.GAP;
    }

    // === NAVIGATE ===

    private record Place(String island, String name, List<String> tags, Vec3 pos) {}

    static int navigate(MenuUi ui, int x, int y, int w) {
        int y0 = y, gap = MenuUi.GAP;
        if (Navigator.target() != null) {
            banner(ui, x, y, w, 38);
            ui.text("Navigating to", x + 10, y + 5, Ui.MUTED);
            ui.textScaled(Navigator.target().name(), x + 10, y + 16, 1.3f, Ui.CYAN, true);
            ui.textRight(Navigator.status, x + w - 66, y + 7, Ui.TEXT);
            if (!Navigator.warpHint.isEmpty())
                ui.button("warp", x + w - 130, y + 18, 66, 14, Navigator.warpHint.replace("/warp ", "Warp "), Ui.PRIMARY, () -> runCommand(Navigator.warpHint));
            ui.button("stop", x + w - 58, y + 12, 50, 16, "Stop", Ui.DANGER, Navigator::stop);
            y += 38 + gap;
        }
        if (!Repo.available) {
            y += ui.wrap("Path lines use island maps, and none were found, so only NPCs the mod has seen are available.", x, y, w, Ui.ORANGE) + 4;
        }
        ui.input(search, x, y, w);
        y += 18 + 4;
        String q = search.value.trim().toLowerCase(Locale.ROOT);
        List<Place> places = new ArrayList<>();
        List<String> islands = q.isEmpty() ? List.of(GameState.island) : Repo.graphIslands();
        for (String isl : islands) {
            Graph g = Repo.graph(isl);
            if (g == null || g.isEmpty()) continue;
            for (Graph.Node n : g.named()) {
                if (n.tags().contains("area") && !n.tags().contains("npc")) continue;
                if (!q.isEmpty() && !n.name().toLowerCase(Locale.ROOT).contains(q)) continue;
                places.add(new Place(isl, n.name(), n.tags(), n.pos()));
            }
        }
        for (Map.Entry<String, double[]> e : Store.I.npcs.entrySet()) {
            String[] k = e.getKey().split("\\|", 2);
            if (k.length < 2) continue;
            if (q.isEmpty() ? !k[0].equals(GameState.island) : !k[1].toLowerCase(Locale.ROOT).contains(q)) continue;
            if (places.stream().anyMatch(p -> p.island().equals(k[0]) && p.name().equalsIgnoreCase(k[1]))) continue;
            places.add(new Place(k[0], k[1], List.of("seen"), new Vec3(e.getValue()[0], e.getValue()[1], e.getValue()[2])));
        }
        var me = Minecraft.getInstance().player;
        Vec3 mp = me == null ? Vec3.ZERO : me.position();
        // npcs on your island first, then other spots here, then everything else. nearest first
        places.sort(Comparator.comparing((Place p) -> !p.island().equals(GameState.island))
                .thenComparing(p -> !isNpc(p))
                .thenComparingDouble(p -> p.pos().distanceToSqr(mp)));
        long npcsHere = places.stream().filter(p -> p.island().equals(GameState.island) && isNpc(p)).count();
        ui.text(q.isEmpty() ? npcsHere + " NPCs on " + (GameState.islandName.isEmpty() ? "this island" : GameState.islandName) + " · type to search every island"
                : places.size() + " results", x + 2, y, Ui.DIM);
        y += 12;

        // one card per section, as tall as whats in it
        List<Place> shown = places.stream().limit(120).toList();
        int i = 0;
        int counter = 0;
        while (i < shown.size()) {
            String sec = sectionOf(shown.get(i));
            int j = i;
            while (j < shown.size() && sectionOf(shown.get(j)).equals(sec)) j++;
            int rowH = 18, h = MenuUi.TOP + (j - i) * rowH;
            int accent = sec.startsWith("NPC") ? 0xFFFFD166 : sec.startsWith("Warps") ? 0xFFB98CFF : NAV;
            long total = places.stream().filter(p -> sectionOf(p).equals(sec)).count();
            int top = ui.card(x, y, w, h, sec, sec.startsWith("NPC") ? "villager_spawn_egg" : sec.startsWith("Warps") ? "ender_pearl" : "map", String.valueOf(total), accent);
            int ry = y + top - 2;
            for (int k = i; k < j; k++) {
                Place p = shown.get(k);
                boolean here = p.island().equals(GameState.island);
                boolean npc = isNpc(p);
                ui.hoverRound(x + 2, ry, w - 4, rowH - 1, 5, 0x00000000, 0x20FFFFFF);
                if (npc) ui.rect(x + 2, ry, 2, rowH - 1, p.tags().contains("seen") ? 0xFF7FE3A8 : Ui.GOLD);
                String tag = p.tags().isEmpty() ? "" : p.tags().get(0);
                ui.icon(npc ? "villager_spawn_egg" : tag.equals("warp") ? "ender_pearl" : "map", x + 6, ry + 1, 0.875f);
                int right = x + w - 6 - 26;
                final Place fp = p;
                ui.button("ng" + counter, right, ry + 1, 26, 14, "Go", Ui.PRIMARY, () -> Navigator.navigate(new Navigator.Target(fp.island(), fp.name(), fp.pos())));
                right -= 5;
                String dist = here ? (int) Math.sqrt(p.pos().distanceToSqr(mp)) + "m" : GameState.pretty(p.island());
                ui.textRight(dist, right, ry + 4, here ? Ui.CYAN : Ui.MUTED);
                int nameEnd = right - ui.width(dist) - 4;
                ui.text(ui.ellipsize(p.name(), nameEnd - x - 90), x + 24, ry + 4, Ui.TEXT);
                int bx = x + 28 + Math.min(ui.width(p.name()), nameEnd - x - 90);
                // section title already says what the rows are, chip only when its mixed or adds something
                if (p.tags().contains("seen")) bx += ui.chip(bx, ry + 3, "SEEN", MenuUi.tone(0xFF7FE3A8)) + 3;
                else if (npc && !here) bx += ui.chip(bx, ry + 3, "NPC", Ui.GOLD) + 3;
                else if (tag.equals("warp")) bx += ui.chip(bx, ry + 3, "WARP", MenuUi.tone(0xFFB98CFF)) + 3;
                if (Navigator.target() != null && Navigator.target().name().equals(p.name())) ui.chip(bx, ry + 3, "NAVIGATING", Ui.CYAN);
                ry += rowH;
                counter++;
            }
            y += h + gap;
            i = j;
        }
        if (shown.isEmpty()) {
            ui.text(GameState.island.isEmpty() ? "Join SkyBlock to see places around you." : "Nothing found.", x + 2, y, Ui.MUTED);
            y += 14;
        }
        return y - y0;
    }

    private static void banner(MenuUi ui, int x, int y, int w, int h) {
        ui.edgeCard(x, y, w, h, NAV);
        ui.gradH(x + 2, y + 1, w - 3, h - 2, Ui.alpha(MenuUi.tone(NAV), 0.22f), Ui.alpha(MenuUi.tone(NAV), 0.02f));
    }

    private static String sectionOf(Place p) {
        return p.island().equals(GameState.island) ? (isNpc(p) ? "NPCs here" : "Warps & places here") : "Other islands";
    }

    private static boolean isNpc(Place p) {
        return p.tags().contains("npc") || p.tags().contains("seen");
    }

    // only ever runs because the player clicked a button for it
    static void runCommand(String cmd) {
        var p = Minecraft.getInstance().player;
        if (p != null) p.connection.sendCommand(cmd.startsWith("/") ? cmd.substring(1) : cmd);
    }

    // === STATS ===

    static int stats(MenuUi ui, int x, int y, int w) {
        int y0 = y, gap = MenuUi.GAP;
        List<Store.SessionRecord> ss = Store.I.sessions;
        double sessionSum = ss.stream().mapToDouble(s -> s.coins).sum();
        double sessionBest = ss.stream().mapToDouble(s -> s.coins).max().orElse(0);
        long sessionMs = ss.stream().mapToLong(s -> s.activeMs).sum();
        double rateCoins = Store.I.rates.values().stream().mapToDouble(r -> r.coins).sum();
        double rateXp = Store.I.rates.values().stream().mapToDouble(r -> r.sbXp).sum();
        long rateMs = Store.I.rates.values().stream().mapToLong(r -> r.activeMs).sum();

        int tg = 3, tw = (w - 3 * tg) / 4, th = 40;
        ui.tile(x, y, tw, th, "Active time (all)", SbTime.dur(Store.I.totalActiveMs), null, Ui.TEXT, -1, 0);
        ui.tile(x + tw + tg, y, tw, th, "This session", "+" + Prices.coins(Tracker.sessionCoins), "coins", Ui.GOLD, -1, 0);
        ui.tile(x + 2 * (tw + tg), y, tw, th, "SkyBlock XP this session", "+" + (int) Tracker.sessionSbXp, null, Ui.CYAN, -1, 0);
        ui.tile(x + 3 * (tw + tg), y, w - 3 * (tw + tg), th, "Earned, measured", rateCoins > 0 ? Prices.coins(rateCoins) : "-", rateMs >= 60_000 ? Prices.coins(rateCoins * 3_600_000.0 / rateMs) + "/h overall" : null, Ui.GOLD, -1, 0);
        y += th + tg;
        ui.tile(x, y, tw, th, "Sessions", MenuUi.num(ss.size()), ss.isEmpty() ? null : SbTime.dur(sessionMs / ss.size()) + " each", Ui.TEXT, -1, 0);
        ui.tile(x + tw + tg, y, tw, th, "Average session", ss.isEmpty() ? "-" : Prices.coins(sessionSum / ss.size()), "coins", Ui.GOLD, -1, 0);
        ui.tile(x + 2 * (tw + tg), y, tw, th, "Best session", ss.isEmpty() ? "-" : Prices.coins(sessionBest), "coins", Ui.GREEN, -1, 0);
        ui.tile(x + 3 * (tw + tg), y, w - 3 * (tw + tg), th, "SkyBlock XP, measured", rateXp > 0 ? MenuUi.num(rateXp) : "-", rateMs >= 60_000 ? String.format(Locale.ROOT, "%.1f XP/h overall", rateXp * 3_600_000.0 / rateMs) : null, Ui.CYAN, -1, 0);
        y += th + gap;

        List<long[]> wealth = Store.I.wealth;
        double[] wv = new double[Math.min(120, wealth.size())];
        for (int i = 0; i < wv.length; i++) {
            long[] p = wealth.get(wealth.size() - wv.length + i);
            wv[i] = p[1] + p[2];
        }
        int chartH = 80, h = MenuUi.TOP + 11 + chartH + 4;
        String change = null;
        if (wv.length > 1) {
            double ch = wv[wv.length - 1] - wv[0];
            change = (ch >= 0 ? "§a+" : "§c") + Prices.coins(ch) + (wv[0] > 0 ? "  " + MenuUi.pct(Math.abs(ch) / wv[0]) : "");
        }
        int top = ui.card(x, y, w, h, "Wealth over time (purse + bank)", "gold_ingot", change, 0xFFFFD166);
        if (wv.length > 1) {
            double hi = 0, lo = Double.MAX_VALUE;
            for (double v : wv) {
                hi = Math.max(hi, v);
                lo = Math.min(lo, v);
            }
            ui.text("§7now §6" + Prices.coins(wv[wv.length - 1]), x + 6, y + top - 1, Ui.GOLD);
            ui.textRight("§7high §6" + Prices.coins(hi) + "  §7low §6" + Prices.coins(lo), x + w - 6, y + top - 1, Ui.GOLD);
        }
        ui.lineChart(x + 4, y + top + 10, w - 8, chartH, wv, Ui.GOLD);
        y += h + gap;

        double[] sv = new double[Math.min(40, ss.size())];
        for (int i = 0; i < sv.length; i++) sv[i] = ss.get(ss.size() - sv.length + i).coins;
        int sh = MenuUi.TOP + 11 + 60 + 4;
        top = ui.card(x, y, w, sh, "Coins earned per session", "emerald", sv.length > 0 ? "last " + sv.length : null, 0xFF5CD8FF);
        if (sv.length > 0) {
            double sum = 0, hi = 0;
            for (double v : sv) {
                sum += v;
                hi = Math.max(hi, v);
            }
            ui.text("§7latest §b" + Prices.coins(sv[sv.length - 1]), x + 6, y + top - 1, Ui.CYAN);
            ui.textRight("§7average §b" + Prices.coins(sum / sv.length) + "  §7best §b" + Prices.coins(hi), x + w - 6, y + top - 1, Ui.CYAN);
        }
        ui.lineChart(x + 4, y + top + 10, w - 8, 60, sv, Ui.CYAN);
        y += sh + gap;

        List<Map.Entry<String, Store.Rate>> rates = new ArrayList<>(Store.I.rates.entrySet());
        rates.sort(Comparator.comparingLong((Map.Entry<String, Store.Rate> e) -> e.getValue().activeMs).reversed());
        rates.removeIf(e -> e.getValue().activeMs < 60_000);
        int rowH = 16, rh = MenuUi.TOP + 11 + Math.max(1, rates.size()) * rowH + 2;
        top = ui.card(x, y, w, rh, "Your measured rates by island", "compass", rates.size() + " islands", 0xFF9CE37D);
        int hy = y + top - 2;
        ui.text("Island", x + 6, hy, Ui.DIM);
        ui.text("Active", x + w * 34 / 100, hy, Ui.DIM);
        ui.text("Coins/h", x + w * 48 / 100, hy, Ui.DIM);
        ui.text("SB XP/h", x + w * 62 / 100, hy, Ui.DIM);
        ui.textRight("Top skill XP/h", x + w - 6, hy, Ui.DIM);
        int ry = hy + 11;
        int i = 0;
        double bestRate = rates.stream().mapToDouble(e -> e.getValue().coinsPerHour()).max().orElse(1);
        for (Map.Entry<String, Store.Rate> e : rates) {
            Store.Rate r = e.getValue();
            if (i++ % 2 == 0) ui.rect(x + 2, ry - 1, w - 4, rowH - 1, 0x0CFFFFFF);
            ui.text(GameState.pretty(e.getKey()), x + 6, ry + 2, Ui.TEXT);
            ui.text(SbTime.dur(r.activeMs), x + w * 34 / 100, ry + 2, Ui.MUTED);
            ui.text(Prices.coins(r.coinsPerHour()), x + w * 48 / 100, ry + 2, Ui.GOLD);
            ui.meter(x + w * 48 / 100, ry + 11, w * 12 / 100, 1, r.coinsPerHour() / Math.max(1, bestRate), 0xFFFFD166);
            ui.text(String.format(Locale.ROOT, "%.1f", r.sbXpPerHour()), x + w * 62 / 100, ry + 2, Ui.CYAN);
            String best = r.skillXp.entrySet().stream().max(Map.Entry.comparingByValue())
                    .map(en -> en.getKey() + " " + Prices.coins(r.skillPerHour(en.getKey()))).orElse("-");
            ui.textRight(best, x + w - 6, ry + 2, Ui.MUTED);
            ry += rowH;
        }
        if (rates.isEmpty()) ui.text("Play a bit - rates appear after a minute of activity on an island.", x + 6, ry + 2, Ui.DIM);
        y += rh + gap;

        // hypixels own numbers once we read them, what we measured till then
        List<io.github.zmuzy.sbc.track.Playtime.Place> real = io.github.zmuzy.sbc.track.Playtime.places();
        long totalT = 0;
        List<Map.Entry<String, Long>> it = new ArrayList<>();
        if (!real.isEmpty()) for (var p : real) {
            totalT += p.ms();
            it.add(Map.entry(p.name(), p.ms()));
        } else {
            for (var e : Store.I.islandTime.entrySet()) {
                totalT += e.getValue();
                if (!e.getKey().isEmpty()) it.add(Map.entry(GameState.pretty(e.getKey()), e.getValue()));
            }
            it.sort(Map.Entry.<String, Long>comparingByValue().reversed());
        }
        it = it.stream().limit(real.isEmpty() ? 10 : 16).toList();
        boolean ask = io.github.zmuzy.sbc.track.Playtime.stale();
        int askH = ask ? 24 : 0;
        int th2 = MenuUi.TOP + askH + Math.max(1, it.size()) * 12 + 16;
        top = ui.card(x, y, w, th2, "Where your time goes", "clock", SbTime.dur(totalT), 0xFFFF9EC7);
        int[] colors = {Ui.VIOLET, Ui.CYAN, Ui.GREEN, Ui.GOLD, Ui.PINK, Ui.ORANGE, Ui.RED};
        int k = 0;
        int ty = y + top - 1;
        if (ask) {
            ui.wrap(real.isEmpty() ? "Only what the mod has seen so far. Hypixel keeps your real time on every island:" : "Read a while ago. Hypixel has newer numbers:", x + 6, ty + 3, w - 150, Ui.MUTED);
            ui.button("playtime_read", x + w - 136, ty + 1, 130, 18, "Read /playtimedetailed", Ui.PRIMARY, io.github.zmuzy.sbc.track.Playtime::ask);
            ty += askH;
        }
        for (Map.Entry<String, Long> e : it) {
            double frac = totalT == 0 ? 0 : (double) e.getValue() / totalT;
            ui.text(ui.ellipsize(e.getKey(), 126), x + 6, ty, Ui.TEXT);
            ui.bar(x + 136, ty + 1, w - 136 - 96, 6, frac, colors[k % colors.length], Ui.lerpColor(colors[k % colors.length], 0xFFFFFFFF, 0.3f));
            ui.textRight("§f" + SbTime.dur(e.getValue()) + String.format(Locale.ROOT, "  §b%.0f%%", frac * 100), x + w - 6, ty, Ui.MUTED);
            ty += 12;
            k++;
        }
        if (!ask && !real.isEmpty()) {
            ui.text("From /playtimedetailed, read " + SbTime.dur(io.github.zmuzy.sbc.track.Playtime.readAgo()) + " ago, plus what the mod measured since.", x + 6, ty + 2, Ui.DIM);
            ui.button("playtime_again", x + w - 66, ty, 60, 14, "Read again", Ui.GHOST, io.github.zmuzy.sbc.track.Playtime::ask);
        }
        y += th2 + gap;
        return y - y0;
    }
}
