package com.example.weather_application.util;

import androidx.annotation.NonNull;

import java.util.Locale;

/**
 * Format temperatures + wind speeds with the suffix that matches the currently-selected
 * {@link TemperatureUnit}. Values are passed in already in the right unit — OWM returns
 * °C or °F (and m/s or mph) directly depending on {@code units=metric|imperial}, so this
 * class never converts.
 */
public final class UnitFormatter {

    private UnitFormatter() {}

    /** {@code "26°C"} / {@code "78°F"}. */
    @NonNull
    public static String formatTemperatureRounded(double value, @NonNull TemperatureUnit unit) {
        return String.format(Locale.getDefault(), "%d%s",
                Math.round(value), unit.temperatureSuffix);
    }

    /** {@code "3.4 m/s"} / {@code "7.6 mph"}. */
    @NonNull
    public static String formatWind(double speed, @NonNull TemperatureUnit unit) {
        return String.format(Locale.getDefault(), "%.1f %s",
                speed, unit.windSuffix);
    }

    /** {@code "22° / 30°"} — used by the daily row where we already render both ends. */
    @NonNull
    public static String formatTemperatureRange(double min, double max) {
        return String.format(Locale.getDefault(), "%d° / %d°",
                Math.round(min), Math.round(max));
    }

    /** {@code "--°C"} placeholder while loading. */
    @NonNull
    public static String temperaturePlaceholder(@NonNull TemperatureUnit unit) {
        return "--" + unit.temperatureSuffix;
    }
}
