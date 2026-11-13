package com.speedrush.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JsonTest {

    @Test
    void escapesQuotesBackslashesAndControlCharacters() {
        assertEquals("\"a\\\"b\\\\c\\n\\u0001\"", Json.write("a\"b\\c\n\u0001"));
    }

    @Test
    void escapesLineSeparatorsThatBreakJavaScript() {
        assertEquals("\"\\u2028\"", Json.write("\u2028"));
    }

    @Test
    void writesNestedStructuresInInsertionOrder() {
        String json = Json.write(Json.obj("b", 1, "a", List.of(true, "x", 2.5), "n", null));
        assertEquals("{\"b\":1,\"a\":[true,\"x\",2.5],\"n\":null}", json);
    }

    @Test
    void nonFiniteNumbersBecomeNull() {
        assertEquals("null", Json.write(Double.NaN));
    }

    @Test
    void writesIntArrays() {
        assertEquals("[25,18]", Json.write(new int[]{25, 18}));
    }
}
