// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package one.chartsy.ui.common;

import javax.accessibility.AccessibleContext;
import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

public class TitledSeparator extends JPanel {
    public static final int TOP_INSET = 7;
    public static final int BOTTOM_INSET = 5;
    public static final int SEPARATOR_LEFT_INSET = 6;
    public static final int SEPARATOR_RIGHT_INSET = 0;

    private static final Color ENABLED_SEPARATOR_FOREGROUND = ColorOptions.named("Group.separatorColor", ColorOptions.Gray.of(205), ColorOptions.Gray.of(81));
    private static final Color DISABLED_SEPARATOR_FOREGROUND = ColorOptions.named("Group.disabledSeparatorColor", ENABLED_SEPARATOR_FOREGROUND);

    public static Border createEmptyBorder() {
        return BorderFactory.createEmptyBorder(TOP_INSET, 0, BOTTOM_INSET, 0);
    }

    protected final JLabel label = new JLabel();
    protected final JSeparator separator = new JSeparator(SwingConstants.HORIZONTAL);
    private String text;

    public TitledSeparator() {
        this("");
    }

    public TitledSeparator(String text) {
        this(text, null);
    }

    public TitledSeparator(String text, JComponent labelFor) {
        separator.setForeground(ENABLED_SEPARATOR_FOREGROUND);

        setLayout(new GridBagLayout());
        add(label, new GridBagConstraints(0, 0, 1, 1, 0, 1, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0,0,0,0), 0, 0));
        add(separator,
                new GridBagConstraints(1, 0, GridBagConstraints.REMAINDER, 1, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
                        new Insets(2, SEPARATOR_LEFT_INSET, 0, SEPARATOR_RIGHT_INSET), 0, 0));
        setBorder(createEmptyBorder());
        setText(text);
        setLabelFor(labelFor);
        setOpaque(false);
        updateLabelFont();
    }

    @Override
    public void updateUI() {
        super.updateUI();
        updateLabelFont();
    }

    private void updateLabelFont() {
        if (label != null) {
            //Font labelFont = StartupUiUtil.getLabelFont();
            //myLabel.setFont(RelativeFont.NORMAL.fromResource("TitledSeparator.fontSizeOffset", 0).derive(labelFont));
        }
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
        label.setText(text);
    }
    public void setTitleFont(Font font) {
        label.setFont(font);
    }

    public Font getTitleFont() {
        return label.getFont();
    }

    public JLabel getLabel() {
        return label;
    }

    public JSeparator getSeparator() {
        return separator;
    }


    public Component getLabelFor() {
        return label.getLabelFor();
    }

    public void setLabelFor(Component labelFor) {
        label.setLabelFor(labelFor);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        label.setEnabled(enabled);
        separator.setEnabled(enabled);

        separator.setForeground(enabled ? ENABLED_SEPARATOR_FOREGROUND : DISABLED_SEPARATOR_FOREGROUND);
    }

    @Override
    public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = super.getAccessibleContext();
            accessibleContext.setAccessibleName(label.getText());
        }
        return accessibleContext;
    }
}
