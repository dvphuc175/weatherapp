package com.example.weather_application.ui;

import android.app.Application;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.weather_application.data.UserPreferences;
import com.example.weather_application.data.local.AppDatabase;
import com.example.weather_application.data.local.CachedSnapshotDao;
import com.example.weather_application.data.local.RecentSearch;
import com.example.weather_application.data.local.RecentSearchDao;
import com.example.weather_application.data.local.SavedCity;
import com.example.weather_application.data.local.SavedCityDao;
import com.example.weather_application.util.TemperatureUnit;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Activity-scoped ViewModel. Owns the cross-page concerns: which cities are pinned, the active
 * temperature unit, the recent-search list, and the current GPS coord. Per-page weather state
 * lives in {@link CityViewModel}.
 *
 * <p>The unit-pref listener is the central place that wipes the offline cache when the user
 * flips °C/°F — individual {@link CityViewModel} instances just react to the change by reloading.
 */
public class MainViewModel extends AndroidViewModel {

    private final UserPreferences userPreferences;
    private final RecentSearchDao recentSearchDao;
    private final SavedCityDao savedCityDao;
    private final CachedSnapshotDao cachedSnapshotDao;
    private final ExecutorService diskIo = Executors.newSingleThreadExecutor();

    private final MutableLiveData<TemperatureUnit> temperatureUnit = new MutableLiveData<>();
    private final MutableLiveData<LatLon> currentLocation = new MutableLiveData<>();
    private final LiveData<List<RecentSearch>> recentSearches;
    private final LiveData<List<SavedCity>> savedCities;

    @NonNull
    private final SharedPreferences.OnSharedPreferenceChangeListener unitListener;

    public MainViewModel(@NonNull Application application) {
        super(application);
        AppDatabase db = AppDatabase.get(application);
        this.userPreferences = UserPreferences.get(application);
        this.recentSearchDao = db.recentSearchDao();
        this.savedCityDao = db.savedCityDao();
        this.cachedSnapshotDao = db.cachedSnapshotDao();
        this.recentSearches = recentSearchDao.observeRecent(RecentSearchDao.MAX_ENTRIES);
        this.savedCities = savedCityDao.observeOrdered();
        this.temperatureUnit.setValue(userPreferences.getTemperatureUnit());
        this.unitListener = (sp, key) -> {
            if (UserPreferences.keyTemperatureUnit().equals(key)) {
                TemperatureUnit next = this.userPreferences.getTemperatureUnit();
                temperatureUnit.postValue(next);
                // Cache is keyed on the old units= param. Wipe it so each page falls through
                // to a fresh fetch with the new unit.
                diskIo.execute(cachedSnapshotDao::deleteAll);
            }
        };
        userPreferences.registerListener(unitListener);
    }

    @NonNull
    public LiveData<TemperatureUnit> getTemperatureUnit() {
        return temperatureUnit;
    }

    @NonNull
    public LiveData<List<RecentSearch>> getRecentSearches() {
        return recentSearches;
    }

    @NonNull
    public LiveData<List<SavedCity>> getSavedCities() {
        return savedCities;
    }

    @NonNull
    public LiveData<LatLon> getCurrentLocation() {
        return currentLocation;
    }

    /** Called by the activity once the FusedLocation API returns a fix. */
    public void setCurrentLocation(double lat, double lon) {
        currentLocation.setValue(new LatLon(lat, lon));
    }

    /**
     * Record a successful search. Bumps {@code lastQueriedAt} (or inserts if new) and evicts
     * beyond the cap. Called by the activity after any successful city load from the search bar
     * or a recent chip — the per-page {@link CityViewModel} doesn't know about recent searches.
     */
    public void recordRecentSearch(@Nullable String cityName) {
        if (cityName == null || cityName.isEmpty()) return;
        diskIo.execute(() -> {
            recentSearchDao.upsert(new RecentSearch(cityName, System.currentTimeMillis()));
            List<RecentSearch> all = recentSearchDao.getAllSync();
            for (int i = RecentSearchDao.MAX_ENTRIES; i < all.size(); i++) {
                recentSearchDao.deleteByName(all.get(i).cityName);
            }
        });
    }

    public void clearRecentSearches() {
        diskIo.execute(recentSearchDao::deleteAll);
    }

    public void removeRecentSearch(@NonNull String cityName) {
        diskIo.execute(() -> recentSearchDao.deleteByName(cityName));
    }

    /**
     * Pin a new city to the pager. {@code displayOrder} is set to {@code current count + 1} so
     * it lands at the end of the list. No-op if the cap is reached or the city is already pinned.
     */
    public void addSavedCity(@NonNull String cityName) {
        if (cityName.isEmpty()) return;
        diskIo.execute(() -> {
            if (savedCityDao.countByName(cityName) > 0) return;
            int count = savedCityDao.count();
            if (count >= SavedCityDao.MAX_ENTRIES) return;
            savedCityDao.upsert(new SavedCity(cityName, count + 1, System.currentTimeMillis()));
        });
    }

    /** Async existence check — caller is notified on the disk-IO thread. */
    public void hasSavedCity(@NonNull String cityName, @NonNull SavedCityResultCallback result) {
        diskIo.execute(() -> result.onResult(savedCityDao.countByName(cityName) > 0));
    }

    public void removeSavedCity(@NonNull String cityName) {
        diskIo.execute(() -> savedCityDao.deleteByName(cityName));
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        userPreferences.unregisterListener(unitListener);
        diskIo.shutdown();
    }

    /** Immutable lat/lon pair, posted whenever the activity gets a new GPS fix. */
    public static final class LatLon {
        public final double lat;
        public final double lon;

        public LatLon(double lat, double lon) {
            this.lat = lat;
            this.lon = lon;
        }
    }

    /** Functional callback for {@link #hasSavedCity}. Avoids a {@code java.util.function} dep. */
    public interface SavedCityResultCallback {
        void onResult(boolean exists);
    }
}
