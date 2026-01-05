package one.chartsy.financial.indicators;

import com.google.gson.Gson;
import one.chartsy.Candle;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HaarBreakoutDistanceTest {
    private static final int WINDOW = HaarBreakoutDistance.WINDOW;

    @Test
    void distance_is_zero_for_each_reference_window() throws IOException {
        List<CoefficientsRecord> records = loadRecords();
        assertFalse(records.isEmpty(), "No records found in HaarBreakoutDistance.jsonl");

        for (CoefficientsRecord record : records) {
            assertNotNull(record.closes, "Missing closes for " + record.symbol + "@" + record.date);
            assertEquals(WINDOW, record.closes.length, "Unexpected closes length for " + record.symbol + "@" + record.date);

            HaarBreakoutDistance indicator = new HaarBreakoutDistance();
            long time = 1L;

            // Add a prefix to exercise ring-buffer rotation; the last WINDOW bars must match record.closes
            for (int i = 0; i < 13; i++) {
                indicator.accept(Candle.of(time++, 0.0));
            }
            for (double close : record.closes) {
                indicator.accept(Candle.of(time++, close));
            }

            assertTrue(indicator.isReady(), "Indicator should be ready for " + record.symbol + "@" + record.date);
            assertEquals(0.0, indicator.getLast(), 1e-9, "Distance should be 0 for " + record.symbol + "@" + record.date);
            assertEquals(1.0, indicator.getPearson(), 1e-9, "Pearson should be 1 for " + record.symbol + "@" + record.date);
        }
    }

    private static List<CoefficientsRecord> loadRecords() throws IOException {
        InputStream stream = HaarBreakoutDistance.class.getResourceAsStream("HaarBreakoutDistance.jsonl");
        assertNotNull(stream, "Missing coefficients resource: HaarBreakoutDistance.jsonl");

        Gson gson = new Gson();
        List<CoefficientsRecord> records = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                CoefficientsRecord record = gson.fromJson(line, CoefficientsRecord.class);
                if (record != null) {
                    records.add(record);
                }
            }
        }
        return records;
    }

    private static final class CoefficientsRecord {
        private String symbol;
        private String date;
        private double[] closes;
    }
}

