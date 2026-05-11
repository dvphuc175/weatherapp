package com.example.weather_application.ui;

import android.app.Application;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AbstractSavedStateViewModelFactory;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import androidx.savedstate.SavedStateRegistryOwner;

import com.example.weather_application.data.UserPreferences;
import com.example.weather_application.data.WeatherRepository;
import com.example.weather_application.data.WeatherRepository.Cancellable;
import com.example.weather_application.data.WeatherRepository.ResultCallback;
import com.example.weather_application.data.local.AppDatabase;
import com.example.weather_application.data.local.CachedSnapshot;
import com.example.weather_application.data.local.CachedSnapshotDao;
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

/**
 * Per-page ViewModel scoped to a {@link com.example.weather_application.ui.CityWeatherFragment}.
 * Owns the network state for one city (weather, forecast, offline-banner). Shared concerns —
 * temperature unit, recent searches, saved-city list, GPS coord — live in {@link MainViewModel}.
 *
 * <p>The {@code cityName} / {@code isCurrentLocation} args come from the fragment's
 * {@link SavedStateHandle}; this lets Room rehydrate the right page after process death without
 * relying on the adapter to re-supply them.
 */
public class CityViewModel extends AndroidViewModel {

    public static final String KEY_CITY_NAME = "city_name";
    public static final String KEY_IS_CURRENT_LOCATION = "is_current_location";

    private final WeatherRepository repository;
    private final UserPreferences userPreferences;
    private final CachedSnapshotDao cachedSnapshotDao;
    private final ExecutorService diskIo = Executors.newSingleThreadExecutor();
    private final Gson gson = new Gson();

    @Nullable
    private final String boundCityName;
    private final boolean boundIsCurrentLocation;

    private final MutableLiveData<WeatherUiState> weather = new MutableLiveData<>();
    private final MutableLiveData<ForecastUiState> forecast = new MutableLiveData<>();
    private final MutableLiveData<Boolean> servingFromCache = new MutableLiveData<>(false);
    private final MutableLiveData<Long> cacheSavedAt = new MutableLiveData<>(null);

    private final List<Cancellable> inFlight = new ArrayList<>();
    private int loadGeneration;

    @Nullable
    private LastQuery lastQuery;

    @Nullable
    private WeatherResponse latestWeather;
    @Nullable
    private ForecastResponse latestForecast;

    /** Listens for unit changes so the page reloads with the new {@code units=} param. */
    @NonNull
    private final SharedPreferences.OnSharedPreferenceChangeListener unitListener;

    CityViewModel(@NonNull Application application,
                  @NonNull WeatherRepository repository,
                  @NonNull UserPreferences userPreferences,
                  @NonNull AppDatabase database,
                  @NonNull SavedStateHandle savedState) {
        super(application);
        this.repository = repository;
        this.userPreferences = userPreferences;
        this.cachedSnapshotDao = database.cachedSnapshotDao();
        this.boundCityName = savedState.get(KEY_CITY_NAME);
        Boolean cur = savedState.get(KEY_IS_CURRENT_LOCATION);
        this.boundIsCurrentLocation = cur != null && cur;
        this.unitListener = (sp, key) -> {
            if (UserPreferences.keyTemperatureUnit().equals(key)) {
                // Cache is keyed on the old units param; the shared MainViewModel wipes it.
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
    public LiveData<Boolean> getServingFromCache() {
        return servingFromCache;
    }

    @NonNull
    public LiveData<Long> getCacheSavedAt() {
        return cacheSavedAt;
    }

    @Nullable
    public String getBoundCityName() {
        return boundCityName;
    }

    public boolean isCurrentLocationPage() {
        return boundIsCurrentLocation;
    }

    /** Initial load by city name. No-op if the city is empty. */
    public void loadByCity(@NonNull String city) {
        if (city.isEmpty()) return;
        lastQuery = LastQuery.city(city);
        int gen = startNewGeneration();
        TemperatureUnit unit = currentUnit();
        weather.postValue(WeatherUiState.loading());
        forecast.postValue(ForecastUiState.loading());
        track(repository.getCurrentWeatherByCity(city, unit, weatherCallback(false, gen)));
        track(repository.getForecastByCity(city, unit, forecastCallback(gen)));
    }

    /** Initial load by GPS coord. Only the current-location page should call this. */
    public void loadByCoord(double lat, double lon) {
        lastQuery = LastQuery.coord(lat, lon);
        int gen = startNewGeneration();
        TemperatureUnit unit = currentUnit();
        weather.postValue(WeatherUiState.loading());
        forecast.postValue(ForecastUiState.loading());
        track(repository.getCurrentWeatherByCoord(lat, lon, unit, weatherCallback(true, gen)));
        track(repository.getForecastByCoord(lat, lon, unit, forecastCallback(gen)));
    }

    /** Re-issue the last query. Pull-to-refresh, retry button, and network-regained all call this. */
    public void refresh() {
        LastQuery q = lastQuery;
        if (q == null) {
            if (boundCityName != null && !boundIsCurrentLocation) {
                loadByCity(boundCityName);
            }
            return;
        }
        if (q.city != null) {
            loadByCity(q.city);
        } else {
            loadByCoord(q.lat, q.lon);
        }
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
        return userPreferences.getTemperatureUnit();
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
                if (Boolean.TRUE.equals(servingFromCache.getValue())) {
                    return;
                }
                forecast.postValue(ForecastUiState.error(message == null ? "" : message));
            }
        };
    }

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

    /**
     * Fall back to the single-row cache if the units match AND the cached city name matches
     * this page's bound city. We don't want page A to render with page B's cached blob just
     * because B was the last city fetched before going offline.
     */
    private void tryServeFromCache(@Nullable String errorMessage,
                                   final boolean isCurrentLocation) {
        final TemperatureUnit currentUnit = currentUnit();
        final String wantedCityName = boundCityName;
        final boolean isGpsPage = boundIsCurrentLocation;
        diskIo.execute(() -> {
            CachedSnapshot snap = cachedSnapshotDao.getByIdSync(CachedSnapshot.SINGLE_ROW_KEY);
            if (snap == null || snap.weatherJson == null || snap.forecastJson == null
                    || !currentUnit.owmUnitsParam.equals(snap.units)) {
                weather.postValue(WeatherUiState.error(errorMessage == null ? "" : errorMessage));
                return;
            }
            // Only serve the cached blob if it belongs to this page. The GPS page is a fuzzy
            // match (any cached city is acceptable when reverse geocoding isn't possible);
            // other pages must match by city name.
            boolean matches = isGpsPage
                    || (wantedCityName != null && wantedCityName.equalsIgnoreCase(snap.cityName));
            if (!matches) {
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

    private static final class LastQuery {
        @Nullable
        final String city;
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

    /**
     * Factory that injects the fragment's args into the ViewModel via {@link SavedStateHandle}.
     * Without this, {@code CityViewModel} can't know which city it's bound to.
     */
    public static final class Factory extends AbstractSavedStateViewModelFactory {

        private final Application application;

        public Factory(@NonNull SavedStateRegistryOwner owner,
                       @Nullable android.os.Bundle defaultArgs,
                       @NonNull Application application) {
            super(owner, defaultArgs);
            this.application = application;
        }

        @NonNull
        @Override
        protected <T extends ViewModel> T create(@NonNull String key,
                                                 @NonNull Class<T> modelClass,
                                                 @NonNull SavedStateHandle handle) {
            return modelClass.cast(new CityViewModel(
                    application,
                    new WeatherRepository(),
                    UserPreferences.get(application),
                    AppDatabase.get(application),
                    handle));
        }
    }
}
