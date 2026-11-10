// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of owo-lib; see THIRD_PARTY_NOTICES.md.
/*
 * Ported from owo-lib (https://github.com/wisp-forest/owo-lib), ui.core.PositionedRectangle, branch 26.2.
 * owo-lib is by glisco and contributors; its licence reads "Copyright (c) 2021" and names no holder. MIT licence, full text in licenses/owo-lib-LICENSE.txt.
 */
package io.github.zmuzy.sbc.ui.layout;

import org.joml.Matrix3x2f;
import org.joml.Vector2f;

public interface PositionedRectangle {

    int x();

    int y();

    int width();

    int height();

    default boolean isInBoundingBox(double x, double y) {
        return x >= this.x() && x < this.x() + this.width() && y >= this.y() && y < this.y() + this.height();
    }

    default PositionedRectangle transform(Matrix3x2f matrix) {
        var pos1 = matrix.transformPosition(x(), y(), new Vector2f());
        var pos2 = matrix.transformPosition(x() + width(), y() + height(), new Vector2f());

        return PositionedRectangle.of((int) pos1.x, (int) pos1.y, (int) (pos2.x - pos1.x), (int) (pos2.y - pos1.y));
    }

    static PositionedRectangle of(int x, int y, int width, int height) {
        return new PositionedRectangle() {
            @Override
            public int x() {
                return x;
            }

            @Override
            public int y() {
                return y;
            }

            @Override
            public int width() {
                return width;
            }

            @Override
            public int height() {
                return height;
            }
        };
    }
}
