package com.example.weather_application.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class WeatherResponse {
    @SerializedName("main")
    private CurrentWeather main;

    @SerializedName("weather")
    private List<WeatherDescription> weather;

    @SerializedName("name")
    private String name;

    @SerializedName("wind")
    private Wind wind;

    @SerializedName("sys")
    private Sys sys;

    /** Visibility in meters. -1 / 0 are treated as missing by the UI. */
    @SerializedName("visibility")
    private int visibility;

    /** City timezone offset from UTC, in seconds. Needed to render sunrise/sunset in city local time. */
    @SerializedName("timezone")
    private int timezone;

    public CurrentWeather getMain() { return main; }
    public List<WeatherDescription> getWeather() { return weather; }
    public String getName() { return name; }
    public Wind getWind() { return wind; }
    public Sys getSys() { return sys; }
    public int getVisibility() { return visibility; }
    public int getTimezone() { return timezone; }
}
