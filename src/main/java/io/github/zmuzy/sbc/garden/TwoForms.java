// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.garden;

import io.github.zmuzy.sbc.rift.RichCard;

import java.util.AbstractList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

// for a card whose compact form shows different lines than the open one
// a RichCard keeps what it was told for a bit and doesnt notice the layout changed in that time, so it gets
// both and picks the form every time lines are read
public final class TwoForms {
    private TwoForms() {}

    // build gets true for compact. band text is the open form's, both have the same one
    public static RichCard.Content of(BooleanSupplier compact, Function<Boolean, RichCard.Content> build) {
        RichCard.Content open = build.apply(false), brief = build.apply(true);
        return new RichCard.Content(open.right(), lines(compact, open.lines(), brief.lines()));
    }

    static <T> List<T> lines(BooleanSupplier compact, List<T> open, List<T> brief) {
        return new AbstractList<>() {
            @Override
            public T get(int index) {
                return (compact.getAsBoolean() ? brief : open).get(index);
            }

            @Override
            public int size() {
                return (compact.getAsBoolean() ? brief : open).size();
            }
        };
    }
}
