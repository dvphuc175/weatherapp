package com.example.weather_application.data.local;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/**
 * Application-scoped Room database. Bumped to v2 in G7 to add {@link SavedCity}. The blobs in
 * {@link CachedSnapshot} are throwaway and the recent_search list is rebuildable from user
 * actions, so {@code fallbackToDestructiveMigration()} is acceptable.
 */
@Database(
        entities = {RecentSearch.class, CachedSnapshot.class, SavedCity.class},
        version = 2,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static final String DB_NAME = "weather_app.db";
    private static volatile AppDatabase INSTANCE;

    public abstract RecentSearchDao recentSearchDao();

    public abstract CachedSnapshotDao cachedSnapshotDao();

    public abstract SavedCityDao savedCityDao();

    @NonNull
    public static AppDatabase get(@NonNull Context appContext) {
        AppDatabase local = INSTANCE;
        if (local == null) {
            synchronized (AppDatabase.class) {
                local = INSTANCE;
                if (local == null) {
                    local = Room.databaseBuilder(
                                    appContext.getApplicationContext(),
                                    AppDatabase.class,
                                    DB_NAME)
                            .fallbackToDestructiveMigration()
                            .build();
                    INSTANCE = local;
                }
            }
        }
        return local;
    }
}
