package com.speedrush.service;

import com.speedrush.dao.CarDao;
import com.speedrush.dao.RaceDao;
import com.speedrush.model.Car;
import com.speedrush.model.FinishRecord;

import java.sql.SQLException;
import java.util.*;
import java.util.function.Consumer;

/** Business logic: run a race, validate reported results, award championship points, store everything. */
public class RaceService {

    // Formula-1 style points for 1st, 2nd, 3rd ...
    private static final int[] POINTS = {25, 18, 15, 12, 10, 8, 6, 4};

    public static final int MIN_CARS = 2;
    public static final int MAX_CARS = 8;
    /** Sanity limits for times reported by the browser (it plays the race, so it cannot be fully trusted). */
    public static final long MIN_TIME_MS = 15_000;
    public static final long MAX_TIME_MS = 600_000;

    /** One car's result as reported by the browser. */
    public record Entry(int carId, long timeMs) {}

    private final RaceDao raceDao;
    private final CarDao carDao;

    public RaceService(RaceDao raceDao, CarDao carDao) {
        this.raceDao = raceDao;
        this.carDao = carDao;
    }

    /** Points for a 1-based finishing position. */
    public static int pointsFor(int position) {
        return position >= 1 && position <= POINTS.length ? POINTS[position - 1] : 0;
    }

    /** The full points table (also sent to the web page so it never disagrees with the server). */
    public static int[] pointsTable() {
        return POINTS.clone();
    }

    // ---------- console mode ----------

    /** Simulate the race with threads, then save. @return id of the saved race */
    public int runRace(String trackName, int lengthM, List<Car> cars,
                       Consumer<List<RaceEngine.Entrant>> renderer)
            throws SQLException, InterruptedException {

        List<RaceEngine.Entrant> order = new RaceEngine(lengthM).run(cars, renderer);

        List<Car> orderedCars = new ArrayList<>();
        List<Long> times = new ArrayList<>();
        for (RaceEngine.Entrant e : order) {
            orderedCars.add(e.car());
            times.add(e.finishMs());
        }
        return saveRace(trackName, lengthM, orderedCars, times);
    }

    // ---------- web mode ----------

    /** Validates what the browser sent, ranks the cars by time and stores the race. @return race id */
    public int submitRace(String rawTrack, int lengthM, List<Entry> reported) throws SQLException {
        String track = checkTrack(rawTrack, lengthM);

        Map<Integer, Car> byId = new HashMap<>();
        for (Car c : carDao.findAll()) byId.put(c.id(), c);

        List<Entry> ranked = checkAndRank(reported, byId.keySet());

        List<Car> ordered = new ArrayList<>();
        List<Long> times = new ArrayList<>();
        for (Entry e : ranked) {
            ordered.add(byId.get(e.carId()));
            times.add(e.timeMs());
        }
        return saveRace(track, lengthM, ordered, times);
    }

    /** Pure validation, easy to unit test. */
    public static String checkTrack(String rawTrack, int lengthM) {
        String track = rawTrack == null ? "" : rawTrack.trim();
        if (track.isEmpty() || track.length() > 20 || lengthM < 50 || lengthM > 100_000) {
            throw new IllegalArgumentException("Invalid track.");
        }
        return track;
    }

    /** Pure validation + ranking (fastest first; equal times keep the order they were sent in). */
    public static List<Entry> checkAndRank(List<Entry> entries, Set<Integer> knownCarIds) {
        if (entries == null || entries.size() < MIN_CARS || entries.size() > MAX_CARS) {
            throw new IllegalArgumentException("A race needs " + MIN_CARS + "-" + MAX_CARS + " cars.");
        }
        Set<Integer> seen = new HashSet<>();
        for (Entry e : entries) {
            if (!knownCarIds.contains(e.carId())) throw new IllegalArgumentException("Unknown car " + e.carId());
            if (!seen.add(e.carId())) throw new IllegalArgumentException("Car " + e.carId() + " is entered twice.");
            if (e.timeMs() < MIN_TIME_MS || e.timeMs() > MAX_TIME_MS) throw new IllegalArgumentException("Invalid time.");
        }
        List<Entry> ranked = new ArrayList<>(entries);
        ranked.sort(Comparator.comparingLong(Entry::timeMs)); // List.sort is stable
        return ranked;
    }

    // ---------- shared ----------

    /** Cars are given in finishing order. */
    public int saveRace(String trackName, int lengthM, List<Car> orderedCars, List<Long> timesMs)
            throws SQLException {

        List<FinishRecord> records = new ArrayList<>();
        for (int i = 0; i < orderedCars.size(); i++) {
            Car c = orderedCars.get(i);
            records.add(new FinishRecord(c.id(), c.name(), c.driverName(), i + 1, timesMs.get(i), pointsFor(i + 1)));
        }
        return raceDao.saveRace(trackName, lengthM, records);
    }
}
