// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.feature;

import io.muzy.sbc.core.Config;
import io.muzy.sbc.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

// one switchable piece of the mod. on by default, only active where it makes sense (when), and can hook tick,
// chat, tooltips + the world. Features runs every hook guarded
public final class Feature {
    public final String id, module, title, description;
    // which mods feature this came from. just for us, no screen shows it (Credits has the licences)
    public final String replaces;
    // its settings, shown + saved by the settings screen
    public final List<Option> options = new java.util.ArrayList<>();
    // no on/off switch, it only carries settings
    public boolean alwaysOn;

    private BooleanSupplier getter;
    private Consumer<Boolean> setter;
    private BooleanSupplier context = () -> true;

    public Consumer<Minecraft> onTick;
    public Consumer<String> onChat;
    public BiConsumer<ItemStack, List<Component>> onTooltip;
    public Consumer<WorldDraw> onWorld;
    public Runnable onLeave;
    boolean wasActive;

    // health, Features fills it in
    public volatile boolean paused;
    public volatile String lastError = "";
    public int errors, slow;
    public long windowStart, calls, totalNs, maxNs;

    public Feature(String id, String module, String title, String description, String replaces) {
        this.id = id;
        this.module = module;
        this.title = title;
        this.description = description;
        this.replaces = replaces == null ? "" : replaces;
    }

    // off till you turn it on
    public boolean defaultOff;

    public Feature defaultOff() {
        defaultOff = true;
        return this;
    }

    public Feature alwaysOn() {
        alwaysOn = true;
        return this;
    }

    // adds a setting, keyed "featureId.key"
    public Feature option(Option o) {
        options.add(o);
        return this;
    }

    public Option option(String key) {
        String full = id + "." + key;
        for (Option o : options) if (o.key.equals(full) || o.key.equals(key)) return o;
        return null;
    }

    // keeps on/off in an existing Config field instead of the generic feature map
    public Feature bind(BooleanSupplier get, Consumer<Boolean> set) {
        getter = get;
        setter = set;
        return this;
    }

    // where it makes sense. outside it the feature idles without being switched off
    public Feature when(BooleanSupplier ctx) {
        context = ctx;
        return this;
    }

    public Feature tick(Consumer<Minecraft> f) {
        onTick = f;
        return this;
    }

    public Feature chat(Consumer<String> f) {
        onChat = f;
        return this;
    }

    public Feature tooltip(BiConsumer<ItemStack, List<Component>> f) {
        onTooltip = f;
        return this;
    }

    public Feature world(Consumer<WorldDraw> f) {
        onWorld = f;
        return this;
    }

    // runs once when it stops being active: you left the place in when(), it got switched off or paused, or the
    // world changed while it was on. tick + chat handlers stop till its active again
    public Feature left(Runnable f) {
        onLeave = f;
        return this;
    }

    // your switch (default on)
    private long enabledEpoch = -1;
    private boolean enabledSeen;

    public boolean enabled() {
        long e = Features.epoch;
        if (e != 0 && e == enabledEpoch) return enabledSeen;
        boolean v = enabledNow();
        enabledSeen = v;
        enabledEpoch = e;
        return v;
    }

    private boolean enabledNow() {
        if (io.muzy.sbc.core.data.RemoteSwitch.isOff(id)) return false;
        if (alwaysOn) return true;
        if (getter != null) return getter.getAsBoolean();
        return Config.I.features.getOrDefault(id, !defaultOff);
    }

    // the switch lives in a field of its own, so the settings cannot tell a stored choice from the default
    public boolean ownField() {
        return getter != null;
    }

    public void setEnabled(boolean v) {
        if (io.muzy.sbc.core.data.RemoteSwitch.keepChoice(id, v)) {
            // held off remotely: the choice gets stored and comes back with the field
        } else if (setter != null) setter.accept(v);
        else Config.I.features.put(id, v);
        if (v) resume();
        Features.changed();
        Config.save();
    }

    // its switch lives in a Config field other code might read directly
    public boolean bound() {
        return getter != null;
    }

    // bound field as is, whatever the remote switch says
    public boolean field() {
        return getter != null && getter.getAsBoolean();
    }

    // writes the bound field and nothing else
    public void field(boolean v) {
        if (setter != null) setter.accept(v);
    }

    // on, not paused after errors, and in its place
    // asked thousands of times a frame so its worked out once a tick (Features.epoch). 0 = not ticking yet (tests)
    private long seenEpoch = -1;
    private boolean seenActive;

    public boolean active() {
        long e = Features.epoch;
        if (e != 0 && e == seenEpoch) return seenActive;
        boolean a = work();
        seenActive = a;
        seenEpoch = e;
        return a;
    }

    private boolean work() {
        if (paused || !enabled()) return false;
        try {
            return context.getAsBoolean();
        } catch (Throwable t) {
            return false;
        }
    }

    public void resume() {
        Features.changed();
        paused = false;
        errors = 0;
        slow = 0;
        lastError = "";
    }

    // avg ms per call, for the settings page
    public double avgMs() {
        return calls == 0 ? 0 : totalNs / (double) calls / 1e6;
    }
}
