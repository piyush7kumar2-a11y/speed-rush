package com.speedrush.model;

/** Fastest recorded time on one track. */
public record TrackRecord(String trackName, int lengthM, long timeMs, String driverName, String carName) {}
