package com.speedrush.web;

import com.speedrush.dao.CarDao;
import com.speedrush.dao.DriverDao;
import com.speedrush.dao.RaceDao;
import com.speedrush.model.Car;
import com.speedrush.model.DriverStats;
import com.speedrush.model.RaceSummary;
import com.speedrush.model.TrackRecord;
import com.speedrush.service.GarageService;
import com.speedrush.service.RaceService;
import com.speedrush.util.Json;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Small REST API + the game page, using only the JDK (com.sun.net.httpserver).
 *
 *   GET  /                 the game (web/index.html, bundled in the jar)
 *   GET  /api/config       points table + stat rules (single source of truth for the page)
 *   GET  /api/cars         GET /api/leaderboard     GET /api/history     GET /api/records
 *   POST /api/garage       build a car              POST /api/race       save a finished race
 *   GET  /api/health       liveness check
 */
public class WebServer {

    private static final int MAX_BODY = 16 * 1024;
    private static final String CSP = "default-src 'self'; script-src 'self' 'unsafe-inline'; "
            + "style-src 'self' 'unsafe-inline'; img-src 'self' data:; frame-ancestors 'none'";

    /** Thrown for expected client problems; carries the HTTP status. */
    private static final class ApiException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final int status;
        ApiException(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    private final CarDao carDao;
    private final RaceDao raceDao;
    private final GarageService garage;
    private final RaceService races;
    private HttpServer server;
    private ExecutorService pool;

    public WebServer() {
        this(new DriverDao(), new CarDao(), new RaceDao());
    }

    /** DAOs are injectable so the server can be tested without a database. */
    public WebServer(DriverDao driverDao, CarDao carDao, RaceDao raceDao) {
        this.carDao = carDao;
        this.raceDao = raceDao;
        this.garage = new GarageService(driverDao, carDao);
        this.races = new RaceService(raceDao, carDao);
    }

    /** @return the port actually bound (useful with port 0 in tests) */
    public int start(int port) throws IOException, SQLException {
        byte[] page = loadPage();
        garage.seedDemoDataIfEmpty();

        server = HttpServer.create(new InetSocketAddress(port), 0);
        pool = Executors.newFixedThreadPool(8);
        server.setExecutor(pool);
        server.createContext("/", ex -> {
            try {
                route(ex, page);
            } catch (Throwable t) {          // never let an exception kill the connection silently
                System.err.println("Unhandled error: " + t);
                try { ex.close(); } catch (Exception ignored) { }
            }
        });
        server.start();
        return server.getAddress().getPort();
    }

    public void stop() {
        if (server != null) server.stop(1);
        if (pool != null) {
            pool.shutdown();
            try {
                pool.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // ---------------- routing ----------------

    private void route(HttpExchange ex, byte[] page) throws IOException {
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();
        try {
            if (path.equals("/") || path.equals("/index.html")) {
                allow(method, "GET");
                send(ex, 200, "text/html; charset=utf-8", page, "no-cache");
                return;
            }
            if (!path.startsWith("/api/")) throw new ApiException(404, "Not found");

            Object result = switch (path) {
                case "/api/health"      -> { allow(method, "GET");  yield Json.obj("ok", true); }
                case "/api/config"      -> { allow(method, "GET");  yield config(); }
                case "/api/cars"        -> { allow(method, "GET");  yield cars(); }
                case "/api/leaderboard" -> { allow(method, "GET");  yield leaderboard(); }
                case "/api/history"     -> { allow(method, "GET");  yield history(params(ex, false)); }
                case "/api/records"     -> { allow(method, "GET");  yield records(); }
                case "/api/garage"      -> { allow(method, "POST"); yield addCar(params(ex, true)); }
                case "/api/race"        -> { allow(method, "POST"); yield saveRace(params(ex, true)); }
                default -> throw new ApiException(404, "Unknown endpoint");
            };
            send(ex, 200, "application/json; charset=utf-8", Json.write(result).getBytes(UTF_8), "no-store");

        } catch (ApiException e) {
            sendError(ex, e.status, e.getMessage());
        } catch (IllegalArgumentException e) {      // validation errors, incl. NumberFormatException
            sendError(ex, 400, e.getMessage() == null ? "Bad request" : e.getMessage());
        } catch (Exception e) {
            // Details go to the log, not to the client.
            System.err.println("Error handling " + method + " " + path + ": " + e);
            sendError(ex, 500, "Server error - please try again.");
        }
    }

    private static void allow(String actual, String expected) {
        if (!actual.equals(expected)) throw new ApiException(405, "Use " + expected + " for this URL.");
    }

    // ---------------- endpoints ----------------

    private Object config() {
        return Json.obj(
                "points", RaceService.pointsTable(),
                "statBudget", GarageService.STAT_BUDGET,
                "maxName", GarageService.MAX_NAME,
                "minCars", RaceService.MIN_CARS,
                "maxCars", RaceService.MAX_CARS);
    }

    private Object cars() throws SQLException {
        List<Object> out = new ArrayList<>();
        for (Car c : carDao.findAll()) {
            out.add(Json.obj("id", c.id(), "name", c.name(), "driver", c.driverName(),
                    "spd", c.topSpeed(), "acc", c.acceleration(), "hnd", c.handling()));
        }
        return out;
    }

    private Object leaderboard() throws SQLException {
        List<Object> out = new ArrayList<>();
        for (DriverStats s : raceDao.leaderboard()) {
            out.add(Json.obj("driver", s.driverName(), "races", s.races(), "wins", s.wins(),
                    "podiums", s.podiums(), "points", s.points(), "avg", s.avgPosition()));
        }
        return out;
    }

    private Object history(Map<String, List<String>> p) throws SQLException {
        int limit = 10;
        if (p.containsKey("limit")) limit = Math.max(1, Math.min(50, integer(p, "limit")));
        List<Object> out = new ArrayList<>();
        for (RaceSummary r : raceDao.history(limit)) {
            out.add(Json.obj("id", r.id(), "track", r.trackName(), "lengthM", r.lengthM(),
                    "racedAt", r.racedAt(), "winner", r.winner()));
        }
        return out;
    }

    private Object records() throws SQLException {
        List<Object> out = new ArrayList<>();
        for (TrackRecord r : raceDao.trackRecords()) {
            out.add(Json.obj("track", r.trackName(), "lengthM", r.lengthM(), "timeMs", r.timeMs(),
                    "driver", r.driverName(), "car", r.carName()));
        }
        return out;
    }

    /** Build a new car (creates the driver if the name is new). */
    private Object addCar(Map<String, List<String>> p) throws SQLException {
        int id = garage.buildCar(one(p, "driver"), one(p, "car"),
                integer(p, "spd"), integer(p, "acc"), integer(p, "hnd"));
        return Json.obj("carId", id);
    }

    /** The browser sends r=carId:timeMs for every car; the service validates, ranks by time and stores it. */
    private Object saveRace(Map<String, List<String>> p) throws SQLException {
        String track = one(p, "track");
        int length = integer(p, "length");

        List<RaceService.Entry> entries = new ArrayList<>();
        for (String s : p.getOrDefault("r", List.of())) {
            String[] a = s.split(":");
            if (a.length != 2) throw new IllegalArgumentException("Bad result entry.");
            entries.add(new RaceService.Entry(Integer.parseInt(a[0].trim()), Long.parseLong(a[1].trim())));
        }
        return Json.obj("raceId", races.submitRace(track, length, entries));
    }

    // ---------------- plumbing ----------------

    private static byte[] loadPage() throws IOException {
        try (InputStream in = WebServer.class.getResourceAsStream("/web/index.html")) {
            if (in == null) throw new IOException("web/index.html is missing from the application resources.");
            return in.readAllBytes();
        }
    }

    /** POST: parameters come from the form body; GET: from the query string. */
    private static Map<String, List<String>> params(HttpExchange ex, boolean fromBody) throws IOException {
        if (!fromBody) return parse(ex.getRequestURI().getRawQuery());
        byte[] body;
        try (InputStream in = ex.getRequestBody()) {
            body = in.readNBytes(MAX_BODY + 1);
        }
        if (body.length > MAX_BODY) throw new ApiException(413, "Request too large.");
        return parse(new String(body, UTF_8));
    }

    private void sendError(HttpExchange ex, int code, String message) throws IOException {
        if (code == 405) ex.getResponseHeaders().set("Allow", message.contains("POST") ? "POST" : "GET");
        send(ex, code, "application/json; charset=utf-8",
                Json.write(Json.obj("error", message)).getBytes(UTF_8), "no-store");
    }

    private void send(HttpExchange ex, int code, String type, byte[] body, String cache) throws IOException {
        var h = ex.getResponseHeaders();
        h.set("Content-Type", type);
        h.set("Cache-Control", cache);
        h.set("X-Content-Type-Options", "nosniff");
        h.set("Referrer-Policy", "no-referrer");
        if (type.startsWith("text/html")) h.set("Content-Security-Policy", CSP);
        ex.sendResponseHeaders(code, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }

    private static Map<String, List<String>> parse(String raw) {
        Map<String, List<String>> map = new HashMap<>();
        if (raw == null || raw.isEmpty()) return map;
        for (String pair : raw.split("&")) {
            if (pair.isEmpty()) continue;
            int i = pair.indexOf('=');
            String key = URLDecoder.decode(i < 0 ? pair : pair.substring(0, i), UTF_8);
            String val = i < 0 ? "" : URLDecoder.decode(pair.substring(i + 1), UTF_8);
            map.computeIfAbsent(key, k -> new ArrayList<>()).add(val);
        }
        return map;
    }

    private static String one(Map<String, List<String>> p, String key) {
        List<String> v = p.get(key);
        if (v == null || v.isEmpty()) throw new IllegalArgumentException("Missing field: " + key);
        return v.get(0);
    }

    private static int integer(Map<String, List<String>> p, String key) {
        try {
            return Integer.parseInt(one(p, key).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Field '" + key + "' must be a whole number.");
        }
    }
}
