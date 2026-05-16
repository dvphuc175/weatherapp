package com.example.weather_application.models;

import com.google.gson.annotations.SerializedName;

/** One air-quality measurement returned by OpenWeather. */
public class AirQualityItem {
    @SerializedName("main")
    private AirQualityMain main;

    @SerializedName("components")
    private AirQualityComponents components;

    @SerializedName("dt")
    private long dt;

    public AirQualityMain getMain() { return main; }
    public AirQualityComponents getComponents() { return components; }
    public long getDt() { return dt; }
}
