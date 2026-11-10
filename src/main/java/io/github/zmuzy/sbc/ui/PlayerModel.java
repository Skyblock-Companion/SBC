// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.ui;

import com.mojang.authlib.GameProfile;
import com.mojang.util.UndashedUuid;
import io.github.zmuzy.sbc.core.Profile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.component.ResolvableProfile;
import org.joml.Vector2f;

import java.util.List;
import java.util.UUID;

// the profiles player in their own skin + armor, looking at the mouse
final class PlayerModel {
    // api lists armor boots first
    private static final EquipmentSlot[] BY_INDEX = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};

    private static String uuid = "";
    private static volatile RemotePlayer remote;
    private static Profile.Data dressedFor;
    private static boolean broken;

    private PlayerModel() {}

    // false = nothing to draw yet, caller shows a head instead
    static boolean draw(Ui ui, Profile.Data d, int x, int y, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (broken || mc.level == null || d == null) return false;
        try {
            LivingEntity who = who(mc, d);
            if (who == null) return false;
            // drawn in real screen coords whatever the menu scale or scroll
            Vector2f a = ui.g.pose().transformPosition(x, y, new Vector2f()), b = ui.g.pose().transformPosition(x + w, y + h, new Vector2f());
            var window = mc.getWindow();
            float mx = (float) (mc.mouseHandler.xpos() * window.getGuiScaledWidth() / Math.max(1, window.getScreenWidth()));
            float my = (float) (mc.mouseHandler.ypos() * window.getGuiScaledHeight() / Math.max(1, window.getScreenHeight()));
            int size = Math.max(10, Math.round((b.y - a.y) * 0.42f));
            InventoryScreen.extractEntityInInventoryFollowsMouse(ui.g, Math.round(a.x), Math.round(a.y), Math.round(b.x), Math.round(b.y), size, 0.0625f, mx, my, who);
            return true;
        } catch (Throwable t) {
            broken = true;
            io.github.zmuzy.sbc.SbcMod.LOG.warn("Could not draw the player model", t);
            return false;
        }
    }

    private static LivingEntity who(Minecraft mc, Profile.Data d) {
        if (mc.player != null && mc.player.getUUID().toString().replace("-", "").equalsIgnoreCase(d.uuid)) return mc.player;
        if (d.uuid.length() != 32) return null;
        if (!d.uuid.equals(uuid)) {
            uuid = d.uuid;
            remote = null;
            dressedFor = null;
            String asked = d.uuid;
            UUID id = UndashedUuid.fromStringLenient(asked);
            mc.playerSkinRenderCache().lookup(ResolvableProfile.createUnresolved(id)).thenAccept(entry -> {
                if (entry.isPresent()) mc.execute(() -> {
                    if (asked.equals(uuid) && mc.level != null) remote = make(mc, entry.get().gameProfile());
                });
            });
        }
        RemotePlayer p = remote;
        if (p != null && dressedFor != d) {
            dress(p, d);
            dressedFor = d;
        }
        return p;
    }

    private static RemotePlayer make(Minecraft mc, GameProfile profile) {
        PlayerInfo info = new PlayerInfo(profile, false);
        RemotePlayer p = new RemotePlayer(mc.level, profile) {
            @Override
            public PlayerSkin getSkin() {
                return info.getSkin();
            }

            @Override
            public boolean isModelPartShown(PlayerModelPart part) {
                return part != PlayerModelPart.CAPE;
            }

            @Override
            public boolean isInvisibleTo(Player player) {
                return true;
            }
        };
        p.setCustomNameVisible(false);
        p.setId(-1);
        return p;
    }

    private static void dress(RemotePlayer p, Profile.Data d) {
        for (EquipmentSlot slot : BY_INDEX) p.setItemSlot(slot, ItemStack.EMPTY);
        List<Profile.Item> armor = d.inventories.get("Armor");
        if (armor == null) return;
        for (int i = 0; i < armor.size() && i < BY_INDEX.length; i++) {
            Profile.Item it = armor.get(i);
            if (it == null) continue;
            ItemStack stack = it.stack();
            Equippable equip = stack.get(DataComponents.EQUIPPABLE);
            p.setItemSlot(equip != null && equip.slot().isArmor() ? equip.slot() : BY_INDEX[i], stack);
        }
    }
}
