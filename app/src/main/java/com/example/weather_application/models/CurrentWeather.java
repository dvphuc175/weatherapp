package com.example.weather_application.models;

import com.google.gson.annotations.SerializedName;

public class CurrentWeather {
    @SerializedName("temp")
    private double temp;

    @SerializedName("humidity")
    private int humidity;

    @SerializedName("feels_like")
    private double feelsLike;

    public double getTemp() { return temp; }
    public int getHumidity() { return humidity; }

    public double getFeelsLike() { return feelsLike; }
}