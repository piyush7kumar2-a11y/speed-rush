package com.speedrush.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/** Opens connections and creates the schema on first run. */
public final class Database {

    private static volatile String url = "jdbc:sqlite:speedrush.db";

    private Database() {}

    /** Point the app at another SQLite file (call before {@link #init()}). */
    public static void configure(String dbPath) {
        url = "jdbc:sqlite:" + dbPath;
    }

    public static Connection getConnection() throws SQLException {
        Connection con = DriverManager.getConnection(url);
        try (Statement st = con.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");   // SQLite needs this per connection
            st.execute("PRAGMA busy_timeout = 5000"); // wait for a lock instead of failing at once
        } catch (SQLException e) {
            con.close();
            throw e;
        }
        return con;
    }

    public static void init() throws SQLException {
        try (Connection con = getConnection(); Statement st = con.createStatement()) {

            // WAL lets the web server read the leaderboard while a race is being saved.
            st.execute("PRAGMA journal_mode = WAL");

            // COLLATE NOCASE: "aarav" and "Aarav" are the same driver, enforced by the database itself.
            st.execute("""
                CREATE TABLE IF NOT EXISTS drivers (
                    id   INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL UNIQUE COLLATE NOCASE
                )""");

            st.execute("""
                CREATE TABLE IF NOT EXISTS cars (
                    id           INTEGER PRIMARY KEY AUTOINCREMENT,
                    name         TEXT    NOT NULL,
                    driver_id    INTEGER NOT NULL,
                    top_speed    INTEGER NOT NULL CHECK (top_speed    BETWEEN 1 AND 10),
                    acceleration INTEGER NOT NULL CHECK (acceleration BETWEEN 1 AND 10),
                    handling     INTEGER NOT NULL CHECK (handling     BETWEEN 1 AND 10),
                    FOREIGN KEY (driver_id) REFERENCES drivers(id) ON DELETE CASCADE
                )""");

            st.execute("""
                CREATE TABLE IF NOT EXISTS races (
                    id         INTEGER PRIMARY KEY AUTOINCREMENT,
                    track_name TEXT    NOT NULL,
                    length_m   INTEGER NOT NULL,
                    raced_at   TEXT    NOT NULL DEFAULT CURRENT_TIMESTAMP
                )""");

            // Names are copied into the result row so history survives deleting a car/driver.
            st.execute("""
                CREATE TABLE IF NOT EXISTS race_results (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    race_id     INTEGER NOT NULL,
                    car_id      INTEGER,
                    car_name    TEXT    NOT NULL,
                    driver_name TEXT    NOT NULL,
                    position    INTEGER NOT NULL,
                    time_ms     INTEGER NOT NULL,
                    points      INTEGER NOT NULL,
                    FOREIGN KEY (race_id) REFERENCES races(id) ON DELETE CASCADE,
                    FOREIGN KEY (car_id)  REFERENCES cars(id)  ON DELETE SET NULL
                )""");

            st.execute("CREATE INDEX IF NOT EXISTS idx_results_driver ON race_results(driver_name)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_results_race   ON race_results(race_id)");
            // Deleting a car runs "SET NULL" on race_results.car_id - without this index that is a full scan.
            st.execute("CREATE INDEX IF NOT EXISTS idx_results_car    ON race_results(car_id)");
        }
    }
}
