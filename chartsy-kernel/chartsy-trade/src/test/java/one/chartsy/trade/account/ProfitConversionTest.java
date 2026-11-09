package one.chartsy.trade.account;

import one.chartsy.SymbolIdentity;
import one.chartsy.trade.Order;

import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProfitConversionTest {
    private static final SymbolIdentity USDCHF = SymbolIdentity.of("USDCHF");

    @Test void base_currency_marks_at_current_price_but_settled_cash_keeps_its_exit_conversion() {
        var balance = new AccountBalanceEntry(10000, ProfitConversion.BASE_CURRENCY);
        balance.enterLong(USDCHF, 1000, .9, 1);
        balance.updateMarketData(USDCHF, 1, 2);
        assertEquals(100, balance.getUnrealizedPnL(), 1e-10);
        balance.updateMarketData(USDCHF, .8, 3);
        assertEquals(-125, balance.getUnrealizedPnL(), 1e-10);
        balance.exitPosition(USDCHF, .85, 4);
        assertEquals(-50 / .85, balance.getRealizedPnL(), 1e-10);
        balance.updateMarketData(USDCHF, 1.2, 5);
        assertEquals(10000 - 50 / .85, balance.getCashBalance(), 1e-10);
        assertEquals(0, balance.getUnrealizedPnL(), 1e-10);
        balance.enterShort(USDCHF, 1000, .9, 6);
        balance.exitPosition(USDCHF, .8, 7);
        assertEquals(-50 / .85 + 125, balance.getRealizedPnL(), 1e-10);
    }

    @Test void scale_out_converts_closed_units_at_the_exit_and_remaining_units_at_later_marks() {
        var balance = new AccountBalanceEntry(10000, ProfitConversion.BASE_CURRENCY);
        balance.enterLong(USDCHF, 1000, .9, 1);
        balance.enterLong(USDCHF, 600, .8, 2);
        assertEquals(-50, balance.getRealizedPnL(), 1e-10);
        assertEquals(-75, balance.getUnrealizedPnL(), 1e-10);
        balance.updateMarketData(USDCHF, 1, 3);
        assertEquals(60, balance.getUnrealizedPnL(), 1e-10);
        assertEquals(10010, balance.getEquity(), 1e-10);
        balance.enterShort(USDCHF, 200, 1, 4);
        assertEquals(10, balance.getRealizedPnL(), 1e-10);
        assertEquals(0, balance.getUnrealizedPnL(), 1e-10);
        balance.updateMarketData(USDCHF, .8, 5);
        assertEquals(50, balance.getUnrealizedPnL(), 1e-10);
    }

    @Test void quote_currency_default_is_unchanged_and_inverse_requires_a_valid_price() {
        var original = new AccountBalanceEntry(10000);
        var explicit = new AccountBalanceEntry(10000, ProfitConversion.QUOTE_CURRENCY);
        for (var balance : new AccountBalanceEntry[]{original, explicit}) {
            balance.enterShort(USDCHF, 1234, .91, 1);
            balance.updateMarketData(USDCHF, .89, 2);
            balance.enterShort(USDCHF, 432, .92, 3);
            balance.exitPosition(USDCHF, .93, 4);
        }
        assertEquals(original.getEquity(), explicit.getEquity());
        assertEquals(original.getRealizedPnL(), explicit.getRealizedPnL());
        assertThrows(IllegalArgumentException.class, () -> ProfitConversion.BASE_CURRENCY.toAccountCurrency(USDCHF, 1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> ProfitConversion.BASE_CURRENCY.toAccountCurrency(USDCHF, 1, Double.NaN, 1));
    }

    @Test void failed_conversion_does_not_publish_an_open_position() {
        var balance = new AccountBalanceEntry(10000, ProfitConversion.BASE_CURRENCY);
        int[] notifications = {0};
        balance.addPositionValueChangeListener(position -> notifications[0]++);
        assertThrows(IllegalArgumentException.class, () -> balance.enterLong(USDCHF, 1000, 0, 1));
        assertNull(balance.getPosition(USDCHF));
        assertEquals(10000, balance.getEquity());
        assertEquals(0, notifications[0]);
    }

    @Test void failed_marks_closes_and_resizes_preserve_account_and_position_state() {
        List<Consumer<AccountBalanceEntry>> actions = List.of(
                balance -> balance.updateMarketData(USDCHF, 0, 3),
                balance -> balance.exitPosition(USDCHF, 0, 3),
                balance -> balance.enterLong(USDCHF, 2000, 0, 3),
                balance -> balance.enterLong(USDCHF, 600, 0, 3),
                balance -> balance.enterShort(USDCHF, 200, 0, 3));
        for (var action : actions) {
            var balance = markedLong(ProfitConversion.BASE_CURRENCY);
            Object[] before = snapshot(balance);
            int[] notifications = {0};
            balance.addPositionValueChangeListener(position -> notifications[0]++);
            assertThrows(IllegalArgumentException.class, () -> action.accept(balance));
            assertArrayEquals(before, snapshot(balance));
            assertEquals(0, notifications[0]);
        }
    }

    @Test void nonfinite_conversion_results_are_rejected_before_changing_the_ledger() {
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            var balance = markedLong((symbol, profit, price, time) -> time == 3 ? invalid : profit / price);
            Object[] before = snapshot(balance);
            assertThrows(ArithmeticException.class, () -> balance.updateMarketData(USDCHF, .8, 3));
            assertArrayEquals(before, snapshot(balance));
            assertThrows(ArithmeticException.class, () -> balance.exitPosition(USDCHF, .8, 3));
            assertArrayEquals(before, snapshot(balance));
        }
    }

    @Test void scale_out_prepares_the_remaining_conversion_before_settling_any_closed_units() {
        var balance = markedLong((symbol, profit, price, time) -> {
            if (time == 3 && Math.abs(profit + 60) < 1E-8)
                throw new IllegalStateException("No conversion rate for the remaining position");
            return profit / price;
        });
        Object[] before = snapshot(balance);
        assertThrows(IllegalStateException.class, () -> balance.enterLong(USDCHF, 600, .8, 3));
        assertArrayEquals(before, snapshot(balance));
    }

    @Test void reversal_prepares_both_conversions_before_closing_the_existing_position() {
        List<Consumer<AccountBalanceEntry>> actions = List.of(
                balance -> balance.enterShort(USDCHF, 200, .8, 3),
                balance -> balance.onOrderFill(new Order.Filled(3, "reverse", "broker", "strategy", "execution",
                        USDCHF, Order.Side.SELL_SHORT, 1200, .8, 1200, .8)));
        for (var action : actions) {
            var balance = markedLong((symbol, profit, price, time) -> {
                if (time == 3 && profit == 0)
                    throw new IllegalStateException("Opening conversion unavailable");
                return profit / price;
            });
            Object[] before = snapshot(balance);
            assertThrows(IllegalStateException.class, () -> action.accept(balance));
            assertArrayEquals(before, snapshot(balance));
        }
    }

    @Test void base_currency_scale_in_and_partial_fill_keep_quantity_weighted_entry_prices() {
        var balance = markedLong(ProfitConversion.BASE_CURRENCY);
        balance.onOrderFill(new Order.PartiallyFilled(500, new Order.Filled(3, "scale", "broker", "strategy", "execution",
                USDCHF, Order.Side.BUY, 500, .8, 500, .8)));
        double average = 1000.0 / 1500 * .9 + (1 - 1000.0 / 1500) * .8;
        assertEquals(1500, balance.getPosition(USDCHF).getQuantity());
        assertEquals(average, balance.getPosition(USDCHF).getAveragePrice());
        assertEquals((.8 - average) * 1500 / .8, balance.getUnrealizedPnL(), 1E-10);
        assertEquals(0, balance.getRealizedPnL());
        balance.updateMarketData(USDCHF, 1, 4);
        assertEquals((1 - average) * 1500, balance.getUnrealizedPnL(), 1E-10);
    }

    private static AccountBalanceEntry markedLong(ProfitConversion conversion) {
        var balance = new AccountBalanceEntry(10000, conversion);
        balance.enterLong(USDCHF, 1000, .9, 1);
        balance.updateMarketData(USDCHF, 1, 2);
        return balance;
    }

    private static Object[] snapshot(AccountBalanceEntry balance) {
        var position = balance.getPosition(USDCHF);
        return new Object[]{balance.getCashBalance(), balance.getRealizedPnL(), balance.getUnrealizedPnL(), balance.getEquity(),
                position, position.getDirection(), position.getQuantity(), position.getAveragePrice(), position.getMarketPrice(),
                position.getMarketTime(), position.getProfit(), position.getMaxFavorableExcursion(), position.getMaxAdverseExcursion(), position.isClosed()};
    }
}
