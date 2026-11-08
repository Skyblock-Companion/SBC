// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.item.custom;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.ItemIcons;
import dev.zmuzy.sbc.core.ItemInfo;
import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.misc.MiscStore;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimPattern;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// how your own items look on your own screen, by item uuid. from skyblocker item/custom (CustomItemNames,
// CustomArmorTrims, CustomHelmetTextures, CustomArmorAnimatedDyes, RepoDyeColors, SkyblockItemModels)
// with its mixins on DataComponentHolder, ItemStack, DyedItemColor, ItemModelResolver,
// EquipmentLayerRenderer. nobody else sees any of it
// any stays false till a look is set and every hook checks it first, so a player with no looks pays one field read
public final class CustomLooks {
    public static final String ID = "custom_looks";

    // saved in misc.json
    public static final class Data {
        public Map<String, String> names = new HashMap<>();
        public Map<String, Integer> dyes = new HashMap<>();
        public Map<String, AnimatedDye> animatedDyes = new HashMap<>();
        // uuid -> {material, pattern}
        public Map<String, String[]> trims = new HashMap<>();
        public Map<String, String> helmets = new HashMap<>();
        public Map<String, Boolean> glint = new HashMap<>();
        public Map<String, String> models = new HashMap<>();
        public Map<String, String> armorModels = new HashMap<>();

        boolean empty() {
            return names.isEmpty() && dyes.isEmpty() && animatedDyes.isEmpty() && trims.isEmpty() && helmets.isEmpty() && glint.isEmpty() && models.isEmpty() && armorModels.isEmpty();
        }
    }

    public record Keyframe(int color, float time) {}

    public record AnimatedDye(List<Keyframe> keyframes, boolean cycleBack, float delay, float duration) {}

    // false as long as no look is set anywhere, thats all most players ever pay for
    private static volatile boolean any;
    private static final Map<String, Component> NAME_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<String, ResolvableProfile> PROFILE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<String, ArmorTrim> TRIM_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<String, Identifier> ID_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<AnimatedDye, DyeState> DYE_STATES = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<ItemStack, String> UUIDS = Collections.synchronizedMap(new WeakHashMap<>());

    private CustomLooks() {}

    public static Data data() {
        MiscStore s = MiscStore.get();
        if (s.customLooks == null) s.customLooks = new Data();
        return s.customLooks;
    }

    private static void changed() {
        any = !data().empty();
        NAME_CACHE.clear();
        MiscStore.save();
    }

    // slow path of a hook, only once a look is set somewhere
    private static boolean on() {
        return GameState.inSkyBlock && Features.on(ID);
    }

    // skyblocker ItemStack.getUuid, cached per stack bc a look is read per item per frame
    public static String uuid(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        String known = UUIDS.get(stack);
        if (known != null) return known;
        var cd = stack.get(DataComponents.CUSTOM_DATA);
        String uuid = cd == null ? "" : cd.copyTag().getStringOr("uuid", "");
        UUIDS.put(stack, uuid);
        return uuid;
    }

    // hooks

    // skyblocker DataComponentHolderMixin: trim, head, glint
    @SuppressWarnings("unchecked")
    public static <T> T component(Object holder, DataComponentType<? extends T> type, T original) {
        if (!any || !(holder instanceof ItemStack stack)) return original;
        if (type != DataComponents.TRIM && type != DataComponents.PROFILE && type != DataComponents.ENCHANTMENT_GLINT_OVERRIDE) return original;
        if (!on()) return original;
        String uuid = uuid(stack);
        if (uuid.isEmpty()) return original;
        Data d = data();
        if (type == DataComponents.TRIM) {
            String[] t = d.trims.get(uuid);
            ArmorTrim trim = t == null ? null : trim(t[0], t[1]);
            return trim == null ? original : (T) trim;
        }
        if (type == DataComponents.PROFILE) {
            String texture = stack.is(Items.PLAYER_HEAD) ? d.helmets.get(uuid) : null;
            return texture == null ? original : (T) PROFILE_CACHE.computeIfAbsent(texture, ItemIcons::head);
        }
        Boolean glint = d.glint.get(uuid);
        return glint == null ? original : (T) glint;
    }

    // from skyblocker ItemStackMixin.customItemNames
    public static Component name(ItemStack stack, Component original) {
        if (!any || !on()) return original;
        Map<String, String> names = data().names;
        if (names.isEmpty()) return original;
        String uuid = uuid(stack);
        String text = uuid.isEmpty() ? null : names.get(uuid);
        return text == null ? original : NAME_CACHE.computeIfAbsent(uuid, u -> Component.literal(text).withStyle(s -> s.withItalic(false)));
    }

    // skyblocker DyedItemColorMixin: animated dye first, then the plain one
    public static int dye(ItemStack stack, int original) {
        if (!any || !on()) return original;
        Data d = data();
        if (d.animatedDyes.isEmpty() && d.dyes.isEmpty()) return original;
        String uuid = uuid(stack);
        if (uuid.isEmpty()) return original;
        AnimatedDye a = d.animatedDyes.get(uuid);
        if (a != null) return 0xFF000000 | animate(a);
        Integer c = d.dyes.get(uuid);
        return c == null ? original : 0xFF000000 | c;
    }

    // from skyblocker ItemModelResolverMixin
    public static Object model(Object original, ItemStack stack) {
        if (!any || !(original instanceof Identifier) || !on()) return original;
        Map<String, String> models = data().models;
        if (models.isEmpty()) return original;
        String model = models.get(uuid(stack));
        Identifier id = model == null ? null : identifier(model);
        return id == null ? original : id;
    }

    // from skyblocker EquipmentLayerRendererMixin
    public static ResourceKey<EquipmentAsset> armorModel(ResourceKey<EquipmentAsset> original, ItemStack stack) {
        if (!any || !on()) return original;
        Map<String, String> models = data().armorModels;
        if (models.isEmpty()) return original;
        String model = models.get(uuid(stack));
        Identifier id = model == null ? null : identifier(model);
        return id == null ? original : ResourceKey.create(EquipmentAssets.ROOT_ID, id);
    }

    private static Identifier identifier(String text) {
        return ID_CACHE.computeIfAbsent(text, Identifier::tryParse);
    }

    // skyblocker initializeTrimCache: looked up when first needed bc the registries are the servers
    private static ArmorTrim trim(String material, String pattern) {
        return TRIM_CACHE.computeIfAbsent(material + "|" + pattern, k -> {
            var conn = Minecraft.getInstance().getConnection();
            Identifier m = Identifier.tryParse(material), p = Identifier.tryParse(pattern);
            if (conn == null || m == null || p == null) return null;
            try {
                var materials = conn.registryAccess().lookupOrThrow(Registries.TRIM_MATERIAL);
                var patterns = conn.registryAccess().lookupOrThrow(Registries.TRIM_PATTERN);
                Holder<TrimMaterial> hm = materials.getOrThrow(ResourceKey.create(Registries.TRIM_MATERIAL, m));
                Holder<TrimPattern> hp = patterns.getOrThrow(ResourceKey.create(Registries.TRIM_PATTERN, p));
                return new ArmorTrim(hm, hp);
            } catch (RuntimeException e) {
                return null;
            }
        });
    }

    // animated dyes, from skyblocker AnimatedDyeStateTracker

    static final class DyeState {
        float progress;
        boolean back;
        int lastColor;
        long lastAt;

        DyeState(AnimatedDye dye) {
            if (dye.delay() > 0) {
                if (dye.cycleBack()) {
                    back = true;
                    progress = dye.delay() / dye.duration();
                } else {
                    progress = 1 - dye.delay() / dye.duration();
                }
                progress = clamp(progress);
            }
        }

        int interpolate(AnimatedDye dye, float deltaTicks) {
            update(dye, deltaTicks);
            List<Keyframe> keys = dye.keyframes();
            int k = 0;
            // k cant be the last keyframe or k + 1 is out of bounds
            while (k < keys.size() - 2 && keys.get(k + 1).time() < progress) k++;
            Keyframe current = back ? keys.get(k + 1) : keys.get(k), next = back ? keys.get(k) : keys.get(k + 1);
            float colorProgress = clamp((progress - current.time()) / (next.time() - current.time()));
            return lastColor = interpolateOkLab(current.color(), next.color(), colorProgress);
        }

        private void update(AnimatedDye dye, float deltaTicks) {
            float v = deltaTicks * 0.05f / dye.duration();
            if (back) {
                progress -= v;
                if (progress <= 0f) {
                    back = false;
                    progress = Math.abs(progress);
                }
            } else {
                progress += v;
                if (progress >= 1f) {
                    if (dye.cycleBack()) {
                        back = true;
                        progress = 2f - progress;
                    } else {
                        progress %= 1f;
                    }
                }
            }
            progress = clamp(progress);
        }

        private static float clamp(float p) {
            return Math.clamp(p, 0f, 1f);
        }
    }

    private static int animate(AnimatedDye dye) {
        DyeState s = DYE_STATES.computeIfAbsent(dye, DyeState::new);
        long now = System.nanoTime();
        // once a frame, the same stack gets asked several times in one
        if (s.lastAt != 0 && now - s.lastAt < 2_000_000L) return s.lastColor;
        float ticks = s.lastAt == 0 ? 0 : Math.min((now - s.lastAt) / 50_000_000f, 20f);
        s.lastAt = now;
        return s.interpolate(dye, ticks);
    }

    // skyblocker OkLabColor.interpolate, the game doesnt have it

    private static float linearize(float c) {
        return c <= 0.04045f ? c / 12.92f : (float) Math.pow((c + 0.055f) / 1.055f, 2.4f);
    }

    private static float delinearize(float c) {
        return c <= 0.0031308f ? c * 12.92f : (float) (1.055f * Math.pow(c, 1 / 2.4f) - 0.055f);
    }

    private static float[] lab(int rgb) {
        float r = linearize((rgb >> 16 & 255) / 255f), g = linearize((rgb >> 8 & 255) / 255f), b = linearize((rgb & 255) / 255f);
        float l = (float) Math.cbrt(0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b);
        float m = (float) Math.cbrt(0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b);
        float s = (float) Math.cbrt(0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b);
        return new float[]{0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * s, 1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s, 0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s};
    }

    private static int channel(float c) {
        return Math.clamp(Math.round(delinearize(c) * 255f), 0, 255);
    }

    static int interpolateOkLab(int from, int to, float t) {
        float[] a = lab(from), b = lab(to);
        float L = a[0] + (b[0] - a[0]) * t, A = a[1] + (b[1] - a[1]) * t, B = a[2] + (b[2] - a[2]) * t;
        float l = L + 0.3963377774f * A + 0.2158037573f * B, m = L - 0.1055613458f * A - 0.0638541728f * B, s = L - 0.0894841775f * A - 1.2914855480f * B;
        l = l * l * l;
        m = m * m * m;
        s = s * s * s;
        float r = 4.0767416621f * l - 3.3077115913f * m + 0.2309699292f * s, g = -1.2684380046f * l + 2.6097574011f * m - 0.3413193965f * s, bl = -0.0041960863f * l - 0.7034186147f * m + 1.7076147010f * s;
        return channel(r) << 16 | channel(g) << 8 | channel(bl);
    }

    // repo dyes, from skyblocker RepoDyeColors

    private static JsonObject repoDyes;

    private static JsonObject repoDyes() {
        if (repoDyes != null) return repoDyes;
        try (var in = dev.zmuzy.sbc.core.data.DataFiles.neu("dyes.json")) {
            repoDyes = in == null ? new JsonObject() : com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            repoDyes = new JsonObject();
        }
        return repoDyes;
    }

    // names of the dyes the game has, for the command and the screen
    public static List<String> repoDyeNames() {
        List<String> out = new ArrayList<>();
        for (String group : new String[]{"static", "animated"}) {
            if (repoDyes().has(group)) out.addAll(repoDyes().getAsJsonObject(group).keySet());
        }
        Collections.sort(out);
        return out;
    }

    private static int hex(String text) {
        return Integer.parseInt(text.trim().replace("#", ""), 16) & 0xFFFFFF;
    }

    // for DyeSelectPopup: a static dye is one color, an animated one is all its colors in equal time
    static AnimatedDye animatedOf(JsonArray colours) {
        List<Keyframe> keys = new ArrayList<>();
        int n = colours.size();
        for (int i = 0; i < n; i++) keys.add(new Keyframe(hex(colours.get(i).getAsString()), n == 1 ? 0 : (float) i / (n - 1)));
        return new AnimatedDye(keys, false, 0, 4f);
    }

    // changing a look

    // held items uuid, or null after saying whats wrong
    private static String heldUuid(FabricClientCommandSource src) {
        return heldUuid(src.getPlayer().getMainHandItem(), s -> src.sendError(Component.literal("§b[SBC] §c" + s)));
    }

    public static String heldUuid(ItemStack held, java.util.function.Consumer<String> say) {
        if (!GameState.inSkyBlock) {
            say.accept("You are not on SkyBlock.");
            return null;
        }
        if (!Features.on(ID)) {
            say.accept("Turn on Custom item looks in the settings first.");
            return null;
        }
        if (held.isEmpty()) {
            say.accept("Hold an item first.");
            return null;
        }
        String uuid = uuid(held);
        if (uuid.isEmpty()) say.accept("This item has no uuid, so a look would have nothing to hold on to.");
        return uuid.isEmpty() ? null : uuid;
    }

    private static int done(FabricClientCommandSource src, String text) {
        src.sendFeedback(Component.literal("§b[SBC] §f" + text));
        return 1;
    }

    // &amp; for color codes, like the chat rules take them
    public static void setName(String uuid, String text) {
        if (text == null || text.isBlank()) data().names.remove(uuid);
        else data().names.put(uuid, text.replace('&', '§'));
        changed();
    }

    public static void setTrim(String uuid, String material, String pattern) {
        if (material == null || pattern == null) data().trims.remove(uuid);
        else data().trims.put(uuid, new String[]{material, pattern});
        changed();
    }

    public static void setGlint(String uuid, Boolean glint) {
        if (glint == null) data().glint.remove(uuid);
        else data().glint.put(uuid, glint);
        changed();
    }

    public static void setModel(String uuid, String model) {
        if (model == null || model.isBlank()) data().models.remove(uuid);
        else data().models.put(uuid, model.trim());
        changed();
    }

    public static void setArmorModel(String uuid, String model) {
        if (model == null || model.isBlank()) data().armorModels.remove(uuid);
        else data().armorModels.put(uuid, model.trim());
        changed();
    }

    public static void setHelmet(String uuid, String textureOrItem) {
        if (textureOrItem == null || textureOrItem.isBlank()) data().helmets.remove(uuid);
        else data().helmets.put(uuid, texture(textureOrItem.trim()));
        changed();
    }

    // skyblock items head texture by id like CustomHelmetTextures lists them, otherwise the text is the texture
    static String texture(String textureOrItem) {
        String id = textureOrItem.toUpperCase(Locale.ROOT);
        ItemStack stack = id.matches("[A-Z0-9_:;-]+") ? ItemInfo.stack(id) : ItemStack.EMPTY;
        if (stack != null && !stack.isEmpty()) {
            String t = dev.zmuzy.sbc.dungeon.DungeonMarks.headTexture(stack);
            if (!t.isEmpty()) return t;
        }
        return textureOrItem;
    }

    public static void setDye(String uuid, Integer rgb) {
        data().animatedDyes.remove(uuid);
        if (rgb == null) data().dyes.remove(uuid);
        else data().dyes.put(uuid, rgb & 0xFFFFFF);
        changed();
    }

    public static void setAnimatedDye(String uuid, AnimatedDye dye) {
        data().dyes.remove(uuid);
        if (dye == null) data().animatedDyes.remove(uuid);
        else data().animatedDyes.put(uuid, dye);
        DYE_STATES.clear();
        changed();
    }

    // a games dye by name, false if none
    public static boolean setRepoDye(String uuid, String name) {
        JsonObject all = repoDyes();
        JsonElement one = all.has("static") ? all.getAsJsonObject("static").get(name) : null;
        if (one != null) {
            setDye(uuid, hex(one.getAsString()));
            return true;
        }
        JsonElement many = all.has("animated") ? all.getAsJsonObject("animated").get(name) : null;
        if (many == null || many.getAsJsonArray().isEmpty()) return false;
        setAnimatedDye(uuid, animatedOf(many.getAsJsonArray()));
        return true;
    }

    public static void clear(String uuid) {
        Data d = data();
        d.names.remove(uuid);
        d.dyes.remove(uuid);
        d.animatedDyes.remove(uuid);
        d.trims.remove(uuid);
        d.helmets.remove(uuid);
        d.glint.remove(uuid);
        d.models.remove(uuid);
        d.armorModels.remove(uuid);
        changed();
    }

    // the commands

    private static int openScreen() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.gui.setScreen(new dev.zmuzy.sbc.ui.CustomLooksScreen(mc.gui.screen())));
        return 1;
    }

    private static int renameItem(FabricClientCommandSource src, String text) {
        String uuid = heldUuid(src);
        if (uuid == null) return 0;
        setName(uuid, text);
        return done(src, "Named the held item, only you see it.");
    }

    private static int armorTrim(FabricClientCommandSource src, String material, String pattern) {
        ItemStack held = src.getPlayer().getMainHandItem();
        String uuid = heldUuid(src);
        if (uuid == null) return 0;
        if (!held.is(ItemTags.TRIMMABLE_ARMOR)) {
            src.sendError(Component.literal("§b[SBC] §cThis is not a piece of armor that takes a trim."));
            return 0;
        }
        if (material == null) {
            boolean had = data().trims.containsKey(uuid);
            setTrim(uuid, null, null);
            return done(src, had ? "Took the trim off." : "This item never had a trim.");
        }
        if (trim(material, pattern) == null) {
            src.sendError(Component.literal("§b[SBC] §cThere is no such material or pattern."));
            return 0;
        }
        setTrim(uuid, material, pattern);
        return done(src, "Trimmed the held item, only you see it.");
    }

    private static int animatedDye(FabricClientCommandSource src, Integer from, Integer to, float duration, boolean cycleBack, float delay) {
        ItemStack held = src.getPlayer().getMainHandItem();
        String uuid = heldUuid(src);
        if (uuid == null) return 0;
        if (!held.is(ItemTags.CAULDRON_CAN_REMOVE_DYE)) {
            src.sendError(Component.literal("§b[SBC] §cThis item cannot be dyed."));
            return 0;
        }
        if (from == null) {
            setAnimatedDye(uuid, null);
            return done(src, "Took the animated dye off.");
        }
        setAnimatedDye(uuid, new AnimatedDye(List.of(new Keyframe(from, 0), new Keyframe(to, 1)), cycleBack, delay, duration));
        return done(src, "Dyed the held item, only you see it.");
    }

    private static int dyeCommand(FabricClientCommandSource src, String what) {
        String uuid = heldUuid(src);
        if (uuid == null) return 0;
        if (what.equalsIgnoreCase("off")) {
            setDye(uuid, null);
            return done(src, "Took the dye off.");
        }
        if (setRepoDye(uuid, what.toUpperCase(Locale.ROOT))) return done(src, "Dyed the held item, only you see it.");
        try {
            if (what.replace("#", "").length() != 6) throw new NumberFormatException();
            setDye(uuid, hex(what));
            return done(src, "Dyed the held item, only you see it.");
        } catch (NumberFormatException e) {
            src.sendError(Component.literal("§b[SBC] §cUse six hex digits, a dye's name like DYE_ROSE, or off."));
            return 0;
        }
    }

    private static int textLook(FabricClientCommandSource src, String what, java.util.function.BiConsumer<String, String> set) {
        String uuid = heldUuid(src);
        if (uuid == null) return 0;
        set.accept(uuid, what.equalsIgnoreCase("off") ? null : what);
        return done(src, what.equalsIgnoreCase("off") ? "Took it off the held item." : "Set for the held item, only you see it.");
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> textCommand(String name, java.util.function.BiConsumer<String, String> set) {
        return literal(name).then(argument("what", StringArgumentType.greedyString()).executes(c -> textLook(c.getSource(), StringArgumentType.getString(c, "what"), set)));
    }

    public static void register() {
        Feature f = Features.add(new Feature(ID, "Items", "Custom item looks", "Names, armor trims, dyes, models, glint and head textures for your own items, by item. Only you see them. /sbc custom opens the screen for what you hold.", "Skyblocker: custom item names, armor trims, dyes, models, glint and helmet textures")
                .when(() -> GameState.inSkyBlock || dev.zmuzy.sbc.SbcMod.DEV).defaultOff());
        any = !data().empty();
        dev.zmuzy.sbc.core.Commands.describe("custom", "How the item you hold looks, only for you: /sbc custom, renameItem, armorTrim, animatedDye, dye, model, armormodel, glint, helmet, clear");
        Safe.join("CustomLooks.join", (handler, sender, client) -> TRIM_CACHE.clear());
        Safe.commands("CustomLooks.commands", (d, r) -> d.register(literal("sbc").then(literal("custom")
                .executes(c -> openScreen())
                .then(literal("renameItem")
                        .executes(c -> openScreen())
                        .then(argument("text", StringArgumentType.greedyString()).executes(c -> renameItem(c.getSource(), StringArgumentType.getString(c, "text")))))
                .then(literal("armorTrim")
                        .executes(c -> armorTrim(c.getSource(), null, null))
                        .then(argument("material", net.minecraft.commands.arguments.IdentifierArgument.id())
                                .suggests((c, b) -> c.getSource().suggestRegistryElements(Registries.TRIM_MATERIAL, net.minecraft.commands.SharedSuggestionProvider.ElementSuggestionType.ELEMENTS, b, c))
                                .then(argument("pattern", net.minecraft.commands.arguments.IdentifierArgument.id())
                                        .suggests((c, b) -> c.getSource().suggestRegistryElements(Registries.TRIM_PATTERN, net.minecraft.commands.SharedSuggestionProvider.ElementSuggestionType.ELEMENTS, b, c))
                                        .executes(c -> armorTrim(c.getSource(), c.getArgument("material", Identifier.class).toString(), c.getArgument("pattern", Identifier.class).toString())))))
                .then(literal("animatedDye")
                        .executes(c -> animatedDye(c.getSource(), null, null, 0, false, 0))
                        .then(argument("hex1", StringArgumentType.word())
                                .then(argument("hex2", StringArgumentType.word())
                                        .then(argument("duration", FloatArgumentType.floatArg(0.1f, 10f))
                                                .then(argument("cycleBack", BoolArgumentType.bool())
                                                        .executes(c -> animatedDye(c.getSource(), hex(StringArgumentType.getString(c, "hex1")), hex(StringArgumentType.getString(c, "hex2")), FloatArgumentType.getFloat(c, "duration"), BoolArgumentType.getBool(c, "cycleBack"), 0))
                                                        .then(argument("delay", FloatArgumentType.floatArg(0))
                                                                .executes(c -> animatedDye(c.getSource(), hex(StringArgumentType.getString(c, "hex1")), hex(StringArgumentType.getString(c, "hex2")), FloatArgumentType.getFloat(c, "duration"), BoolArgumentType.getBool(c, "cycleBack"), FloatArgumentType.getFloat(c, "delay")))))))))
                .then(literal("dye").then(argument("what", StringArgumentType.word()).suggests((c, b) -> {
                    for (String n : repoDyeNames()) if (n.toLowerCase(Locale.ROOT).startsWith(b.getRemainingLowerCase())) b.suggest(n);
                    return b.buildFuture();
                }).executes(c -> dyeCommand(c.getSource(), StringArgumentType.getString(c, "what")))))
                .then(textCommand("model", CustomLooks::setModel))
                .then(textCommand("armormodel", CustomLooks::setArmorModel))
                .then(textCommand("helmet", CustomLooks::setHelmet))
                .then(literal("glint").then(argument("what", StringArgumentType.word()).suggests((c, b) -> {
                    b.suggest("on");
                    b.suggest("off");
                    b.suggest("default");
                    return b.buildFuture();
                }).executes(c -> {
                    String uuid = heldUuid(c.getSource());
                    if (uuid == null) return 0;
                    String what = StringArgumentType.getString(c, "what");
                    setGlint(uuid, what.equalsIgnoreCase("default") ? null : Boolean.valueOf(what.equalsIgnoreCase("on")));
                    return done(c.getSource(), "Set the glint of the held item, only you see it.");
                })))
                .then(literal("clear").executes(c -> {
                    String uuid = heldUuid(c.getSource());
                    if (uuid == null) return 0;
                    clear(uuid);
                    return done(c.getSource(), "The held item looks as it does for everyone again.");
                })))));
    }

    // skyblocker SkyblockItemModels.updateItemModels: a model the repo renamed gets followed to the new name, one
    // it removed goes
    static String renamedModel(String model, java.util.Map<String, String> renamed) {
        String current = model;
        java.util.Set<String> visited = new java.util.HashSet<>();
        visited.add(current);
        while (renamed.containsKey(current)) {
            String next = renamed.get(current);
            if (!visited.add(next)) break;
            current = next;
        }
        return current;
    }
}
