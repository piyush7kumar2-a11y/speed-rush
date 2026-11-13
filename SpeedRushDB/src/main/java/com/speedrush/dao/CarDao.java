package com.speedrush.dao;

import com.speedrush.db.Database;
import com.speedrush.model.Car;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CarDao {

    private static final String SELECT = """
            SELECT c.id, c.name, c.driver_id, d.name AS driver_name,
                   c.top_speed, c.acceleration, c.handling
            FROM cars c JOIN drivers d ON d.id = c.driver_id
            """;

    public int insert(Car car) throws SQLException {
        String sql = "INSERT INTO cars(name, driver_id, top_speed, acceleration, handling) VALUES (?,?,?,?,?)";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, car.name());
            ps.setInt(2, car.driverId());
            ps.setInt(3, car.topSpeed());
            ps.setInt(4, car.acceleration());
            ps.setInt(5, car.handling());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    public List<Car> findAll() throws SQLException {
        List<Car> list = new ArrayList<>();
        try (Connection con = Database.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(SELECT + " ORDER BY c.id")) {
            while (rs.next()) {
                list.add(map(rs));
            }
        }
        return list;
    }

    public boolean delete(int id) throws SQLException {
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement("DELETE FROM cars WHERE id = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    private Car map(ResultSet rs) throws SQLException {
        return new Car(rs.getInt("id"), rs.getString("name"), rs.getInt("driver_id"),
                rs.getString("driver_name"), rs.getInt("top_speed"),
                rs.getInt("acceleration"), rs.getInt("handling"));
    }
}
