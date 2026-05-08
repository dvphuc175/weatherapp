package com.example.weather_application.models;

import com.google.gson.annotations.SerializedName;

public class WeatherAlert {
    @SerializedName("sender_name")
    private String senderName;

    @SerializedName("event")
    private String event; // Ví dụ: "Thunderstorm warning" (Cảnh báo bão)

    @SerializedName("description")
    private String description;

    public String getEvent() {
        return event;
    }

    public String getDescription() {
        return description;
    }
}
