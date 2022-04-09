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
