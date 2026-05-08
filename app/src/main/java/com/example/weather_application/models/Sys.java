package com.example.weather_application.models;

import com.google.gson.annotations.SerializedName;

public class Sys {
    /** Sunrise time, Unix seconds (UTC). */
    @SerializedName("sunrise")
    private long sunrise;

    /** Sunset time, Unix seconds (UTC). */
    @SerializedName("sunset")
    private long sunset;

    public long getSunrise() { return sunrise; }
    public long getSunset() { return sunset; }
}
