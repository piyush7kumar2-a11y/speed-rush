package com.speedrush.model;

/** Stats are 1-10. driverName is filled when loaded from the DB (null for new cars). */
public record Car(int id, String name, int driverId, String driverName,
                  int topSpeed, int acceleration, int handling) {}
