package com.example.weather_application.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class ForecastItem {
    @SerializedName("dt_txt")
    private String dtTxt; // Trả về thời gian, VD: "2023-10-25 12:00:00"

    @SerializedName("main")
    private CurrentWeather main; // Lấy lại class chứa nhiệt độ

    @SerializedName("weather")
    private List<WeatherDescription> weather; // Lấy lại class chứa icon

    /** Probability of precipitation, 0.0–1.0. May be missing on older API responses. */
    @SerializedName("pop")
    private Double pop;

    public String getDtTxt() { return dtTxt; }
    public CurrentWeather getMain() { return main; }
    public List<WeatherDescription> getWeather() { return weather; }
    public Double getPop() { return pop; }
}
