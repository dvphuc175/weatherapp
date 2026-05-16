package com.example.weather_application.models;

import com.google.gson.annotations.SerializedName;

/** Geographic coordinate returned by OpenWeather current-weather responses. */
public class Coord {
    @SerializedName("lat")
    private double lat;

    @SerializedName("lon")
    private double lon;

    public double getLat() { return lat; }
    public double getLon() { return lon; }
}
