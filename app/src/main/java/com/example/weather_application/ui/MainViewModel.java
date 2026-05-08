package com.example.weather_application.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.weather_application.data.WeatherRepository;
import com.example.weather_application.data.WeatherRepository.Cancellable;
import com.example.weather_application.data.WeatherRepository.ResultCallback;
import com.example.weather_application.models.ForecastItem;
import com.example.weather_application.models.ForecastResponse;
import com.example.weather_application.models.WeatherResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainViewModel extends ViewModel {

    private final WeatherRepository repository;

    private final MutableLiveData<WeatherUiState> weather = new MutableLiveData<>();
    private final MutableLiveData<ForecastUiState> forecast = new MutableLiveData<>();

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

    public MainViewModel() {
        this(new WeatherRepository());
    }

    MainViewModel(@NonNull WeatherRepository repository) {
        this.repository = repository;
    }

    @NonNull
    public LiveData<WeatherUiState> getWeather() {
        return weather;
    }

    @NonNull
    public LiveData<ForecastUiState> getForecast() {
        return forecast;
    }

    public void loadByCoord(double lat, double lon) {
        lastQuery = LastQuery.coord(lat, lon);
        int gen = startNewGeneration();
        weather.postValue(WeatherUiState.loading());
        forecast.postValue(ForecastUiState.loading());
        track(repository.getCurrentWeatherByCoord(lat, lon, weatherCallback(true, gen)));
        track(repository.getForecastByCoord(lat, lon, forecastCallback(gen)));
    }

    public void loadByCity(@NonNull String city) {
        lastQuery = LastQuery.city(city);
        int gen = startNewGeneration();
        weather.postValue(WeatherUiState.loading());
        forecast.postValue(ForecastUiState.loading());
        track(repository.getCurrentWeatherByCity(city, weatherCallback(false, gen)));
        track(repository.getForecastByCity(city, forecastCallback(gen)));
    }

    /**
     * Re-issue the last successful query. Wired to pull-to-refresh and the inline error retry
     * button. No-op if no query has been issued yet (e.g. permission still being requested).
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

    @Override
    protected void onCleared() {
        super.onCleared();
        cancelInFlight();
    }

    private synchronized int startNewGeneration() {
        cancelInFlight();
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
                weather.postValue(WeatherUiState.success(data, isCurrentLocation));
            }

            @Override
            public void onError(@Nullable String message) {
                if (isStale(gen)) return;
                weather.postValue(WeatherUiState.error(message == null ? "" : message));
            }
        };
    }

    private ResultCallback<ForecastResponse> forecastCallback(final int gen) {
        return new ResultCallback<ForecastResponse>() {
            @Override
            public void onSuccess(@NonNull ForecastResponse data) {
                if (isStale(gen)) return;
                List<ForecastItem> items = data.getList();
                forecast.postValue(ForecastUiState.success(
                        items == null ? Collections.<ForecastItem>emptyList() : items));
            }

            @Override
            public void onError(@Nullable String message) {
                if (isStale(gen)) return;
                forecast.postValue(ForecastUiState.error(message == null ? "" : message));
            }
        };
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
