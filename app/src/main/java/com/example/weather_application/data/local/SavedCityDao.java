package com.example.weather_application.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/**
 * Saved-city DAO. We cap at {@link #MAX_ENTRIES} to keep the pager swipeable on one hand;
 * the cap is enforced in the ViewModel after every insert. Sort order is
 * {@code displayOrder ASC, added_at ASC} so the user's first pin shows up on the left.
 */
@Dao
public interface SavedCityDao {

    int MAX_ENTRIES = 10;

    /** Live, ordered for the pager. */
    @Query("SELECT * FROM saved_city ORDER BY display_order ASC, added_at ASC")
    LiveData<List<SavedCity>> observeOrdered();

    /** Synchronous read used by the cap-enforcement step. */
    @Query("SELECT * FROM saved_city ORDER BY display_order ASC, added_at ASC")
    List<SavedCity> getAllSync();

    /** Synchronous existence check used before insert to avoid bumping {@code addedAt}. */
    @Query("SELECT COUNT(*) FROM saved_city WHERE city_name = :cityName")
    int countByName(String cityName);

    @Query("SELECT COUNT(*) FROM saved_city")
    int count();

    /** Replace-on-conflict makes re-adding a city a no-op (re-issuing same PK). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(SavedCity entry);

    @Query("DELETE FROM saved_city WHERE city_name = :cityName")
    void deleteByName(String cityName);

    @Query("DELETE FROM saved_city")
    void deleteAll();
}
