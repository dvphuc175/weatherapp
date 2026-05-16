package com.example.weather_application.models;

import com.google.gson.annotations.SerializedName;

/** Air quality index wrapper. OpenWeather uses a 1 (good) to 5 (very poor) scale. */
public class AirQualityMain {
    @SerializedName("aqi")
    private int aqi;

    public int getAqi() { return aqi; }
}
