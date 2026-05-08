package com.example.weather_application.models;

import com.google.gson.annotations.SerializedName;

public class Wind {
    /** Wind speed. m/s when units=metric. */
    @SerializedName("speed")
    private double speed;

    /** Wind direction in meteorological degrees (0..360, where 0/360 = N). */
    @SerializedName("deg")
    private int deg;

    public double getSpeed() { return speed; }
    public int getDeg() { return deg; }
}
