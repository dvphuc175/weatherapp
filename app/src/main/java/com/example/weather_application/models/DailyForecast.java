package com.example.weather_application.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Aggregated per-day forecast computed from the 3-hour {@link ForecastItem} list. Immutable.
 */
public final class DailyForecast {

    /** Midnight (00:00) of this forecast's local day, expressed as Unix milliseconds (UTC clock). */
    private final long localDayStartUtcMillis;
    private final double minTempCelsius;
    private final double maxTempCelsius;
    @Nullable private final String iconCode;
    @Nullable private final String description;
    /** 0.0–1.0; the most pessimistic precipitation probability seen across the day. */
    private final double maxPop;

    public DailyForecast(long localDayStartUtcMillis,
                         double minTempCelsius,
                         double maxTempCelsius,
                         @Nullable String iconCode,
                         @Nullable String description,
                         double maxPop) {
        this.localDayStartUtcMillis = localDayStartUtcMillis;
        this.minTempCelsius = minTempCelsius;
        this.maxTempCelsius = maxTempCelsius;
        this.iconCode = iconCode;
        this.description = description;
        this.maxPop = maxPop;
    }

    public long getLocalDayStartUtcMillis() { return localDayStartUtcMillis; }
    public double getMinTempCelsius() { return minTempCelsius; }
    public double getMaxTempCelsius() { return maxTempCelsius; }
    @Nullable public String getIconCode() { return iconCode; }
    @Nullable public String getDescription() { return description; }
    public double getMaxPop() { return maxPop; }

    @NonNull
    @Override
    public String toString() {
        return "DailyForecast{day=" + localDayStartUtcMillis
                + ", min=" + minTempCelsius + ", max=" + maxTempCelsius
                + ", icon=" + iconCode + ", pop=" + maxPop + '}';
    }
}
