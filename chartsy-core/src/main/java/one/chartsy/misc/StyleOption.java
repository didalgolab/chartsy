/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.misc;

import java.awt.*;

/**
 * A style option associated with the {@code StyledValue}.
 */
public interface StyleOption<T> {

    String name();

    Class<T> type();

    record Of<T>(String name, Class<T> type) implements StyleOption<T> { }

    StyleOption<Color> BACKGROUND = new Of<>("Background", Color.class);
    StyleOption<Color> FOREGROUND = new Of<>("Foreground", Color.class);
}
