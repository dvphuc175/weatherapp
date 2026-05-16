package com.example.weather_application.models;

import com.google.gson.annotations.SerializedName;

/** Pollutant concentrations from OpenWeather Air Pollution API, in μg/m³. */
public class AirQualityComponents {
    @SerializedName("pm2_5")
    private double pm25;

    @SerializedName("pm10")
    private double pm10;

    public double getPm25() { return pm25; }
    public double getPm10() { return pm10; }
}
