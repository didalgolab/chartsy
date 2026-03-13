package one.chartsy.charting.financial;

import java.awt.Color;

/**
 * Defines the color used by the Stock demo.
 */
public interface StockColors {
  Color[] DEFAULT_COLORS = new Color[] {
      new Color(85, 150, 220), // blue
      new Color(60, 160, 80),  // green
      new Color(220, 80, 60),  // red
      new Color(200, 160, 60), // orange
      new Color(150, 120, 200),
      new Color(120, 180, 235),
      new Color(100, 180, 150),
      new Color(220, 150, 200),
      new Color(160, 170, 190), };

  Color FOREGROUND = Color.black;
  Color BACKGROUND = Color.white;
  Color HEADER_COLOR = new Color(230, 230, 230);
  Color STRIPES_COLOR = new Color(240, 240, 240);
  Color GRID_COLOR = new Color(200, 200, 200);
  Color PRIMARY_COLOR = new Color(64, 64, 64);

  // --------------------------------------------------------------------------
  // - Indicator colors.
  Color RSI_COLOR = new Color(33, 150, 243);
    Color RSI_INDIC_COLOR = Color.darkGray;
    Color RSI_PATTERN_COLOR = new Color(200, 224, 244);

    Color WILLIAMSR_COLOR = new Color(255, 193, 7);
    Color WILLIAMSR_INDIC_COLOR = Color.darkGray;

    Color STOCHASTIC_COLOR1 = new Color(76, 175, 80);
    Color STOCHASTIC_COLOR2 = new Color(244, 67, 54);
    Color STOCHASTIC_INDIC_COLOR = Color.darkGray;

    Color BOLLINGER_BANDS_COLOR = new Color(33, 150, 243);
    Color PRICE_CHANNEL_COLOR = new Color(76, 175, 80);
  Color VOLUME_COLOR = new Color(255, 165, 0);

    Color MACD_COLOR = new Color(0, 188, 212);
    Color MACD_SIGNAL_COLOR = new Color(76, 175, 80);
    Color MACD_DIV_COLOR = Color.darkGray;
}


