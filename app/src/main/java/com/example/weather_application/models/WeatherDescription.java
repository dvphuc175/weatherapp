package com.example.weather_application.models;

import com.google.gson.annotations.SerializedName;

public class WeatherDescription {
    @SerializedName("main")
    private String main; // Ví dụ: "Rain", "Clouds", "Clear"

    @SerializedName("description")
    private String description; // Ví dụ: "mưa rào nhẹ"

    @SerializedName("icon")
    private String icon; // Mã icon để load hình ảnh bằng Glide

    public String getMain() {
        return main;
    }

    public String getDescription() {
        return description;
    }

    public String getIcon() {
        return icon;
    }
}
