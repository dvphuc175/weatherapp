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
        cancelInFlight();
        track(repository.getCurrentWeatherByCoord(lat, lon, weatherCallback(true)));
        track(repository.getForecastByCoord(lat, lon, forecastCallback()));
    }

    public void loadByCity(@NonNull String city) {
        cancelInFlight();
        track(repository.getCurrentWeatherByCity(city, weatherCallback(false)));
        track(repository.getForecastByCity(city, forecastCallback()));
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        cancelInFlight();
    }

    private ResultCallback<WeatherResponse> weatherCallback(final boolean isCurrentLocation) {
        return new ResultCallback<WeatherResponse>() {
            @Override
            public void onSuccess(@NonNull WeatherResponse data) {
                weather.postValue(WeatherUiState.success(data, isCurrentLocation));
            }

            @Override
            public void onError(@Nullable String message) {
                weather.postValue(WeatherUiState.error(message == null ? "" : message));
            }
        };
    }

    private ResultCallback<ForecastResponse> forecastCallback() {
        return new ResultCallback<ForecastResponse>() {
            @Override
            public void onSuccess(@NonNull ForecastResponse data) {
                List<ForecastItem> items = data.getList();
                forecast.postValue(ForecastUiState.success(
                        items == null ? Collections.<ForecastItem>emptyList() : items));
            }

            @Override
            public void onError(@Nullable String message) {
                forecast.postValue(ForecastUiState.error(message == null ? "" : message));
            }
        };
    }

    private void track(@NonNull Cancellable c) {
        inFlight.add(c);
    }

    private void cancelInFlight() {
        for (Cancellable c : inFlight) {
            c.cancel();
        }
        inFlight.clear();
    }
}
