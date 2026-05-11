package com.example.weather_application.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Temperature unit the app currently renders in. {@link #owmUnitsParam} drives the OWM
 * {@code units} query parameter so the API returns values in the right unit directly — we
 * don't convert client-side.
 */
public enum TemperatureUnit {

    CELSIUS("metric", "metric", "°C", "m/s"),
    FAHRENHEIT("imperial", "imperial", "°F", "mph");

    /** Persisted key in SharedPreferences. */
    @NonNull
    public final String key;

    /** The {@code units=} value sent to OpenWeatherMap. */
    @NonNull
    public final String owmUnitsParam;

    /** Suffix for temperatures, e.g. {@code "°C"} or {@code "°F"}. */
    @NonNull
    public final String temperatureSuffix;

    /** Suffix for wind speed, e.g. {@code "m/s"} or {@code "mph"}. */
    @NonNull
    public final String windSuffix;

    TemperatureUnit(@NonNull String key, @NonNull String owmUnitsParam,
                    @NonNull String temperatureSuffix, @NonNull String windSuffix) {
        this.key = key;
        this.owmUnitsParam = owmUnitsParam;
        this.temperatureSuffix = temperatureSuffix;
        this.windSuffix = windSuffix;
    }

    @NonNull
    public static TemperatureUnit fromKey(@Nullable String key) {
        if (FAHRENHEIT.key.equals(key)) {
            return FAHRENHEIT;
        }
        return CELSIUS;
    }
}
