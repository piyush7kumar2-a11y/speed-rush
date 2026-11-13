package com.speedrush.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GarageServiceTest {

    @Test
    void trimsNames() {
        assertEquals("Aarav", GarageService.cleanName("Driver name", "  Aarav "));
    }

    @Test
    void rejectsBadNames() {
        assertThrows(IllegalArgumentException.class, () -> GarageService.cleanName("Car name", "   "));
        assertThrows(IllegalArgumentException.class, () -> GarageService.cleanName("Car name", null));
        assertThrows(IllegalArgumentException.class, () -> GarageService.cleanName("Car name", "x".repeat(21)));
        assertThrows(IllegalArgumentException.class, () -> GarageService.cleanName("Car name", "bad\u0007name"));
    }

    @Test
    void statBudgetIsEnforced() {
        assertDoesNotThrow(() -> GarageService.checkStats(6, 6, 6));
        assertDoesNotThrow(() -> GarageService.checkStats(10, 7, 1));
        assertThrows(IllegalArgumentException.class, () -> GarageService.checkStats(7, 6, 6));
    }

    @Test
    void eachStatMustBeInRange() {
        assertThrows(IllegalArgumentException.class, () -> GarageService.checkStats(0, 5, 5));
        assertThrows(IllegalArgumentException.class, () -> GarageService.checkStats(11, 1, 1));
    }
}
