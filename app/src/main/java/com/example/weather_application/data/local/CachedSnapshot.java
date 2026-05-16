package com.example.weather_application.data.local;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * One successfully-loaded weather + forecast snapshot, serialized as Gson JSON. Snapshots are
 * keyed by the query identity plus units (for example, {@code city:hanoi:metric} or
 * {@code gps:21.0285,105.8542:metric}) so each pager page can fall back to its own cache instead
 * of being limited to the last city fetched. AQI is intentionally fetched live and not part of
 * this Room row so a non-critical widget never blocks the core weather cache.
 */
@Entity(tableName = "cached_snapshot")
public class CachedSnapshot {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    public String id;

    /** Pretty city name for debugging / future offline UI. {@code null} when the API omits it. */
    @Nullable
    @ColumnInfo(name = "city_name")
    public String cityName;

    /** OWM units flag the data was fetched with: {@code "metric"} or {@code "imperial"}. */
    @NonNull
    @ColumnInfo(name = "units")
    public String units;

    /** Gson-serialized {@link com.example.weather_application.models.WeatherResponse}. */
    @Nullable
    @ColumnInfo(name = "weather_json")
    public String weatherJson;

    /** Gson-serialized {@link com.example.weather_application.models.ForecastResponse}. */
    @Nullable
    @ColumnInfo(name = "forecast_json")
    public String forecastJson;

    @ColumnInfo(name = "saved_at")
    public long savedAt;

    public CachedSnapshot(@NonNull String id, @Nullable String cityName, @NonNull String units,
                          @Nullable String weatherJson, @Nullable String forecastJson,
                          long savedAt) {
        this.id = id;
        this.cityName = cityName;
        this.units = units;
        this.weatherJson = weatherJson;
        this.forecastJson = forecastJson;
        this.savedAt = savedAt;
    }
}
