/*
 * Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PaintScope {
    private static final PaintScope current = new PaintScope();

    public static PaintScope current() {
        return current;
    }

    private boolean onTopRedraw;
    private Orientation orientation;

    public void setOnTopRedraw(boolean onTopRedraw) {
        this.onTopRedraw = onTopRedraw;
    }

    public void setOnTopRedraw(Orientation o) {
        setOnTopRedraw(true);
        setOrientation(o);
    }

    public enum Orientation {
        HORIZONTAL, VERTICAL
    }
}
