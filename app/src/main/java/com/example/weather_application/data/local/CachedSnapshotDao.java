package com.example.weather_application.data.local;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface CachedSnapshotDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(CachedSnapshot snapshot);

    @Nullable
    @Query("SELECT * FROM cached_snapshot WHERE id = :id LIMIT 1")
    CachedSnapshot getByIdSync(String id);

    @Query("DELETE FROM cached_snapshot")
    void deleteAll();
}
