package com.example.weather_application.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/** Recent-searches DAO. The {@code MAX_ENTRIES} ceiling is enforced after every insert. */
@Dao
public interface RecentSearchDao {

    int MAX_ENTRIES = 5;

    /** Live, ordered by most recent first. Limit clamps for safety. */
    @Query("SELECT * FROM recent_search ORDER BY last_queried_at DESC LIMIT :limit")
    LiveData<List<RecentSearch>> observeRecent(int limit);

    /** Synchronous read used by the eviction step. */
    @Query("SELECT * FROM recent_search ORDER BY last_queried_at DESC")
    List<RecentSearch> getAllSync();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(RecentSearch entry);

    @Query("DELETE FROM recent_search WHERE city_name = :cityName")
    void deleteByName(String cityName);

    @Query("DELETE FROM recent_search")
    void deleteAll();
}
