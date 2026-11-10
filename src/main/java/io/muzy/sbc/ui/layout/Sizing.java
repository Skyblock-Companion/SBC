// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of owo-lib; see THIRD_PARTY_NOTICES.md.
/*
 * Ported from owo-lib (https://github.com/wisp-forest/owo-lib), ui.core.Sizing, branch 26.2.
 * owo-lib is by glisco and contributors; its licence reads "Copyright (c) 2021" and names no holder. MIT licence, full text in licenses/owo-lib-LICENSE.txt.
 */
package io.muzy.sbc.ui.layout;

import java.util.Objects;
import java.util.function.Function;

public class Sizing {

    private static final Sizing CONTENT_SIZING = new Sizing(0, Method.CONTENT);

    public final Method method;
    public final int value;

    private Sizing(int value, Method method) {
        this.method = method;
        this.value = value;
    }

    // inflate into the space. contentSizeFunction sets the size from the content
    public int inflate(int space, Function<Sizing, Integer> contentSizeFunction) {
        return switch (this.method) {
            case FIXED -> this.value;
            case FILL, EXPAND -> Math.round((this.value / 100f) * space);
            case CONTENT -> contentSizeFunction.apply(this) + this.value * 2;
        };
    }

    public static Sizing fixed(int value) {
        return new Sizing(value, Method.FIXED);
    }

    // size by content, no padding
    public static Sizing content() {
        return CONTENT_SIZING;
    }

    // size by content + this padding
    public static Sizing content(int padding) {
        return new Sizing(padding, Method.CONTENT);
    }

    // fill the space
    public static Sizing fill() {
        return fill(100);
    }

    // fill this % of the space
    public static Sizing fill(int percent) {
        return new Sizing(percent, Method.FILL);
    }

    // fill whats left after siblings that dont expand
    public static Sizing expand() {
        return expand(100);
    }

    // this % of whats left after siblings that dont expand
    public static Sizing expand(int percent) {
        return new Sizing(percent, Method.EXPAND);
    }

    public boolean isContent() {
        return this.method == Method.CONTENT;
    }

    public boolean isExpand() {
        return this.method == Method.EXPAND;
    }

    public float contentFactor() {
        return this.isContent() ? 1f : 0f;
    }

    public enum Method {
        FIXED, CONTENT, FILL, EXPAND
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Sizing sizing = (Sizing) o;
        return value == sizing.value && method == sizing.method;
    }

    @Override
    public int hashCode() {
        return Objects.hash(method, value);
    }
}
