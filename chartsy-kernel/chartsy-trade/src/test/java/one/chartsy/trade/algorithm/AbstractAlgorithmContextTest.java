package one.chartsy.trade.algorithm;

import org.junit.jupiter.api.Test;
import org.springframework.beans.DirectFieldAccessor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;

class AbstractAlgorithmContextTest {

    final AbstractAlgorithmContext context = new DefaultAlgorithmContext("test", null, null);

    @Test
    void expandPlaceholders_expands_now_and_uuid() {
        var pattern = "yyyyMMdd";
        var input = "output_${now?" + pattern + "}_${uuid}.jsonl";
        var expanded = context.expandPlaceholders(input, null);
        var today = LocalDateTime.now().format(DateTimeFormatter.ofPattern(pattern));

        assertTrue(expanded.matches("output_" + today + "_[0-9a-f\\-]{36}\\.jsonl"));
    }

    @Test
    void expandPlaceholders_withoutPlaceholders() {
        var input = "output_static.jsonl";
        var expanded = context.expandPlaceholders(input, null);

        assertEquals("output_static.jsonl", expanded);
    }

    @Test
    void expandPlaceholders_unknownPlaceholderUnchanged() {
        var input = "output_${unknown}.jsonl";
        var expanded = context.expandPlaceholders(input, null);

        assertEquals("output_${unknown}.jsonl", expanded);
    }

    @Test
    void expandPlaceholders_withPropertyAccessor() {
        record Nested(String property) { }
        record MyProperties(Nested nested) { }

        var input = "output_${nested.property}.jsonl";
        var expanded = context.expandPlaceholders(input, new DirectFieldAccessor(new MyProperties(new Nested("value"))));

        assertEquals("output_value.jsonl", expanded);
    }
}
