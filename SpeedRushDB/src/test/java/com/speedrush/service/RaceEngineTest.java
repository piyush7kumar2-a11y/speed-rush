package com.speedrush.service;

import com.speedrush.model.Car;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RaceEngineTest {

    private static Car car(int id, int spd, int acc, int hnd) {
        return new Car(id, "Car" + id, id, "Driver" + id, spd, acc, hnd);
    }

    @Test
    void everyCarFinishesAndResultsAreSortedByTime() throws Exception {
        List<Car> cars = List.of(car(1, 8, 5, 5), car(2, 6, 7, 5), car(3, 5, 6, 7), car(4, 1, 1, 1));
        List<RaceEngine.Entrant> result = new RaceEngine(300, 0).run(cars, e -> { });

        assertEquals(4, result.size());
        long previous = 0;
        for (RaceEngine.Entrant e : result) {
            assertTrue(e.finished());
            assertEquals(300.0, e.position(), 1e-9);
            assertTrue(e.finishMs() >= previous, "must be sorted fastest first");
            assertTrue(e.finishMs() > 0 && e.finishMs() < Long.MAX_VALUE);
            previous = e.finishMs();
        }
    }

    @Test
    void aMuchFasterCarWinsMostRaces() throws Exception {
        int fastWins = 0;
        for (int i = 0; i < 30; i++) {
            var r = new RaceEngine(500, 0).run(List.of(car(1, 10, 10, 10), car(2, 1, 1, 1)), e -> { });
            if (r.get(0).car().id() == 1) fastWins++;
        }
        assertTrue(fastWins >= 28, "fast car won only " + fastWins + "/30");
    }
}
