package one.chartsy.kernel;

import one.chartsy.Symbol;
import one.chartsy.data.CandleSeries;
import one.chartsy.misc.StyledValue;

import java.awt.*;
import java.text.Format;

/**
 * The exploration code. A user extends this class to provide custom exploration
 * formula for scanning stock universe or any other types of assets.
 * <p>
 * The exploration is called by the Chartsy framework when a new exploration is
 * started from the GUI application or by using a remote interface.
 * <p>
 * <i><b>Note:</b> The exploration is started in a new, separate {@link Thread}.
 * If you are interacting with the UI components from your exploration code, you
 * <b>must</b> do it using {@link EventQueue#invokeLater(Runnable)} to ensure
 * that all modifications to the UI occur always in the
 * <a href="https://stackoverflow.com/a/7217210/2300469">Event Dispatch Thread
 * (EDT)</a>.
 *
 * @author Mariusz Bernacki
 *
 */
public abstract class Exploration {

    private ExplorationFragment.Builder resultFragment;

    /**
     * Allows to programmatically exclude a symbol from the exploration.
     *
     * @param symbol
     *            the candidate symbol for exploration
     * @return {@code false} if the user wants to exclude this symbol from the
     *         exploration, or {@code true} (default value) otherwise
     */
    public boolean filter(Symbol symbol) {
        return true;
    }

    /**
     * Allows to programmatically exclude a symbol from the exploration.
     *
     * @param symbol
     *            the candidate symbol for exploration
     * @param series
     *            the symbol's historical data
     * @return {@code false} if the user wants to exclude symbol from the
     *         exploration, or {@code true} (default value) otherwise
     */
    public boolean filter(Symbol symbol, CandleSeries series) {
        return true;
    }

    /**
     * The entry point of custom exploration code, called for each symbol.
     * <p>
     * This method is called multiple times for each symbol selected for
     * exploration. For example if running exploration against 100 symbols, this
     * method will get called 100 times.
     * <p>
     * Use the provided {@code symbol} object and {@code quotes} series to calculate
     * any market attributes you are interested in and add them to the result sheet
     * using any of the exposed {@code addColumn(...)} methods.
     *
     * @param symbol
     *            the symbol explored
     * @param series
     *            the symbol historical data
     */
    public abstract void explore(Symbol symbol, CandleSeries series);

    /**
     * Adds the value to the exploration result.
     *
     * @param columnName
     *            the column name used
     * @param value
     *            the value to add
     * @see #addColumn(String, Object, Format)
     */
    protected void addColumn(String columnName, Object value) {
        addColumn(columnName, value, null, null, null);
    }

    /**
     * Adds the value to the exploration result with the specified background color
     * and/or formatting. The provided {@code format} (if not null) will be used to
     * display the {@code value} in the result list. The {@code bgColor} (if
     * provided) will be used as a background color of the cell where the value is
     * displayed.
     *
     * @param columnName
     *            the column name used
     * @param value
     *            the value to add
     * @param bgColor
     *            the background color of the cell displaying the value, may be
     *            {@code null}
     * @see #addColumn(String, Object, Color, Color)
     */
    protected void addColumn(String columnName, Object value, Color bgColor) {
        addColumn(columnName, value, bgColor, null);
    }

    /**
     * Adds the value to the exploration result with the specified formatting and
     * coloring options. The provided {@code format} (if not null) will be used to
     * display the {@code value} in the result list. The {@code bgColor} (if
     * provided) will be used as a background color of the cell where the value is
     * displayed. The {@code fgColor} will be used as a foreground color of the
     * cell.
     *
     * @param columnName
     *            the column name used
     * @param value
     *            the value to add
     * @param bgColor
     *            the background color of the cell displaying the value, may be
     *            {@code null}
     * @param fgColor
     *            the foreground color of the cell displaying the value, may be
     *            {@code null}
     */
    protected void addColumn(String columnName, Object value, Color bgColor, Color fgColor) {
        addColumn(columnName, value, null, bgColor, fgColor);
    }

    /**
     * Adds the value to the exploration result using the specified formatting. The
     * provided {@code format} (if not null) will be used to format and display the
     * {@code value} in the result sheet. Example formats include:
     * <table>
     * <tr>
     * <td style="text-align:right">{@code new DecimalFormat("#.##")}</td>
     * <td>Displays numbers with 2 decimal places.</td>
     * </tr>
     * <tr>
     * <td style="text-align:right">{@code new DecimalFormat("#.##%")}</td>
     * <td>Displays numbers as a percent value with 2 decimal places.</td>
     * </tr>
     * <tr>
     * <td style="text-align:right">{@code new SimpleDateFormat("yyyy-MM-dd")}</td>
     * <td>Displays {@code Date} dates in ISO 8601 format (YYYY-MM-DD, e.g.: 2018-01-21).</td>
     * </tr>
     * <tr>
     * <td style="text-align:right">{@code DateTimeFormatter.ISO_DATE.toFormat()}</td>
     * <td>Displays {@code LocalDate} or {@code LocalDateTime} objects in ISO 8601 format.</td>
     * </tr>
     * </table>
     *
     * @param columnName
     *            the column name used
     * @param value
     *            the value to add
     * @param format
     *            the format used to format and display the value, may be
     *            {@code null}
     * @see #addColumn(String, Object, Format, Color)
     */
    protected void addColumn(String columnName, Object value, Format format) {
        addColumn(columnName, value, format, null, null);
    }

    /**
     * Adds the value to the exploration result with the specified background color
     * and/or formatting. The provided {@code format} (if not null) will be used to
     * display the {@code value} in the result list. The {@code bgColor} (if
     * provided) will be used as a background color of the cell where the value is
     * displayed.
     *
     * @param columnName
     *            the column name used
     * @param value
     *            the value to add
     * @param format
     *            the format used to display the value, may be {@code null}
     * @param bgColor
     *            the background color of the cell displaying the value, may be
     *            {@code null}
     * @see #addColumn(String, Object, Format, Color, Color)
     */
    protected void addColumn(String columnName, Object value, Format format, Color bgColor) {
        addColumn(columnName, value, format, bgColor, null);
    }

    /**
     * Adds the value to the exploration result with the specified formatting and
     * coloring options. The provided {@code format} (if not null) will be used to
     * display the {@code value} in the result list. The {@code bgColor} (if
     * provided) will be used as a background color of the cell where the value is
     * displayed. The {@code fgColor} will be used as a foreground color of the
     * cell.
     *
     * @param columnName
     *            the column name used
     * @param value
     *            the value to add
     * @param format
     *            the format used to display the value, may be {@code null}
     * @param bgColor
     *            the background color of the cell displaying the value, may be
     *            {@code null}
     * @param fgColor
     *            the foreground color of the cell displaying the value, may be
     *            {@code null}
     */
    protected void addColumn(String columnName, Object value, Format format, Color bgColor, Color fgColor) {
        getResultFragment().addColumn(columnName, StyledValue.of(value, format, bgColor, fgColor));
    }

    protected ExplorationFragment.Builder getResultFragment() {
        return resultFragment;
    }

    /**
     * Advances running exploration to the next {@code symbol}.
     *
     * @param symbol the next symbol to advance to
     * @return the newly created exploration fragment for next row
     */
    public ExplorationFragment.Builder addResultFragment(Symbol symbol) {
        return resultFragment = ExplorationFragment.builder(symbol);
    }
}
