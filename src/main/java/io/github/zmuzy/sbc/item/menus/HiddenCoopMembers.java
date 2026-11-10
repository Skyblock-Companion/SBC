// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.item.menus;

import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.zmuzy.sbc.combat.Combat;
import io.github.zmuzy.sbc.core.Gain;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.LegacyText;
import io.github.zmuzy.sbc.core.Patterns;
import io.github.zmuzy.sbc.core.Safe;
import io.github.zmuzy.sbc.core.Store;
import io.github.zmuzy.sbc.core.game.GameProfile;
import io.github.zmuzy.sbc.core.game.Seen;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// from skyhanni HideExCoopMembers: people who left your coop, out of collection tooltips. historic members
// menu says who, or /sbc coopmembers
public final class HiddenCoopMembers {
    public static final String ID = "hide_ex_coop";
    private static final Patterns.P COLLECTION_MENU = Patterns.p("SH.data.collection.api.collections"), PLAYER = Patterns.p("SH.data.collection.api.playercounter.new"),
            NOT_MAXED = Patterns.p("SH.data.collection.api.collections.notmaxed.new"), HISTORIC = Patterns.p("SH.data.hiddenmembers.inventory.historic");

    private HiddenCoopMembers() {}

    // skyhanni handleTooltip. total = what your collection is at. pure for tests
    static void handle(List<Component> tip, Set<String> hidden, long total) {
        int coop = -1;
        for (int i = 0; i < tip.size(); i++) {
            if (tip.get(i).getString().equals("Co-op Contributions:")) {
                coop = i;
                break;
            }
        }
        if (coop < 0) return;
        int remaining = 0;
        List<Integer> remove = new ArrayList<>();
        for (int i = coop; i < tip.size(); i++) {
            String line = tip.get(i).getString();
            if (line.isBlank()) continue;
            Matcher m = PLAYER.matcher(line);
            if (!m.matches()) continue;
            if (hidden.contains(m.group("name"))) remove.add(i);
            else remaining++;
        }
        for (int i = remove.size() - 1; i >= 0; i--) tip.remove((int) remove.get(i));
        if (remaining >= 2) return;
        boolean notMaxed = false;
        for (Component c : tip) notMaxed |= NOT_MAXED.matcher(c.getString()).matches();
        if (!notMaxed) {
            if (coop + 1 < tip.size()) tip.set(coop + 1, Component.literal("§7Total collected: §e" + String.format(Locale.ROOT, "%,d", total)));
            if (coop < tip.size()) tip.set(coop, Component.literal("§a§lCOLLECTION MAXED OUT!"));
        } else {
            for (int i = coop + 1; i >= coop - 1; i--) if (i >= 0 && i < tip.size()) tip.remove(i);
        }
    }

    private static String menuTitle() {
        var screen = Minecraft.getInstance().gui.screen();
        return screen == null ? "" : GameState.strip(screen.getTitle().getString()).trim();
    }

    private static void tooltip(ItemStack stack, List<Component> tip) {
        Seen s = GameProfile.current();
        if (s == null || s.hiddenCoopMembers.isEmpty() || !COLLECTION_MENU.matcher(menuTitle()).matches()) return;
        Long total = s.collections.get(Gain.sbId(stack));
        handle(tip, s.hiddenCoopMembers, total == null ? 0 : total);
    }

    // skyhanni onInventoryOpen: every head in historic members. a rank in front of the name isnt part of it
    private static void historic(MenuView v) {
        Seen s = GameProfile.current();
        if (s == null) return;
        for (int slot = 0; slot < v.top; slot++) {
            MenuView.Item it = v.item(slot);
            if (!it.model.equals("player_head")) continue;
            String name = it.clean.substring(it.clean.lastIndexOf(' ') + 1);
            if (s.hiddenCoopMembers.add(name)) Store.markDirty();
        }
    }

    private static int edit(String name, boolean add) {
        Seen s = GameProfile.current();
        if (s == null) return 1;
        boolean changed = add ? s.hiddenCoopMembers.add(name) : s.hiddenCoopMembers.remove(name);
        if (!changed) Combat.say(add ? "§cThat username is already in the list!" : "§cThat username wasn't in the list!");
        else {
            Store.markDirty();
            Combat.say((add ? "Added " : "Removed ") + name + ".");
        }
        return 1;
    }

    public static void register() {
        Features.add(new Feature(ID, "Menus", "Hide former co-op members", "Takes those who left your co-op out of the collection tooltips. Open Historic Members once, or use /sbc coopmembers.", "SkyHanni: Hide Co-op Member Collections")
                .when(() -> GameState.inSkyBlock).defaultOff().tooltip(HiddenCoopMembers::tooltip));
        MenuMarks.add(ID, t -> HISTORIC.matcher(t).matches(), HiddenCoopMembers::historic);
        Safe.commands("HiddenCoopMembers.commands", (dispatcher, registry) -> dispatcher.register(literal("sbc")
                .then(literal("coopmembers")
                        .then(literal("add").then(argument("name", StringArgumentType.word()).executes(c -> edit(StringArgumentType.getString(c, "name"), true))))
                        .then(literal("remove").then(argument("name", StringArgumentType.word()).executes(c -> edit(StringArgumentType.getString(c, "name"), false)))))));
    }
}
