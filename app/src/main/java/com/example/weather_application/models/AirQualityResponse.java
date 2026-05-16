package com.example.weather_application.models;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/** Response from OpenWeather Air Pollution API. */
public class AirQualityResponse {
    @SerializedName("list")
    private List<AirQualityItem> list;

    public List<AirQualityItem> getList() { return list; }
}
