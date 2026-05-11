package com.example.weather_application.ui;

import android.app.Application;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.weather_application.data.UserPreferences;
import com.example.weather_application.data.WeatherRepository;
import com.example.weather_application.data.WeatherRepository.Cancellable;
import com.example.weather_application.data.WeatherRepository.ResultCallback;
import com.example.weather_application.data.local.AppDatabase;
import com.example.weather_application.data.local.CachedSnapshot;
import com.example.weather_application.data.local.CachedSnapshotDao;
import com.example.weather_application.data.local.RecentSearch;
import com.example.weather_application.data.local.RecentSearchDao;
import com.example.weather_application.models.ForecastItem;
import com.example.weather_application.models.ForecastResponse;
import com.example.weather_application.models.WeatherResponse;
import com.example.weather_application.util.TemperatureUnit;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainViewModel extends AndroidViewModel {

    private final WeatherRepository repository;
    private final UserPreferences userPreferences;
    private final RecentSearchDao recentSearchDao;
    private final CachedSnapshotDao cachedSnapshotDao;
    private final ExecutorService diskIo = Executors.newSingleThreadExecutor();
    private final Gson gson = new Gson();

    private final MutableLiveData<WeatherUiState> weather = new MutableLiveData<>();
    private final MutableLiveData<ForecastUiState> forecast = new MutableLiveData<>();
    /** Live list of recent successful searches, most-recent-first. */
    private final LiveData<List<RecentSearch>> recentSearches;
    /** Live current temperature unit so the UI can refresh when the user flips it from Settings. */
    private final MutableLiveData<TemperatureUnit> temperatureUnit = new MutableLiveData<>();
    /** {@code true} when the currently-displayed data was loaded from the offline cache. */
    private final MutableLiveData<Boolean> servingFromCache = new MutableLiveData<>(false);
    /** Wall-clock ms when the cache row was saved. {@code null} unless serving from cache. */
    private final MutableLiveData<Long> cacheSavedAt = new MutableLiveData<>(null);

    private final List<Cancellable> inFlight = new ArrayList<>();

    /**
     * Monotonically increases on every {@link #loadByCoord}/{@link #loadByCity}/{@link #cancelInFlight}.
     * Callbacks captured before the bump are stale: they must drop their result instead of overwriting
     * a newer state. Guards against the race where a slow first request finishes after a fresh one.
     */
    private int loadGeneration;

    /** Last successful query, kept so {@link #refresh()} can re-issue it (e.g. pull-to-refresh, retry). */
    @Nullable
    private LastQuery lastQuery;

    /** Latest payloads, kept so we can snapshot to cache as a pair on the second arrival. */
    @Nullable
    private WeatherResponse latestWeather;
    @Nullable
    private ForecastResponse latestForecast;

    /** Listens for unit toggles in {@code SettingsBottomSheet}; assigned in the constructor so
     *  the lambda captures fully-initialized {@code userPreferences} / {@code cachedSnapshotDao}. */
    @NonNull
    private final SharedPreferences.OnSharedPreferenceChangeListener unitListener;

    public MainViewModel(@NonNull Application application) {
        this(application, new WeatherRepository(),
                UserPreferences.get(application),
                AppDatabase.get(application));
    }

    MainViewModel(@NonNull Application application,
                  @NonNull WeatherRepository repository,
                  @NonNull UserPreferences userPreferences,
                  @NonNull AppDatabase database) {
        super(application);
        this.repository = repository;
        this.userPreferences = userPreferences;
        this.recentSearchDao = database.recentSearchDao();
        this.cachedSnapshotDao = database.cachedSnapshotDao();
        this.recentSearches = recentSearchDao.observeRecent(RecentSearchDao.MAX_ENTRIES);
        this.temperatureUnit.setValue(userPreferences.getTemperatureUnit());
        this.unitListener = (sp, key) -> {
            if (UserPreferences.keyTemperatureUnit().equals(key)) {
                TemperatureUnit next = this.userPreferences.getTemperatureUnit();
                temperatureUnit.postValue(next);
                // Invalidate the cache — it's keyed to the old unit's units= param.
                diskIo.execute(cachedSnapshotDao::deleteAll);
                refresh();
            }
        };
        userPreferences.registerListener(unitListener);
    }

    @NonNull
    public LiveData<WeatherUiState> getWeather() {
        return weather;
    }

    @NonNull
    public LiveData<ForecastUiState> getForecast() {
        return forecast;
    }

    @NonNull
    public LiveData<List<RecentSearch>> getRecentSearches() {
        return recentSearches;
    }

    @NonNull
    public LiveData<TemperatureUnit> getTemperatureUnit() {
        return temperatureUnit;
    }

    @NonNull
    public LiveData<Boolean> getServingFromCache() {
        return servingFromCache;
    }

    @NonNull
    public LiveData<Long> getCacheSavedAt() {
        return cacheSavedAt;
    }

    public void loadByCoord(double lat, double lon) {
        lastQuery = LastQuery.coord(lat, lon);
        int gen = startNewGeneration();
        TemperatureUnit unit = currentUnit();
        weather.postValue(WeatherUiState.loading());
        forecast.postValue(ForecastUiState.loading());
        track(repository.getCurrentWeatherByCoord(lat, lon, unit, weatherCallback(true, gen)));
        track(repository.getForecastByCoord(lat, lon, unit, forecastCallback(gen)));
    }

    public void loadByCity(@NonNull String city) {
        lastQuery = LastQuery.city(city);
        int gen = startNewGeneration();
        TemperatureUnit unit = currentUnit();
        weather.postValue(WeatherUiState.loading());
        forecast.postValue(ForecastUiState.loading());
        track(repository.getCurrentWeatherByCity(city, unit, weatherCallback(false, gen)));
        track(repository.getForecastByCity(city, unit, forecastCallback(gen)));
    }

    /**
     * Re-issue the last successful query. Wired to pull-to-refresh, the inline error retry
     * button, and the network-regained callback. No-op if no query has been issued yet.
     */
    public void refresh() {
        LastQuery q = lastQuery;
        if (q == null) return;
        if (q.city != null) {
            loadByCity(q.city);
        } else {
            loadByCoord(q.lat, q.lon);
        }
    }

    /** Used by the Settings sheet's "Clear" action. Runs on a disk-I/O thread. */
    public void clearRecentSearches() {
        diskIo.execute(recentSearchDao::deleteAll);
    }

    /** Delete a single recent-search row — wired to the chip's close icon. */
    public void removeRecentSearch(@NonNull String cityName) {
        diskIo.execute(() -> recentSearchDao.deleteByName(cityName));
    }

    /** Tap on a recent chip → re-load. */
    public void loadRecent(@NonNull String cityName) {
        loadByCity(cityName);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        cancelInFlight();
        userPreferences.unregisterListener(unitListener);
        diskIo.shutdown();
    }

    @NonNull
    private TemperatureUnit currentUnit() {
        TemperatureUnit unit = temperatureUnit.getValue();
        return unit != null ? unit : userPreferences.getTemperatureUnit();
    }

    private synchronized int startNewGeneration() {
        cancelInFlight();
        latestWeather = null;
        latestForecast = null;
        return ++loadGeneration;
    }

    private synchronized boolean isStale(int gen) {
        return gen != loadGeneration;
    }

    private ResultCallback<WeatherResponse> weatherCallback(final boolean isCurrentLocation,
                                                            final int gen) {
        return new ResultCallback<WeatherResponse>() {
            @Override
            public void onSuccess(@NonNull WeatherResponse data) {
                if (isStale(gen)) return;
                servingFromCache.postValue(false);
                cacheSavedAt.postValue(null);
                latestWeather = data;
                weather.postValue(WeatherUiState.success(data, isCurrentLocation));
                recordRecentSearch(data.getName());
                persistSnapshotIfReady();
            }

            @Override
            public void onError(@Nullable String message) {
                if (isStale(gen)) return;
                tryServeFromCache(message, isCurrentLocation);
            }
        };
    }

    private ResultCallback<ForecastResponse> forecastCallback(final int gen) {
        return new ResultCallback<ForecastResponse>() {
            @Override
            public void onSuccess(@NonNull ForecastResponse data) {
                if (isStale(gen)) return;
                latestForecast = data;
                List<ForecastItem> items = data.getList();
                forecast.postValue(ForecastUiState.success(
                        items == null ? Collections.<ForecastItem>emptyList() : items));
                persistSnapshotIfReady();
            }

            @Override
            public void onError(@Nullable String message) {
                if (isStale(gen)) return;
                // If we already served weather from cache, the cached forecast was already
                // posted by tryServeFromCache() — suppress to keep the offline view intact.
                if (Boolean.TRUE.equals(servingFromCache.getValue())) {
                    return;
                }
                forecast.postValue(ForecastUiState.error(message == null ? "" : message));
            }
        };
    }

    /** Snapshot both halves to disk once we have a successful weather + forecast pair. */
    private void persistSnapshotIfReady() {
        final WeatherResponse w = latestWeather;
        final ForecastResponse f = latestForecast;
        if (w == null || f == null) return;
        final TemperatureUnit unit = currentUnit();
        final String cityName = w.getName();
        diskIo.execute(() -> cachedSnapshotDao.upsert(new CachedSnapshot(
                CachedSnapshot.SINGLE_ROW_KEY,
                cityName,
                unit.owmUnitsParam,
                gson.toJson(w),
                gson.toJson(f),
                System.currentTimeMillis())));
    }

    private void recordRecentSearch(@Nullable String cityName) {
        if (cityName == null || cityName.isEmpty()) return;
        diskIo.execute(() -> {
            recentSearchDao.upsert(new RecentSearch(cityName, System.currentTimeMillis()));
            // Evict beyond the cap, oldest first.
            List<RecentSearch> all = recentSearchDao.getAllSync();
            for (int i = RecentSearchDao.MAX_ENTRIES; i < all.size(); i++) {
                recentSearchDao.deleteByName(all.get(i).cityName);
            }
        });
    }

    /**
     * Network failed and we don't have data yet. Try to deserialize the last cached snapshot
     * (assuming its units match the current unit pref — if they don't, the cache was already
     * wiped by {@link #unitListener}). If anything is missing, fall through to a normal error.
     */
    private void tryServeFromCache(@Nullable String errorMessage,
                                   final boolean isCurrentLocation) {
        final TemperatureUnit currentUnit = currentUnit();
        diskIo.execute(() -> {
            CachedSnapshot snap = cachedSnapshotDao.getByIdSync(CachedSnapshot.SINGLE_ROW_KEY);
            if (snap == null || snap.weatherJson == null || snap.forecastJson == null
                    || !currentUnit.owmUnitsParam.equals(snap.units)) {
                weather.postValue(WeatherUiState.error(errorMessage == null ? "" : errorMessage));
                return;
            }
            try {
                WeatherResponse w = gson.fromJson(snap.weatherJson, WeatherResponse.class);
                ForecastResponse f = gson.fromJson(snap.forecastJson, ForecastResponse.class);
                if (w == null || f == null) {
                    weather.postValue(WeatherUiState.error(errorMessage == null ? "" : errorMessage));
                    return;
                }
                latestWeather = w;
                latestForecast = f;
                servingFromCache.postValue(true);
                cacheSavedAt.postValue(snap.savedAt);
                weather.postValue(WeatherUiState.success(w, isCurrentLocation));
                List<ForecastItem> items = f.getList();
                forecast.postValue(ForecastUiState.success(
                        items == null ? Collections.<ForecastItem>emptyList() : items));
            } catch (Exception parseError) {
                weather.postValue(WeatherUiState.error(errorMessage == null ? "" : errorMessage));
            }
        });
    }

    private synchronized void track(@NonNull Cancellable c) {
        inFlight.add(c);
    }

    private synchronized void cancelInFlight() {
        for (Cancellable c : inFlight) {
            c.cancel();
        }
        inFlight.clear();
    }

    /** Snapshot of the last issued query, used by {@link #refresh()}. */
    private static final class LastQuery {
        @Nullable final String city;
        final double lat;
        final double lon;

        private LastQuery(@Nullable String city, double lat, double lon) {
            this.city = city;
            this.lat = lat;
            this.lon = lon;
        }

        static LastQuery city(@NonNull String city) {
            return new LastQuery(city, 0d, 0d);
        }

        static LastQuery coord(double lat, double lon) {
            return new LastQuery(null, lat, lon);
        }
    }
}
