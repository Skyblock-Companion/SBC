// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.mc;

// per entity slot for what we decided this tick (EntityTickCache), added to Entity by a mixin
// the game asks about every entity 9 times a frame, a field read beats 9 hash lookups
public interface EntitySlots {
    Object[] sbc$slots();

    void sbc$slots(Object[] slots);
}
