package com.example.weather_application.util;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Maps an OpenWeatherMap icon code (e.g. {@code "10n"}) to a 3-stop background gradient
 * drawn from top to bottom. Designed so every gradient stays dark enough for the global
 * white text/icons to remain readable.
 */
public final class WeatherGradientMapper {

    private WeatherGradientMapper() {}

    /**
     * @return {@code int[3]} of {@link androidx.annotation.ColorInt} values
     *         (start = top, center, end = bottom). Never {@code null}.
     */
    @NonNull
    @ColorInt
    public static int[] colorsForIconCode(@Nullable String iconCode) {
        if (iconCode == null) {
            return CLEAR_NIGHT;
        }
        switch (iconCode) {
            case "01d":
                return CLEAR_DAY;
            case "01n":
                return CLEAR_NIGHT;
            case "02d":
            case "03d":
            case "04d":
                return CLOUDS_DAY;
            case "02n":
            case "03n":
            case "04n":
                return CLOUDS_NIGHT;
            case "09d":
            case "10d":
                return RAIN_DAY;
            case "09n":
            case "10n":
                return RAIN_NIGHT;
            case "11d":
            case "11n":
                return THUNDER;
            case "13d":
                return SNOW_DAY;
            case "13n":
                return SNOW_NIGHT;
            case "50d":
            case "50n":
                return MIST;
            default:
                return CLEAR_NIGHT;
        }
    }

    // Clear sky — daytime: deep sky blue → mid blue → soft cyan.
    @ColorInt
    private static final int[] CLEAR_DAY = {
            0xFF1E3A8A, 0xFF2563EB, 0xFF60A5FA
    };

    // Clear sky — night: original brand gradient (dark indigo).
    @ColorInt
    private static final int[] CLEAR_NIGHT = {
            0xFF0F0C29, 0xFF302B63, 0xFF24243E
    };

    // Few/scattered/broken clouds — day: muted slate-blue.
    @ColorInt
    private static final int[] CLOUDS_DAY = {
            0xFF3B4A60, 0xFF556579, 0xFF7388A0
    };

    // Few/scattered/broken clouds — night: dark slate-blue.
    @ColorInt
    private static final int[] CLOUDS_NIGHT = {
            0xFF1A1F2E, 0xFF2D3548, 0xFF1A2030
    };

    // Rain — day: navy with cool steel-blue accent.
    @ColorInt
    private static final int[] RAIN_DAY = {
            0xFF1E3A5F, 0xFF2C4F73, 0xFF4A6F95
    };

    // Rain — night: deep navy.
    @ColorInt
    private static final int[] RAIN_NIGHT = {
            0xFF0E1B2C, 0xFF182B40, 0xFF0E1B2C
    };

    // Thunderstorm — almost-black with a hint of indigo.
    @ColorInt
    private static final int[] THUNDER = {
            0xFF14141E, 0xFF1F1F33, 0xFF0A0A12
    };

    // Snow — day: muted blue-gray, slightly lighter than clouds_day.
    @ColorInt
    private static final int[] SNOW_DAY = {
            0xFF4A6794, 0xFF6F8FBC, 0xFF8AA8C9
    };

    // Snow — night: cold dark blue-gray.
    @ColorInt
    private static final int[] SNOW_NIGHT = {
            0xFF1F2D45, 0xFF2C3E5C, 0xFF1F2D45
    };

    // Mist / fog / haze — muted gray-purple, day and night the same.
    @ColorInt
    private static final int[] MIST = {
            0xFF3A3F4B, 0xFF4F5663, 0xFF3A3F4B
    };
}
