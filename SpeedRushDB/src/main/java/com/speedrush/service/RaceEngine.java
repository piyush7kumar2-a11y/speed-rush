package com.speedrush.service;

import com.speedrush.model.Car;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Simulates a race. Every car runs on its own thread (ExecutorService),
 * while the calling thread redraws the live positions.
 * Finish times are computed from logical ticks, so thread scheduling cannot change the result.
 */
public class RaceEngine {

    /** One simulation tick is 100 ms of race time, whatever the real sleep per tick is. */
    public static final int TICK_MS = 100;

    /** One car taking part in a race. Fields are volatile because two threads touch them. */
    public static final class Entrant {
        private final Car car;
        private volatile double position;
        private volatile boolean finished;
        private volatile long finishMs = Long.MAX_VALUE;

        Entrant(Car car) { this.car = car; }

        public Car car()          { return car; }
        public double position()  { return position; }
        public boolean finished() { return finished; }
        public long finishMs()    { return finishMs; }
    }

    private final int trackLength;
    private final long sleepMs;

    public RaceEngine(int trackLength) {
        this(trackLength, TICK_MS);
    }

    /** sleepMs = real time per tick; tests use 0 so a race finishes instantly (results are unaffected). */
    public RaceEngine(int trackLength, long sleepMs) {
        this.trackLength = trackLength;
        this.sleepMs = sleepMs;
    }

    /** Runs the race and returns entrants sorted by finishing order (winner first). */
    public List<Entrant> run(List<Car> cars, Consumer<List<Entrant>> renderer) throws InterruptedException {
        List<Entrant> entrants = new ArrayList<>();
        for (Car c : cars) {
            entrants.add(new Entrant(c));
        }

        ExecutorService pool = Executors.newFixedThreadPool(entrants.size());
        CountDownLatch done = new CountDownLatch(entrants.size());

        for (Entrant entrant : entrants) {
            pool.submit(() -> {
                try {
                    drive(entrant);
                } finally {
                    done.countDown();
                }
            });
        }
        pool.shutdown();

        // UI thread: redraw until every car has crossed the line
        try {
            while (!done.await(150, TimeUnit.MILLISECONDS)) {
                renderer.accept(entrants);
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();   // do not leave car threads running if the player quits
            throw e;
        }
        renderer.accept(entrants);

        List<Entrant> sorted = new ArrayList<>(entrants);
        sorted.sort(Comparator.comparingLong(Entrant::finishMs));
        return sorted;
    }

    private void drive(Entrant entrant) {
        Car car = entrant.car;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        double target = 2.0 + car.topSpeed() * 0.3;          // cruising speed (m per tick)
        double gain = 0.05 + car.acceleration() * 0.02;      // how fast it approaches cruising speed
        double slipChance = 0.04 - car.handling() * 0.003;   // better handling = fewer slips

        double speed = 0;
        double pos = 0;
        long tick = 0;

        try {
            while (pos < trackLength) {
                if (sleepMs > 0) Thread.sleep(sleepMs);
                tick++;

                speed += (target - speed) * gain + rnd.nextDouble(-0.3, 0.3);
                if (rnd.nextDouble() < slipChance) {
                    speed *= 0.6;                             // slip / mistake in a corner
                }
                speed = Math.max(0.5, speed);

                pos += speed;
                entrant.position = Math.min(pos, trackLength);
            }
            double overshoot = pos - trackLength;
            entrant.finishMs = Math.round((tick - overshoot / speed) * TICK_MS);
            entrant.finished = true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
