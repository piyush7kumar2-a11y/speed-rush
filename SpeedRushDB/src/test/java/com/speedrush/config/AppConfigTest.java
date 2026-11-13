package com.speedrush.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AppConfigTest {

    @Test
    void parsesFlags() {
        AppConfig c = AppConfig.parse(new String[]{"--port=9000", "--db", "my.db", "--console"});
        assertEquals(9000, c.port());
        assertEquals("my.db", c.dbPath());
        assertTrue(c.console());
    }

    @Test
    void rejectsBadPortsAndUnknownOptions() {
        assertThrows(IllegalArgumentException.class, () -> AppConfig.parse(new String[]{"--port=abc"}));
        assertThrows(IllegalArgumentException.class, () -> AppConfig.parse(new String[]{"--port=70000"}));
        assertThrows(IllegalArgumentException.class, () -> AppConfig.parse(new String[]{"--bogus"}));
    }
}
