// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.dungeon;

import dev.zmuzy.sbc.SbcMod;
import dev.zmuzy.sbc.chat.ChatBus;
import dev.zmuzy.sbc.core.Gain;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.LegacyText;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.item.sacks.Sacks;
import dev.zmuzy.sbc.mc.Interactions;
import dev.zmuzy.sbc.mc.SoundHooks;
import dev.zmuzy.sbc.ui.Toasts;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;

// dungeon warnings + sounds. from skyhanni DungeonTrinityHelper, ShadowAssassinNotification,
// SpringBootsHelper, ArchitectFeatures, LowHealthAlert, SecretChime, and skyblocker RareRoomAlert
public final class DungeonAlerts {
    private static final Patterns.P PUZZLE_COUNT = Patterns.p("Score.PUZZLE_COUNT"), TEAMMATES = Patterns.p("SH.sb.dungeon.teammates"), SECRETS = Patterns.p("Score.SECRETS");
    private static final Patterns.P FAIL = Patterns.p("SH.dungeon.architectsdraft.puzzle.fail.normal"), FAIL_QUIZ = Patterns.p("SH.dungeon.architectsdraft.puzzle.fail.quiz");
    // RareRoomAlerts 3 rooms, named like the room data names them
    private static final String TRINITY = "trinity-4", TOMIOKA = "tomioka-0", DUNCAN = "duncan-1";

    private static boolean trinityDone;
    private static final Set<String> roomsTold = new HashSet<>();
    private static List<String> lastBoard = List.of();
    private static int soundStreak;
    private static int lowHpRepeat;
    private static long lowHpNext;
    private static long lastChime;

    private DungeonAlerts() {}

    private static void title(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) mc.gui.hud.setTitle(Component.literal(text));
    }

    private static void beep() {
        var p = Minecraft.getInstance().player;
        if (p != null) p.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1f);
    }

    private static void say(String text) {
        var p = Minecraft.getInstance().player;
        if (p != null) p.sendSystemMessage(Component.literal("§b[SBC] §f" + text));
    }

    // sound by its id like the settings hold it, nothing if its not one
    static void play(String id, float pitch) {
        var p = Minecraft.getInstance().player;
        Identifier rl = Identifier.tryParse(id);
        if (p == null || rl == null) return;
        p.playSound(SoundEvent.createVariableRangeEvent(rl), 1f, pitch);
    }

    // --- possible trinity ---

    // from skyhanni DungeonTrinityHelper: 5 puzzles = trinity room is possible
    static boolean trinityPossible(int puzzles) {
        return puzzles >= 5;
    }

    private static void checkTrinity() {
        if (trinityDone || !Features.on("dungeon_trinity") || !DungeonRun.active()) return;
        for (String l : GameState.tab) {
            Matcher m = PUZZLE_COUNT.matcher(l);
            if (!m.find()) continue;
            trinityDone = true;
            if (trinityPossible(Integer.parseInt(m.group(1)))) {
                title("§dPossible Trinity");
                say("5 puzzle dungeon, watch out for possible Trinity room!");
            }
            return;
        }
    }

    // --- rare rooms ---

    private static void checkRooms() {
        if (!Features.on("dungeon_rare_room")) return;
        var room = Dungeon.current();
        if (room == null) return;
        String id = room.def.id();
        boolean want = switch (id) {
            case TRINITY -> DungeonMarks.opt("dungeon_rare_room", "trinity");
            case TOMIOKA -> DungeonMarks.opt("dungeon_rare_room", "tomioka");
            case DUNCAN -> DungeonMarks.opt("dungeon_rare_room", "duncan");
            default -> false;
        };
        if (!want || !roomsTold.add(id)) return;
        title("§d" + room.def.name().toUpperCase(Locale.ENGLISH));
        beep();
        say("Found the " + room.def.name() + " room!");
    }

    // --- shadow assassin ---

    // from skyhanni ShadowAssassinNotification: he jumps when the world border warning goes to 10 sec
    public static void onBorder(ClientboundInitializeBorderPacket packet) {
        if (!DungeonRun.inDungeon() || !Features.on("dungeon_sa_jump")) return;
        if (DungeonRun.floor().contains("3") && DungeonRun.inBoss()) return;
        if (packet.getWarningTime() == 10000) {
            title("§cShadow Assassin Jumping!");
            beep();
        }
    }

    // --- spring boots ---

    // from skyhanni SpringBootsHelper: 5 pling sounds while sneaking in spring boots = high enough
    private static boolean onSound(SoundHooks.Sound s) {
        if (DungeonRun.inDungeon()) {
            if (DungeonMarks.opt("dungeon_secret_chime", "mute_chest") && Features.on("dungeon_secret_chime") && isChestSound(s)) return true;
            if (DungeonMarks.opt("dungeon_secret_chime", "mute_lever") && Features.on("dungeon_secret_chime") && isLeverSound(s)) return true;
            springBoots(s);
        }
        return false;
    }

    private static void springBoots(SoundHooks.Sound s) {
        if (!Features.on("dungeon_spring_boots") || !DungeonRun.isFloor("F7", "M7") || !DungeonRun.inBoss()) return;
        var p = Minecraft.getInstance().player;
        if (p == null || !"SPRING_BOOTS".equals(Gain.sbId(p.getItemBySlot(EquipmentSlot.FEET)))) return;
        String n = s.name();
        if (!n.equals("entity.firework_rocket.launch") && !n.equals("block.note_block.pling") && !n.equals("entity.generic.eat")) return;
        if (!n.equals("block.note_block.pling")) {
            soundStreak = 0;
            return;
        }
        if (!p.isShiftKeyDown()) return;
        if (++soundStreak == 5) {
            title("§cSpring Boots ready!");
            beep();
        }
    }

    // from skyhanni DungeonSecretChime.isChestSound
    static boolean isChestSound(SoundHooks.Sound s) {
        return switch (s.name()) {
            case "block.chest.open" -> s.volume() == 0.5f;
            case "block.note_block.harp" -> s.volume() == 1f && (s.pitch() == 0.7936508f || s.pitch() == 0.8888889f || s.pitch() == 1f || s.pitch() == 1.0952381f || s.pitch() == 1.1904762f);
            default -> false;
        };
    }

    // from skyhanni DungeonSecretChime.isLeverSound
    static boolean isLeverSound(SoundHooks.Sound s) {
        return switch (s.name()) {
            case "block.anvil.break" -> s.volume() == 1f && s.pitch() == 1.6984127f;
            case "block.lever.click" -> (s.volume() == 1f || s.volume() == 2f) && s.pitch() == 0.4920635f;
            default -> false;
        };
    }

    // --- secret chime ---

    public static void chime() {
        lastChime = System.currentTimeMillis();
        play(DungeonMarks.text("dungeon_secret_chime", "sound", "block.note_block.chime"), (float) DungeonMarks.number("dungeon_secret_chime", "pitch", 1.6));
    }

    // tab says found too late, so only chime for stuff no click told us about (like a bat)
    public static boolean chimedLately() {
        return System.currentTimeMillis() - lastChime < 2500;
    }

    // from skyhanni DungeonApi.onBlockClick -> onDungeonClickedBlock: click on a chest, trapped chest, lever or
    // wither essence
    static String clickedType(Vec3 pos) {
        var level = Minecraft.getInstance().level;
        if (level == null) return null;
        BlockPos at = BlockPos.containing(pos);
        var state = level.getBlockState(at);
        if (state.is(Blocks.CHEST)) return "Chest";
        if (state.is(Blocks.TRAPPED_CHEST)) return "Trapped Chest";
        if (state.is(Blocks.LEVER)) return "Lever";
        if (state.is(Blocks.PLAYER_HEAD) && level.getBlockEntity(at) instanceof net.minecraft.world.level.block.entity.SkullBlockEntity skull && skull.getOwnerProfile() != null) {
            for (var p : skull.getOwnerProfile().partialProfile().properties().get("textures"))
                if (p.value().equals(DungeonMarks.skull("WITHER_ESSENCE"))) return "Wither Essence";
        }
        return null;
    }

    // from skyhanni DungeonApi.inWaterRoom: water room levers arent secrets
    static boolean inWaterRoom() {
        var room = Dungeon.current();
        return room != null && room.def.id().equals("water-puzzle");
    }

    private static void onBlock(Vec3 pos, boolean right) {
        if (!right || !DungeonRun.inDungeon() || DungeonRun.inBoss()) return;
        String type = clickedType(pos);
        if (type == null || (type.equals("Lever") && inWaterRoom())) return;
        if (Features.on("dungeon_secret_chime")) chime();
        DungeonObjects.clicked(BlockPos.containing(pos), type);
    }

    // --- teammates low on health ---

    // from skyhanni LowHealthAlert.onScoreboardChange: a teammates health going red on the sidebar
    private static void checkTeam(Minecraft mc) {
        List<String> now = GameState.scoreboardFormatted;
        if (now == lastBoard) return;
        boolean first = lastBoard.isEmpty();
        Set<String> before = new HashSet<>(lastBoard);
        lastBoard = now;
        if (first || !Features.on("dungeon_team_lowhp") || !DungeonRun.active()) return;
        if (DungeonMarks.opt("dungeon_team_lowhp", "healer_only") && !DungeonRun.isClass("Healer")) return;
        for (String line : now) {
            if (before.contains(line)) continue;
            Matcher m = TEAMMATES.matcher(line);
            if (!m.matches()) continue;
            String color = m.group("color"), health = m.group("health");
            if (color == null || !color.equals("c") || "DEAD".equals(health)) continue;
            mc.gui.hud.setTitle(Component.literal("§c" + m.group("username") + " §ais low"));
            mc.gui.hud.setSubtitle(Component.literal("§c" + health + "❤"));
            lowHpRepeat = DungeonMarks.integer("dungeon_team_lowhp", "repeat", 5);
            lowHpNext = 0;
        }
    }

    public static void lowHpSound() {
        play(DungeonMarks.text("dungeon_team_lowhp", "sound", "block.anvil.land"), (float) DungeonMarks.number("dungeon_team_lowhp", "pitch", 1));
    }

    private static void tick(Minecraft mc) {
        if (!DungeonRun.inDungeon()) {
            trinityDone = false;
            roomsTold.clear();
            lastBoard = List.of();
            soundStreak = 0;
            return;
        }
        if (mc.player == null) return;
        long now = System.currentTimeMillis();
        if (lowHpRepeat > 0 && now >= lowHpNext) {
            lowHpRepeat--;
            lowHpNext = now + 100;
            lowHpSound();
        }
        if (mc.player.tickCount % 5 != 0) return;
        checkTrinity();
        checkRooms();
        checkTeam(mc);
    }

    // --- architects first draft ---

    // from skyhanni DungeonArchitectFeatures.generateMessage: the line becomes clickable for a draft from your sacks
    private static ChatBus.Result onChat(ChatBus.Line line) {
        if (!DungeonRun.inDungeon() || !Features.on("dungeon_architect")) return ChatBus.PASS;
        String formatted = LegacyText.of(line.component());
        Matcher m = FAIL.matcher(formatted);
        if (!m.matches()) m = FAIL_QUIZ.matcher(formatted);
        if (!m.matches()) return ChatBus.PASS;
        Integer amount = Sacks.amountInSacks("ARCHITECT_FIRST_DRAFT");
        if (amount == null || amount <= 0) return ChatBus.PASS;
        title("§c§lPUZZLE FAILED!");
        return new ChatBus.Replace(Component.literal("§c§lPUZZLE FAILED! §r§b" + m.group("name") + " §r§efailed a puzzle. \n§eClick here to get §5Architect's First Draft §7(§e" + amount + "x left§7)")
                .withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("/gfs ARCHITECT_FIRST_DRAFT 1"))));
    }

    public static void register() {
        java.util.function.BooleanSupplier dg = () -> DungeonRun.inDungeon() || SbcMod.DEV;
        Features.add(new Feature("dungeon_alerts_tick", "Dungeons", "Dungeon warnings background", "Watches the run for the warnings below.", "").alwaysOn().tick(DungeonAlerts::tick));
        Features.add(new Feature("dungeon_trinity", "Dungeons", "Possible Trinity", "A title when a dungeon has five puzzles, which is when the Trinity room can appear.", "SkyHanni: Trinity Helper").when(dg));
        Features.add(new Feature("dungeon_rare_room", "Dungeons", "Rare room alert", "A title and a line in your chat when you find a rare room.", "Skyblocker: Rare Room Alert").when(dg)
                .option(Option.bool("dungeon_rare_room.trinity", "Trinity", "Tell when you find Trinity.", true))
                .option(Option.bool("dungeon_rare_room.tomioka", "Tomioka", "Tell when you find Tomioka.", false))
                .option(Option.bool("dungeon_rare_room.duncan", "Duncan", "Tell when you find Duncan.", false)));
        Features.add(new Feature("dungeon_sa_jump", "Dungeons", "Shadow Assassin jump", "A title and a beep when a Shadow Assassin is about to jump on you.", "SkyHanni: SA Jump Notification").when(dg));
        Features.add(new Feature("dungeon_spring_boots", "Dungeons", "Spring Boots charged", "A title and a beep in the Floor 7 boss when Spring Boots are charged high enough.", "SkyHanni: Spring Boots Notification").when(dg));
        Feature arch = Features.add(new Feature("dungeon_architect", "Dungeons", "Architect's Draft on a failed puzzle", "When a puzzle fails and you have a draft in your sacks, a title and a line you can click to take one.", "SkyHanni: Architect Notifier").when(dg));
        Features.add(new Feature("dungeon_team_lowhp", "Dungeons", "Teammate low on health", "A title and a sound when a teammate's health on the sidebar turns red.", "SkyHanni: Low Health Alert").when(dg)
                .option(Option.bool("dungeon_team_lowhp.healer_only", "Only as a Healer", "Only warns while you play Healer.", true))
                .option(Option.text("dungeon_team_lowhp.sound", "Sound", "The sound's id. Hear it with /sbc dungeonsound lowhp.", "block.anvil.land"))
                .option(Option.number("dungeon_team_lowhp.pitch", "Pitch", "The pitch of the sound.", 1, 0.5, 2, 0.05))
                .option(Option.integer("dungeon_team_lowhp.repeat", "Repeat", "How many times the sound plays.", 5, 1, 20)));
        ChatBus.register(ChatBus.FILTER - 5, "dungeonarchitect", arch, DungeonAlerts::onChat);
        SoundHooks.listen(DungeonAlerts::onSound);
        Interactions.onBlockClick(DungeonAlerts::onBlock);
    }
}
