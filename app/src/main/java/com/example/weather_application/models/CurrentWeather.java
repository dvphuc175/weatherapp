package com.example.weather_application.models;

import com.google.gson.annotations.SerializedName;

public class CurrentWeather {
    @SerializedName("temp")
    private double temp;

    @SerializedName("humidity")
    private int humidity;

    @SerializedName("feels_like")
    private double feelsLike;

    /** Atmospheric pressure at sea level, hPa. Only set on current weather, missing on forecast items. */
    @SerializedName("pressure")
    private int pressure;

    public double getTemp() { return temp; }
    public int getHumidity() { return humidity; }
    public double getFeelsLike() { return feelsLike; }
    public int getPressure() { return pressure; }
}
