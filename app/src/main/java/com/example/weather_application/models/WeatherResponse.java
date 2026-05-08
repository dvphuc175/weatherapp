package com.example.weather_application.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class WeatherResponse {
    @SerializedName("main")
    private CurrentWeather main;

    @SerializedName("weather")
    private List<WeatherDescription> weather;

    // THÊM BIẾN NÀY VÀO
    @SerializedName("name")
    private String name;

    public CurrentWeather getMain() { return main; }
    public List<WeatherDescription> getWeather() { return weather; }

    // THÊM HÀM GET NÀY VÀO
    public String getName() { return name; }
}