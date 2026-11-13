package com.speedrush.model;

public record DriverStats(String driverName, int races, int wins, int podiums,
                          int points, double avgPosition) {}
