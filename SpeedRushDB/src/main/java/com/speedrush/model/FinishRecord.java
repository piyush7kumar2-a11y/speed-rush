package com.speedrush.model;

public record FinishRecord(int carId, String carName, String driverName,
                           int position, long timeMs, int points) {}
