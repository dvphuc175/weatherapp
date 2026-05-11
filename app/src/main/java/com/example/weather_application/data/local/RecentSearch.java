package com.example.weather_application.data.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * One row per city the user has successfully searched. {@code cityName} is the primary key —
 * re-searching the same city updates {@code lastQueriedAt} instead of inserting a duplicate.
 */
@Entity(tableName = "recent_search")
public class RecentSearch {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "city_name")
    public String cityName;

    @ColumnInfo(name = "last_queried_at")
    public long lastQueriedAt;

    public RecentSearch(@NonNull String cityName, long lastQueriedAt) {
        this.cityName = cityName;
        this.lastQueriedAt = lastQueriedAt;
    }
}
