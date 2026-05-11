package com.example.weather_application.data.local;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * The last successfully-loaded weather + forecast, serialized as Gson JSON. We keep exactly one
 * row (PK = "current") and rewrite it on every successful network load. The {@code units} column
 * lets us avoid mixing °C and °F in the same blob — if the user flips the unit, the cache is
 * invalidated and the next fetch refreshes it.
 */
@Entity(tableName = "cached_snapshot")
public class CachedSnapshot {

    /** Sentinel primary key — there's only ever one row. */
    public static final String SINGLE_ROW_KEY = "current";

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    public String id;

    /** Pretty city name for the offline banner. {@code null} when the cache is empty. */
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
