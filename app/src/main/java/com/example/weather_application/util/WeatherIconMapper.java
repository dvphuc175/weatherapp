package com.example.weather_application.util;

import androidx.annotation.Nullable;
import androidx.annotation.RawRes;

import com.example.weather_application.R;

public final class WeatherIconMapper {

    private WeatherIconMapper() {}

    @RawRes
    public static int rawForIconCode(@Nullable String iconCode) {
        if (iconCode == null) {
            return R.raw.weather_partly_cloudy;
        }
        switch (iconCode) {
            case "01d":
                return R.raw.weather_sunny;
            case "01n":
                return R.raw.weather_night;
            case "02d":
            case "03d":
            case "04d":
                return R.raw.weather_partly_cloudy;
            case "02n":
            case "03n":
            case "04n":
                return R.raw.weather_cloudy_night;
            case "09d":
            case "10d":
                return R.raw.weather_partly_shower;
            case "09n":
            case "10n":
                return R.raw.weather_rainy_night;
            case "11d":
            case "11n":
                return R.raw.weather_thunder;
            case "13d":
                return R.raw.weather_snow_sunny;
            case "13n":
                return R.raw.weather_snow_night;
            case "50d":
            case "50n":
                return R.raw.weather_mist;
            default:
                return R.raw.weather_partly_cloudy;
        }
    }
}
