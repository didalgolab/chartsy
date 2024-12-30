// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package one.chartsy.ui.common;

import java.awt.*;

public class ColorOptions {

    public static boolean isDarkMode() {
        return false;
    }

    public static Color named(String name, Color regular, Color dark) {
        return isDarkMode()? dark: regular;
    }

    public static Color named(String name, Color color) {
        return color;
    }

    public static final class Gray extends Color {

        private Gray(int num) {
            super(num, num, num);
        }

        private Gray(int num, int alpha) {
            super(num, num, num, alpha);
        }

        public Color withAlpha(int alpha) {
            if (alpha == 0)
                return TRANSPARENT;
            if (alpha < 0 || 255 < alpha)
                throw new IllegalArgumentException("Alpha " + alpha + "is incorrect. Alpha should be in range 0..255");
            return new Gray(getRed(), alpha);
        }

        public static Gray of(int gray) {
            if (gray < 0 || 255 < gray)
                throw new IllegalArgumentException("Gray == " + gray +"Gray should be in range 0..255");
            var cached = cache[gray];
            if (cached == null)
                cached = cache[gray] = new Gray(gray);
            return cached;
        }

        public static Color of(int gray, int alpha) {
            return of(gray).withAlpha(alpha);
        }

        private static final Gray[] cache = new Gray[256];

        public static final Color TRANSPARENT = new Color(0,0,0,0);

    }
}
