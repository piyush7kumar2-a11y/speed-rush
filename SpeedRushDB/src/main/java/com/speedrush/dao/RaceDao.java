package com.speedrush.dao;

import com.speedrush.db.Database;
import com.speedrush.model.DriverStats;
import com.speedrush.model.FinishRecord;
import com.speedrush.model.RaceSummary;
import com.speedrush.model.TrackRecord;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class RaceDao {

    /**
     * Saves a race and all its result rows in ONE transaction:
     * either everything is stored or nothing is.
     */
    public int saveRace(String trackName, int lengthM, List<FinishRecord> results) throws SQLException {
        try (Connection con = Database.getConnection()) {
            con.setAutoCommit(false);
            try {
                int raceId;
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO races(track_name, length_m) VALUES (?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, trackName);
                    ps.setInt(2, lengthM);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        keys.next();
                        raceId = keys.getInt(1);
                    }
                }

                String sql = "INSERT INTO race_results(race_id, car_id, car_name, driver_name, position, time_ms, points) "
                        + "VALUES (?,?,?,?,?,?,?)";
                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    for (FinishRecord r : results) {
                        ps.setInt(1, raceId);
                        ps.setInt(2, r.carId());
                        ps.setString(3, r.carName());
                        ps.setString(4, r.driverName());
                        ps.setInt(5, r.position());
                        ps.setLong(6, r.timeMs());
                        ps.setInt(7, r.points());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                con.commit();
                return raceId;
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        }
    }

    public List<RaceSummary> history(int limit) throws SQLException {
        String sql = """
                SELECT r.id, r.track_name, r.length_m, r.raced_at,
                       (SELECT driver_name || ' (' || car_name || ')'
                          FROM race_results WHERE race_id = r.id AND position = 1) AS winner
                FROM races r
                ORDER BY r.id DESC
                LIMIT ?
                """;
        List<RaceSummary> list = new ArrayList<>();
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new RaceSummary(rs.getInt("id"), rs.getString("track_name"),
                            rs.getInt("length_m"), rs.getString("raced_at"), rs.getString("winner")));
                }
            }
        }
        return list;
    }

    public List<FinishRecord> resultsFor(int raceId) throws SQLException {
        String sql = """
                SELECT COALESCE(car_id, 0) AS car_id, car_name, driver_name, position, time_ms, points
                FROM race_results WHERE race_id = ? ORDER BY position
                """;
        List<FinishRecord> list = new ArrayList<>();
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, raceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new FinishRecord(rs.getInt("car_id"), rs.getString("car_name"),
                            rs.getString("driver_name"), rs.getInt("position"),
                            rs.getLong("time_ms"), rs.getInt("points")));
                }
            }
        }
        return list;
    }

    /** Championship table built with SQL aggregates (GROUP BY, SUM, AVG). */
    public List<DriverStats> leaderboard() throws SQLException {
        String sql = """
                SELECT driver_name,
                       COUNT(*)                 AS races,
                       SUM(position = 1)        AS wins,
                       SUM(position <= 3)       AS podiums,
                       SUM(points)              AS pts,
                       ROUND(AVG(position), 2)  AS avg_pos
                FROM race_results
                GROUP BY driver_name
                ORDER BY pts DESC, wins DESC, avg_pos ASC
                """;
        List<DriverStats> list = new ArrayList<>();
        try (Connection con = Database.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new DriverStats(rs.getString("driver_name"), rs.getInt("races"),
                        rs.getInt("wins"), rs.getInt("podiums"),
                        rs.getInt("pts"), rs.getDouble("avg_pos")));
            }
        }
        return list;
    }

    /** Fastest time ever set on each track (window function: best row per track + length). */
    public List<TrackRecord> trackRecords() throws SQLException {
        String sql = """
                SELECT track_name, length_m, time_ms, driver_name, car_name FROM (
                    SELECT r.track_name, r.length_m, rr.time_ms, rr.driver_name, rr.car_name,
                           ROW_NUMBER() OVER (PARTITION BY r.track_name, r.length_m
                                              ORDER BY rr.time_ms, rr.id) AS rn
                    FROM race_results rr JOIN races r ON r.id = rr.race_id
                ) WHERE rn = 1
                ORDER BY track_name, length_m
                """;
        List<TrackRecord> list = new ArrayList<>();
        try (Connection con = Database.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new TrackRecord(rs.getString("track_name"), rs.getInt("length_m"),
                        rs.getLong("time_ms"), rs.getString("driver_name"), rs.getString("car_name")));
            }
        }
        return list;
    }
}
