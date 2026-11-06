package com.beitmama.model;

public class MotherLocation {
    private final int id;
    private final String name;
    private final String region;
    private final String locationUrl;
    private final double latitude;
    private final double longitude;

    public MotherLocation(int id, String name, String region, String locationUrl, double latitude, double longitude) {
        this.id = id;
        this.name = name;
        this.region = region;
        this.locationUrl = locationUrl;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getRegion() { return region; }
    public String getLocationUrl() { return locationUrl; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
}
