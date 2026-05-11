package com.example.weather_application.data.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * A city the user has pinned to the multi-city pager. {@code cityName} is the primary key —
 * re-adding the same city is a no-op. {@code displayOrder} sets the position in the pager
 * (lowest first, ties broken by {@code addedAt} ASC so older pins sit on the left).
 */
@Entity(tableName = "saved_city")
public class SavedCity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "city_name")
    public String cityName;

    @ColumnInfo(name = "display_order")
    public int displayOrder;

    @ColumnInfo(name = "added_at")
    public long addedAt;

    public SavedCity(@NonNull String cityName, int displayOrder, long addedAt) {
        this.cityName = cityName;
        this.displayOrder = displayOrder;
        this.addedAt = addedAt;
    }
}
