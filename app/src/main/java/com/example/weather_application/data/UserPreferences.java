package com.example.weather_application.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.example.weather_application.util.TemperatureUnit;

/**
 * Thin wrapper around {@link SharedPreferences} for user-tweakable knobs (currently only the
 * temperature unit). Singleton-by-application-context so anywhere in the app can read/write
 * without threading worries — {@link SharedPreferences} is itself thread-safe.
 */
public final class UserPreferences {

    private static final String PREFS_NAME = "user_prefs";
    private static final String KEY_TEMPERATURE_UNIT = "temperature_unit";

    private static volatile UserPreferences INSTANCE;

    private final SharedPreferences prefs;

    private UserPreferences(@NonNull Context appContext) {
        this.prefs = appContext.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    @NonNull
    public static UserPreferences get(@NonNull Context appContext) {
        UserPreferences local = INSTANCE;
        if (local == null) {
            synchronized (UserPreferences.class) {
                local = INSTANCE;
                if (local == null) {
                    local = new UserPreferences(appContext);
                    INSTANCE = local;
                }
            }
        }
        return local;
    }

    @NonNull
    public TemperatureUnit getTemperatureUnit() {
        String stored = prefs.getString(KEY_TEMPERATURE_UNIT, null);
        return TemperatureUnit.fromKey(stored);
    }

    public void setTemperatureUnit(@NonNull TemperatureUnit unit) {
        prefs.edit().putString(KEY_TEMPERATURE_UNIT, unit.key).apply();
    }

    public void registerListener(@NonNull SharedPreferences.OnSharedPreferenceChangeListener listener) {
        prefs.registerOnSharedPreferenceChangeListener(listener);
    }

    public void unregisterListener(@NonNull SharedPreferences.OnSharedPreferenceChangeListener listener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener);
    }

    public static String keyTemperatureUnit() {
        return KEY_TEMPERATURE_UNIT;
    }
}
