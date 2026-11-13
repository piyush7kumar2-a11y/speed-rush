package com.speedrush.dao;

import com.speedrush.db.Database;
import com.speedrush.model.Driver;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DriverDao {

    public Driver insert(String name) throws SQLException {
        String sql = "INSERT INTO drivers(name) VALUES (?)";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return new Driver(keys.getInt(1), name);
            }
        }
    }

    public Optional<Driver> findByName(String name) throws SQLException {
        String sql = "SELECT id, name FROM drivers WHERE name = ? COLLATE NOCASE";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next()
                        ? Optional.of(new Driver(rs.getInt("id"), rs.getString("name")))
                        : Optional.empty();
            }
        }
    }

    public List<Driver> findAll() throws SQLException {
        List<Driver> list = new ArrayList<>();
        try (Connection con = Database.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, name FROM drivers ORDER BY id")) {
            while (rs.next()) {
                list.add(new Driver(rs.getInt("id"), rs.getString("name")));
            }
        }
        return list;
    }

    /** Deleting a driver also deletes their cars (ON DELETE CASCADE). */
    public boolean delete(int id) throws SQLException {
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement("DELETE FROM drivers WHERE id = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }
}
