/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart.components;

import net.miginfocom.swing.MigLayout;
import one.chartsy.SymbolIdentity;
import one.chartsy.core.text.SplittedString;
import one.chartsy.core.text.StringSplitter;
import one.chartsy.core.text.StringSplitters;
import one.chartsy.data.provider.DataProvider;
import one.chartsy.financial.IdentityType;
import one.chartsy.ui.common.TitledSeparator;
import one.chartsy.ui.common.WatermarkLayer;
import org.openide.util.ImageUtilities;
import org.openide.util.NbBundle;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;

public class SymbolSelectorArea extends JPanel {
    private static final String WATERMARK_RESOURCE = "one/chartsy/ui/common/resources/animal-bull.png";

    private final ResourceBundle rb = NbBundle.getBundle(SymbolSelectorArea.class);
    private StringSplitter stringSplitter = StringSplitters.create();
    private TitledSeparator caption;
    private JTextArea textComponent;
    private SplittedString text = SplittedString.empty();
    private SymbolAutoCompleter autocompleter;

    public SymbolSelectorArea() {
        super(new MigLayout());
        initComponents();
    }

    public StringSplitter getStringSplitter() {
        return stringSplitter;
    }

    public JTextComponent getTextComponent() {
        return textComponent;
    }

    public String getText() {
        return textComponent.getText();
    }

    public SplittedString getSplittedText() {
        return getStringSplitter().split(getText());
    }

    public List<SymbolIdentity> getSelectedSymbols(IdentityType type) {
        return getSplittedText().fragments()
                .map(frag -> SymbolIdentity.of(frag.toString(), type))
                .toList();
    }

    protected void setSplittedText(SplittedString text) {
        Objects.requireNonNull(text, "text");
        this.text = text;
    }

    public AutoCompleter<?> getAutocompleter() {
        return autocompleter;
    }

    protected void initComponents() {
        caption = new TitledSeparator(rb.getString("SymbolSelector.symbols"));
        textComponent = new JTextArea();

        var img = ImageUtilities.loadImageIcon(WATERMARK_RESOURCE, false);
        int padx = img.getIconWidth(), pady = 30;
        textComponent.setPreferredSize(new Dimension(img.getIconWidth() + padx, img.getIconHeight() + pady));
        var watermarkedTextArea = new JLayer<>(textComponent, new WatermarkLayer<>(img));
        add(caption, "wrap,growx");
        add(watermarkedTextArea);
        var eventHandler = createEventHandler();
        textComponent.getDocument().addDocumentListener(eventHandler);

        autocompleter = new SymbolAutoCompleter(textComponent, stringSplitter);
    }

    public DataProvider getDataProvider() {
        return autocompleter.getDataProvider();
    }

    public void setDataProvider(DataProvider provider) {
        autocompleter.setDataProvider(provider);
    }

    protected EventHandler createEventHandler() {
        return new EventHandler();
    }


    protected class EventHandler implements DocumentListener {
        private String captionText = rb.getString("SymbolSelector.symbols");

        @Override
        public void insertUpdate(DocumentEvent e) {
            maybeChangeSymbols();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            maybeChangeSymbols();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            maybeChangeSymbols();
        }

        protected void maybeChangeSymbols() {
            var text = stringSplitter.split(textComponent.getText());
            setSplittedText(text);
            int symbolCount = text.getFragmentCount();
            if (symbolCount > 1)
                caption.setText(captionText + " (" + symbolCount + ")");
            else
                caption.setText(captionText);
        }
    }
}
