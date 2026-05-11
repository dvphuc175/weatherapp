package com.example.weather_application.data.local;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/**
 * Application-scoped Room database. Schema is in version 1; if we ever add columns we'll
 * write a {@code Migration}. The blobs in {@link CachedSnapshot} are throwaway cache data
 * so {@code fallbackToDestructiveMigration()} is fine here.
 */
@Database(
        entities = {RecentSearch.class, CachedSnapshot.class},
        version = 1,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static final String DB_NAME = "weather_app.db";
    private static volatile AppDatabase INSTANCE;

    public abstract RecentSearchDao recentSearchDao();

    public abstract CachedSnapshotDao cachedSnapshotDao();

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
