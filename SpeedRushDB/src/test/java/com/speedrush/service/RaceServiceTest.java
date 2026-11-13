package com.speedrush.service;

import com.speedrush.service.RaceService.Entry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RaceServiceTest {

    private static final Set<Integer> CARS = Set.of(1, 2, 3, 4);

    @Test
    void pointsFollowFormulaOneStyleTable() {
        assertEquals(25, RaceService.pointsFor(1));
        assertEquals(18, RaceService.pointsFor(2));
        assertEquals(4, RaceService.pointsFor(8));
        assertEquals(0, RaceService.pointsFor(9));
        assertEquals(0, RaceService.pointsFor(0));
    }

    @Test
    void pointsTableIsACopy() {
        RaceService.pointsTable()[0] = 999;
        assertEquals(25, RaceService.pointsFor(1));
    }

    @Test
    void ranksFastestFirst() {
        List<Entry> ranked = RaceService.checkAndRank(
                List.of(new Entry(1, 90_000), new Entry(2, 80_000), new Entry(3, 85_000)), CARS);
        assertEquals(List.of(2, 3, 1), ranked.stream().map(Entry::carId).toList());
    }

    @Test
    void equalTimesKeepSubmissionOrder() {
        List<Entry> ranked = RaceService.checkAndRank(
                List.of(new Entry(3, 80_000), new Entry(1, 80_000)), CARS);
        assertEquals(List.of(3, 1), ranked.stream().map(Entry::carId).toList());
    }

    @Test
    void rejectsTooFewOrTooManyCars() {
        assertThrows(IllegalArgumentException.class,
                () -> RaceService.checkAndRank(List.of(new Entry(1, 80_000)), CARS));
        List<Entry> nine = java.util.stream.IntStream.rangeClosed(1, 9).mapToObj(i -> new Entry(i, 80_000)).toList();
        Set<Integer> many = Set.of(1, 2, 3, 4, 5, 6, 7, 8, 9);
        assertThrows(IllegalArgumentException.class, () -> RaceService.checkAndRank(nine, many));
    }

    @Test
    void rejectsUnknownAndDuplicateCars() {
        assertThrows(IllegalArgumentException.class,
                () -> RaceService.checkAndRank(List.of(new Entry(1, 80_000), new Entry(99, 81_000)), CARS));
        assertThrows(IllegalArgumentException.class,
                () -> RaceService.checkAndRank(List.of(new Entry(1, 80_000), new Entry(1, 81_000)), CARS));
    }

    @Test
    void rejectsImplausibleTimes() {
        assertThrows(IllegalArgumentException.class,
                () -> RaceService.checkAndRank(List.of(new Entry(1, 500), new Entry(2, 81_000)), CARS));
        assertThrows(IllegalArgumentException.class,
                () -> RaceService.checkAndRank(List.of(new Entry(1, 80_000), new Entry(2, 9_999_999)), CARS));
    }

    @Test
    void validatesTrack() {
        assertEquals("Monza", RaceService.checkTrack("  Monza ", 17_379));
        assertThrows(IllegalArgumentException.class, () -> RaceService.checkTrack("", 500));
        assertThrows(IllegalArgumentException.class, () -> RaceService.checkTrack("Monza", 10));
        assertThrows(IllegalArgumentException.class, () -> RaceService.checkTrack("x".repeat(21), 500));
    }
}
