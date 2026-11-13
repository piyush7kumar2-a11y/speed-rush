package com.speedrush.service;

import com.speedrush.dao.CarDao;
import com.speedrush.dao.DriverDao;
import com.speedrush.model.Car;
import com.speedrush.model.Driver;

import java.sql.SQLException;

/**
 * Drivers and cars: validation rules and demo data in ONE place,
 * shared by the console menu and the web API.
 * Validation problems are reported as IllegalArgumentException (a message safe to show to the player).
 */
public class GarageService {

    public static final int STAT_BUDGET = 18;
    public static final int MIN_STAT = 1;
    public static final int MAX_STAT = 10;
    public static final int MAX_NAME = 20;

    private static final String[][] DEMO = {
            {"Aarav", "Thunderbolt", "8", "5", "5"},
            {"Priya", "Silverstreak", "6", "7", "5"},
            {"Rohan", "Road Runner", "5", "6", "7"},
            {"Simran", "Blaze", "7", "6", "5"},
    };

    private final DriverDao driverDao;
    private final CarDao carDao;

    public GarageService(DriverDao driverDao, CarDao carDao) {
        this.driverDao = driverDao;
        this.carDao = carDao;
    }

    // ---------- pure validation (unit-testable, no database) ----------

    /** Trims and checks a driver/car name. */
    public static String cleanName(String label, String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty() || s.length() > MAX_NAME) {
            throw new IllegalArgumentException(label + " must be 1-" + MAX_NAME + " characters.");
        }
        if (s.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(label + " contains invalid characters.");
        }
        return s;
    }

    public static void checkStats(int speed, int acceleration, int handling) {
        for (int v : new int[]{speed, acceleration, handling}) {
            if (v < MIN_STAT || v > MAX_STAT) {
                throw new IllegalArgumentException("Each stat must be between " + MIN_STAT + " and " + MAX_STAT + ".");
            }
        }
        if (speed + acceleration + handling > STAT_BUDGET) {
            throw new IllegalArgumentException("Stats can total at most " + STAT_BUDGET + " points.");
        }
    }

    // ---------- operations ----------

    /** Registers a driver; fails if the name is taken (case-insensitive). */
    public Driver registerDriver(String rawName) throws SQLException {
        String name = cleanName("Driver name", rawName);
        if (driverDao.findByName(name).isPresent()) {
            throw new IllegalArgumentException("A driver called '" + name + "' already exists.");
        }
        return driverDao.insert(name);
    }

    /** Web mode: builds a car for a driver, creating the driver if the name is new. @return new car id */
    public int buildCar(String rawDriver, String rawCar, int speed, int acceleration, int handling)
            throws SQLException {
        String driverName = cleanName("Driver name", rawDriver);
        String carName = cleanName("Car name", rawCar);
        checkStats(speed, acceleration, handling);
        Driver d = findOrCreateDriver(driverName);
        return carDao.insert(new Car(0, carName, d.id(), null, speed, acceleration, handling));
    }

    /** Console mode: builds a car for an existing driver id. @return new car id */
    public int buildCar(int driverId, String rawCar, int speed, int acceleration, int handling)
            throws SQLException {
        String carName = cleanName("Car name", rawCar);
        checkStats(speed, acceleration, handling);
        return carDao.insert(new Car(0, carName, driverId, null, speed, acceleration, handling));
    }

    private Driver findOrCreateDriver(String name) throws SQLException {
        var existing = driverDao.findByName(name);
        if (existing.isPresent()) return existing.get();
        try {
            return driverDao.insert(name);
        } catch (SQLException e) {
            // Two players created the same new driver at the same moment: the UNIQUE constraint
            // rejected the second insert, so the driver exists now - just use it.
            return driverDao.findByName(name).orElseThrow(() -> e);
        }
    }

    /** Adds the four demo drivers + cars when the database has no drivers. @return true if added */
    public boolean seedDemoDataIfEmpty() throws SQLException {
        if (!driverDao.findAll().isEmpty()) return false;
        for (String[] row : DEMO) {
            Driver d = driverDao.insert(row[0]);
            carDao.insert(new Car(0, row[1], d.id(), null,
                    Integer.parseInt(row[2]), Integer.parseInt(row[3]), Integer.parseInt(row[4])));
        }
        return true;
    }

    public static int demoCount() {
        return DEMO.length;
    }
}
