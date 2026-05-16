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
import com.example.weather_application.models.AirQualityResponse;
import com.example.weather_application.models.Coord;
import com.example.weather_application.models.ForecastItem;
import com.example.weather_application.models.ForecastResponse;
import com.example.weather_application.models.WeatherResponse;
import com.example.weather_application.util.TemperatureUnit;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
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
    private final MutableLiveData<AirQualityUiState> airQuality = new MutableLiveData<>();
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
    /** Last AQI payload for the active generation. Kept as a field so AQI callbacks and any
     *  future cache integration have a stable owner; it is intentionally not persisted to Room. */
    @Nullable
    private AirQualityResponse latestAirQuality;

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
                // Reload with the new units= param. Snapshots are keyed per unit, so a matching
                // offline cache can still be used when the network is unavailable.
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
    public LiveData<AirQualityUiState> getAirQuality() {
        return airQuality;
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
        airQuality.postValue(AirQualityUiState.loading());
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
        airQuality.postValue(AirQualityUiState.loading());
        track(repository.getCurrentWeatherByCoord(lat, lon, unit, weatherCallback(true, gen)));
        track(repository.getForecastByCoord(lat, lon, unit, forecastCallback(gen)));
        track(repository.getAirQuality(lat, lon, airQualityCallback(gen)));
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
        latestAirQuality = null;
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
                if (!isCurrentLocation) {
                    loadAirQualityForWeather(data, gen);
                }
                persistSnapshotIfReady();
            }

            @Override
            public void onError(@Nullable String message) {
                if (isStale(gen)) return;
                tryServeFromCache(message, isCurrentLocation);
            }
        };
    }

    private void loadAirQualityForWeather(@NonNull WeatherResponse data, final int gen) {
        Coord coord = data.getCoord();
        if (coord == null) {
            airQuality.postValue(AirQualityUiState.error(""));
            return;
        }
        track(repository.getAirQuality(coord.getLat(), coord.getLon(), airQualityCallback(gen)));
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

    private ResultCallback<AirQualityResponse> airQualityCallback(final int gen) {
        return new ResultCallback<AirQualityResponse>() {
            @Override
            public void onSuccess(@NonNull AirQualityResponse data) {
                if (isStale(gen)) return;
                latestAirQuality = data;
                airQuality.postValue(AirQualityUiState.success(data));
            }

            @Override
            public void onError(@Nullable String message) {
                if (isStale(gen)) return;
                // Air quality is additive. Keep the weather page usable even when this endpoint
                // fails; the AQI card will show an unavailable state.
                airQuality.postValue(AirQualityUiState.error(message == null ? "" : message));
            }
        };
    }

    private void persistSnapshotIfReady() {
        final WeatherResponse w = latestWeather;
        final ForecastResponse f = latestForecast;
        if (w == null || f == null) return;
        final TemperatureUnit unit = currentUnit();
        final LastQuery query = lastQuery;
        if (query == null) return;
        final String cacheKey = query.cacheKey(unit);
        final String cityName = w.getName();
        final AirQualityResponse aq = latestAirQuality;
        diskIo.execute(() -> cachedSnapshotDao.upsert(new CachedSnapshot(
                cacheKey,
                cityName,
                unit.owmUnitsParam,
                gson.toJson(w),
                gson.toJson(f),
                aq == null ? null : gson.toJson(aq),
                System.currentTimeMillis())));
    }

    /**
     * Fall back to the snapshot for this exact query + unit. Each pager page has an independent
     * cache entry, so page A no longer depends on page B being the last city fetched.
     */
    private void tryServeFromCache(@Nullable String errorMessage,
                                   final boolean isCurrentLocation) {
        final TemperatureUnit currentUnit = currentUnit();
        final LastQuery query = lastQuery;
        if (query == null) {
            weather.postValue(WeatherUiState.error(errorMessage == null ? "" : errorMessage));
            return;
        }
        final String cacheKey = query.cacheKey(currentUnit);
        diskIo.execute(() -> {
            CachedSnapshot snap = cachedSnapshotDao.getByKeySync(cacheKey);
            if (snap == null || snap.weatherJson == null || snap.forecastJson == null
                    || !currentUnit.owmUnitsParam.equals(snap.units)) {
                weather.postValue(WeatherUiState.error(errorMessage == null ? "" : errorMessage));
                return;
            }
            try {
                WeatherResponse w = gson.fromJson(snap.weatherJson, WeatherResponse.class);
                ForecastResponse f = gson.fromJson(snap.forecastJson, ForecastResponse.class);
                AirQualityResponse aq = snap.airQualityJson == null
                        ? null
                        : gson.fromJson(snap.airQualityJson, AirQualityResponse.class);
                if (w == null || f == null) {
                    weather.postValue(WeatherUiState.error(errorMessage == null ? "" : errorMessage));
                    return;
                }
                latestWeather = w;
                latestForecast = f;
                latestAirQuality = aq;
                servingFromCache.postValue(true);
                cacheSavedAt.postValue(snap.savedAt);
                weather.postValue(WeatherUiState.success(w, isCurrentLocation));
                List<ForecastItem> items = f.getList();
                forecast.postValue(ForecastUiState.success(
                        items == null ? Collections.<ForecastItem>emptyList() : items));
                airQuality.postValue(AirQualityUiState.error(""));
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

        @NonNull
        String cacheKey(@NonNull TemperatureUnit unit) {
            if (city != null) {
                return "city:" + city.trim().toLowerCase(Locale.ROOT) + ":" + unit.owmUnitsParam;
            }
            return String.format(Locale.US, "gps:%.4f,%.4f:%s", lat, lon, unit.owmUnitsParam);
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
