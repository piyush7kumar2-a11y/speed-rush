package com.speedrush.ui;

import com.speedrush.dao.CarDao;
import com.speedrush.dao.DriverDao;
import com.speedrush.dao.RaceDao;
import com.speedrush.model.*;
import com.speedrush.service.GarageService;
import com.speedrush.service.RaceEngine;
import com.speedrush.service.RaceService;

import java.sql.SQLException;
import java.util.*;

public class ConsoleMenu {

    private static final int STAT_BUDGET = GarageService.STAT_BUDGET;
    private static final int BAR_WIDTH = 40;

    private final Scanner in = new Scanner(System.in);
    private final DriverDao driverDao = new DriverDao();
    private final CarDao carDao = new CarDao();
    private final RaceDao raceDao = new RaceDao();
    private final GarageService garage = new GarageService(driverDao, carDao);
    private final RaceService raceService = new RaceService(raceDao, carDao);

    private int renderedLines = 0;

    public void run() {
        System.out.println("==========================================");
        System.out.println("   SPEED RUSH - Racing Championship Manager");
        System.out.println("==========================================");

        boolean running = true;
        while (running) {
            printMenu();
            int choice = readInt("Choose an option", 0, 9);
            try {
                switch (choice) {
                    case 1 -> addDriver();
                    case 2 -> addCar();
                    case 3 -> listCars();
                    case 4 -> startRace();
                    case 5 -> showLeaderboard();
                    case 6 -> showHistory();
                    case 7 -> deleteCar();
                    case 8 -> deleteDriver();
                    case 9 -> seedDemoData();
                    case 0 -> running = false;
                    default -> { }
                }
            } catch (IllegalArgumentException e) {
                System.out.println(e.getMessage());          // validation message from the service
            } catch (SQLException e) {
                System.out.println("Database error: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running = false;
            }
        }
        System.out.println("Goodbye!");
    }

    private void printMenu() {
        System.out.println();
        System.out.println("1. Register driver        6. Race history");
        System.out.println("2. Build a car            7. Delete car");
        System.out.println("3. List cars              8. Delete driver");
        System.out.println("4. START RACE             9. Load demo data");
        System.out.println("5. Championship table     0. Exit");
    }

    // ---------------- drivers & cars ----------------

    private void addDriver() throws SQLException {
        Driver d = garage.registerDriver(readLine("Driver name"));
        System.out.println("Registered driver #" + d.id() + ": " + d.name());
    }

    private void addCar() throws SQLException {
        List<Driver> drivers = driverDao.findAll();
        if (drivers.isEmpty()) {
            System.out.println("Register a driver first.");
            return;
        }
        drivers.forEach(d -> System.out.printf("  [%d] %s%n", d.id(), d.name()));
        int driverId = readInt("Driver id", 1, Integer.MAX_VALUE);
        if (drivers.stream().noneMatch(d -> d.id() == driverId)) {
            System.out.println("No such driver.");
            return;
        }

        String name = readLine("Car name");
        GarageService.cleanName("Car name", name);   // fail early, before asking for stats

        System.out.println("Split at most " + STAT_BUDGET + " points across 3 stats (1-10 each).");
        int speed = 0, accel = 0, handling = 0;
        while (true) {
            speed = readInt("Top speed", 1, 10);
            accel = readInt("Acceleration", 1, 10);
            handling = readInt("Handling", 1, 10);
            int total = speed + accel + handling;
            if (total <= STAT_BUDGET) break;
            System.out.println("Total is " + total + " - the limit is " + STAT_BUDGET + ". Try again.");
        }

        int id = garage.buildCar(driverId, name, speed, accel, handling);
        System.out.println("Built car #" + id + ": " + name);
    }

    private void listCars() throws SQLException {
        printCars(carDao.findAll());
    }

    private void printCars(List<Car> cars) {
        if (cars.isEmpty()) {
            System.out.println("No cars yet.");
            return;
        }
        System.out.printf("%-4s %-18s %-14s %5s %5s %5s%n", "ID", "Car", "Driver", "SPD", "ACC", "HND");
        for (Car c : cars) {
            System.out.printf("%-4d %-18s %-14s %5d %5d %5d%n",
                    c.id(), trunc(c.name(), 18), trunc(c.driverName(), 14),
                    c.topSpeed(), c.acceleration(), c.handling());
        }
    }

    private void deleteCar() throws SQLException {
        List<Car> cars = carDao.findAll();
        printCars(cars);
        if (cars.isEmpty()) return;
        int id = readInt("Car id to delete (0 = cancel)", 0, Integer.MAX_VALUE);
        if (id == 0) return;
        System.out.println(carDao.delete(id) ? "Car deleted (race history is kept)." : "No such car.");
    }

    private void deleteDriver() throws SQLException {
        List<Driver> drivers = driverDao.findAll();
        if (drivers.isEmpty()) {
            System.out.println("No drivers yet.");
            return;
        }
        drivers.forEach(d -> System.out.printf("  [%d] %s%n", d.id(), d.name()));
        int id = readInt("Driver id to delete (0 = cancel)", 0, Integer.MAX_VALUE);
        if (id == 0) return;
        System.out.println(driverDao.delete(id) ? "Driver and their cars deleted." : "No such driver.");
    }

    // ---------------- racing ----------------

    private void startRace() throws SQLException, InterruptedException {
        List<Car> all = carDao.findAll();
        if (all.size() < 2) {
            System.out.println("You need at least 2 cars to race.");
            return;
        }
        printCars(all);

        Set<Integer> ids = new LinkedHashSet<>();
        try {
            for (String token : readLine("Car ids to race, comma separated (2-8)").split(",")) {
                ids.add(Integer.parseInt(token.trim()));
            }
        } catch (NumberFormatException e) {
            System.out.println("Please enter numbers like: 1,2,3");
            return;
        }
        List<Car> entrants = all.stream().filter(c -> ids.contains(c.id())).toList();
        if (entrants.size() < 2 || entrants.size() > 8) {
            System.out.println("Pick between 2 and 8 existing cars.");
            return;
        }

        System.out.println("1. Sprint (300 m)   2. Circuit (500 m)   3. Endurance (800 m)");
        int track = readInt("Track", 1, 3);
        String trackName = new String[]{"Sprint", "Circuit", "Endurance"}[track - 1];
        final int length = new int[]{300, 500, 800}[track - 1];

        System.out.println();
        for (int i = 3; i >= 1; i--) {
            System.out.println("  " + i + "...");
            Thread.sleep(700);
        }
        System.out.println("  GO!\n");

        renderedLines = 0;
        int raceId = raceService.runRace(trackName, length, entrants, list -> render(list, length));
        renderedLines = 0;

        System.out.println("\nRace #" + raceId + " saved to the database.");
        printResults(raceDao.resultsFor(raceId));
    }

    private void render(List<RaceEngine.Entrant> entrants, int length) {
        StringBuilder sb = new StringBuilder();
        if (renderedLines > 0) {
            sb.append("\033[").append(renderedLines).append("A"); // cursor up (ANSI)
        }
        for (RaceEngine.Entrant e : entrants) {
            int pct = (int) Math.min(100, Math.round(e.position() * 100.0 / length));
            int filled = pct * BAR_WIDTH / 100;
            String bar = pct < 100
                    ? "=".repeat(filled) + ">" + ".".repeat(BAR_WIDTH - filled)
                    : "=".repeat(BAR_WIDTH) + "|";
            sb.append(String.format("%-16s [%s] %3d%%%s\033[K%n",
                    trunc(e.car().name(), 16), bar, pct, e.finished() ? "  FINISHED" : ""));
        }
        System.out.print(sb);
        System.out.flush();
        renderedLines = entrants.size();
    }

    private void printResults(List<FinishRecord> results) {
        if (results.isEmpty()) {
            System.out.println("No results found.");
            return;
        }
        System.out.printf("%-4s %-18s %-14s %9s %6s%n", "POS", "Car", "Driver", "Time", "Pts");
        for (FinishRecord r : results) {
            System.out.printf("%-4d %-18s %-14s %8.2fs %6d%n",
                    r.position(), trunc(r.carName(), 18), trunc(r.driverName(), 14),
                    r.timeMs() / 1000.0, r.points());
        }
    }

    // ---------------- stats ----------------

    private void showLeaderboard() throws SQLException {
        List<DriverStats> table = raceDao.leaderboard();
        if (table.isEmpty()) {
            System.out.println("No races yet - go and start one!");
            return;
        }
        System.out.printf("%-4s %-14s %6s %5s %8s %8s %8s%n",
                "#", "Driver", "Races", "Wins", "Podiums", "AvgPos", "Points");
        int rank = 1;
        for (DriverStats s : table) {
            System.out.printf("%-4d %-14s %6d %5d %8d %8.2f %8d%n",
                    rank++, trunc(s.driverName(), 14), s.races(), s.wins(),
                    s.podiums(), s.avgPosition(), s.points());
        }
    }

    private void showHistory() throws SQLException {
        List<RaceSummary> races = raceDao.history(10);
        if (races.isEmpty()) {
            System.out.println("No races yet.");
            return;
        }
        System.out.printf("%-4s %-10s %6s  %-19s  %s%n", "ID", "Track", "Length", "Date (UTC)", "Winner");
        for (RaceSummary r : races) {
            System.out.printf("%-4d %-10s %5dm  %-19s  %s%n",
                    r.id(), r.trackName(), r.lengthM(), r.racedAt(), r.winner());
        }
        int id = readInt("Race id for full results (0 = skip)", 0, Integer.MAX_VALUE);
        if (id > 0) {
            printResults(raceDao.resultsFor(id));
        }
    }

    // ---------------- demo data ----------------

    private void seedDemoData() throws SQLException {
        if (garage.seedDemoDataIfEmpty()) {
            System.out.println("Added " + GarageService.demoCount() + " drivers with cars. Try option 4 to race them!");
        } else {
            System.out.println("Database already has drivers - demo data skipped.");
        }
    }

    // ---------------- input helpers ----------------

    private String readLine(String prompt) {
        System.out.print(prompt + ": ");
        if (!in.hasNextLine()) {
            System.exit(0);
        }
        return in.nextLine().trim();
    }

    private int readInt(String prompt, int min, int max) {
        while (true) {
            String text = readLine(prompt);
            try {
                int value = Integer.parseInt(text);
                if (value >= min && value <= max) return value;
            } catch (NumberFormatException ignored) {
                // fall through to message
            }
            System.out.println("Enter a number between " + min + " and " + (max == Integer.MAX_VALUE ? "..." : max) + ".");
        }
    }

    private static String trunc(String s, int max) {
        if (s == null) return "-";
        return s.length() <= max ? s : s.substring(0, max - 1) + ".";
    }
}
