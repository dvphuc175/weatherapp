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
        int gen = startNewGeneration();
        track(repository.getCurrentWeatherByCoord(lat, lon, weatherCallback(true, gen)));
        track(repository.getForecastByCoord(lat, lon, forecastCallback(gen)));
    }

    public void loadByCity(@NonNull String city) {
        int gen = startNewGeneration();
        track(repository.getCurrentWeatherByCity(city, weatherCallback(false, gen)));
        track(repository.getForecastByCity(city, forecastCallback(gen)));
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
}
