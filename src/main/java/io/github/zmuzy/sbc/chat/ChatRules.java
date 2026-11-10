// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.chat;

import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.LegacyText;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.ui.Toasts;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

// a rule from the rules screen. from skyblocker ChatRule (inputs: partial, regex, ignore case, formatting,
// locations) and ChatRulesHandler (outputs: hide/replace, announcement, action bar, toast, sound)
// /sbc rule ones keep their single action in ChatFilters
public final class ChatRules {
    // sounds from skyblocker ChatRuleConfigScreen
    public static final java.util.List<String> SOUND_NAMES = java.util.List.of("Pling", "Cave", "Zombie", "Critical", "Ding", "Break", "Amethyst");

    private static final Map<String, Pattern> COMPILED = new HashMap<>();

    private ChatRules() {}

    // what a new rule starts as
    public static ChatFilters.Rule fresh() {
        ChatFilters.Rule r = new ChatFilters.Rule();
        r.action = "rule";
        r.name = "New rule";
        return r;
    }

    // ChatRule.isMatch on the text only, no locations. null = no match, empty matcher = matched with nothing to fill in
    static java.util.Optional<Matcher> match(ChatFilters.Rule r, String input) {
        String test = r.ignoreCase ? input.toLowerCase(Locale.ENGLISH) : input;
        String filter = r.ignoreCase ? r.regex.toLowerCase(Locale.ENGLISH) : r.regex;
        if (filter.isBlank()) return null;
        if (r.useRegex) {
            Pattern p = compile(filter);
            if (p == null) return null;
            Matcher m = p.matcher(test);
            if (r.partial ? m.find() : m.matches()) return java.util.Optional.of(m);
            return null;
        }
        boolean hit = r.partial ? test.contains(filter) : filter.equals(test);
        return hit ? java.util.Optional.empty() : null;
    }

    private static Pattern compile(String filter) {
        if (COMPILED.containsKey(filter)) return COMPILED.get(filter);
        Pattern p;
        try {
            p = Pattern.compile(filter);
        } catch (PatternSyntaxException e) {
            p = null;
        }
        COMPILED.put(filter, p);
        return p;
    }

    // so the screen can say if the regex is broken
    public static boolean valid(ChatFilters.Rule r) {
        return !r.useRegex || compile(r.ignoreCase ? r.regex.toLowerCase(Locale.ENGLISH) : r.regex) != null;
    }

    // from skyblocker Match.insertCaptureGroups
    static String fill(java.util.Optional<Matcher> match, String text) {
        if (match.isEmpty()) return text;
        Matcher m = match.get();
        m.reset();
        if (!m.find()) return text;
        StringBuilder sb = new StringBuilder();
        try {
            m.appendReplacement(sb, text);
        } catch (IndexOutOfBoundsException | IllegalArgumentException e) {
            return text;
        }
        return sb.substring(m.start());
    }

    // ChatRulesHandler.formatText: § first, then &
    static String codes(String text) {
        return text.contains("§") ? text : text.replace('&', '§');
    }

    static ChatBus.Result apply(ChatFilters.Rule r, ChatBus.Line line) {
        if (!r.locations.isEmpty() && !r.locations.contains(GameState.island)) return ChatBus.PASS;
        java.util.Optional<Matcher> match = match(r, r.formatting ? LegacyText.of(line.component()) : line.plain());
        if (match == null) return ChatBus.PASS;
        Minecraft mc = Minecraft.getInstance();
        if (!r.announcement.isBlank()) {
            Feature f = Features.get("chat_rules");
            Option def = f == null ? null : f.option("chat_rules.announcement_ticks");
            int ticks = r.announcementTicks > 0 ? r.announcementTicks : def == null ? 60 : (int) def.number();
            mc.gui.hud.setTimes(0, ticks, 10);
            mc.gui.hud.setTitle(Component.literal(codes(fill(match, r.announcement))));
        }
        if (!r.actionBar.isBlank() && mc.player != null) mc.player.sendOverlayMessage(Component.literal(codes(fill(match, r.actionBar))));
        if (!r.toast.isBlank()) Toasts.push(r.name, codes(fill(match, r.toast)), 0xFF39D0FF, Math.max(1, r.toastSeconds == 0 ? 4 : r.toastSeconds) * 1000L, r.toastIcon);
        SoundEvent sound = sound(r.sound);
        if (sound != null && mc.player != null) mc.player.playSound(sound, 1f, 1f);
        if (r.hide) return ChatBus.CANCEL;
        if (!r.chat.isBlank()) return new ChatBus.Replace(Component.literal(codes(fill(match, r.chat))));
        return ChatBus.PASS;
    }

    // one of the names above or any sound id
    private static SoundEvent sound(String name) {
        if (name == null || name.isBlank() || name.equals("None")) return null;
        return switch (name) {
            case "Pling" -> SoundEvents.NOTE_BLOCK_PLING.value();
            case "Cave" -> SoundEvents.AMBIENT_CAVE.value();
            case "Zombie" -> SoundEvents.ZOMBIE_AMBIENT;
            case "Critical" -> SoundEvents.PLAYER_ATTACK_CRIT;
            case "Ding" -> SoundEvents.ARROW_HIT_PLAYER;
            case "Break" -> SoundEvents.ANVIL_LAND;
            case "Amethyst" -> SoundEvents.AMETHYST_BLOCK_BREAK;
            default -> {
                Identifier id = Identifier.tryParse(name);
                yield id == null ? null : BuiltInRegistries.SOUND_EVENT.getValue(id);
            }
        };
    }
}
