package com.example.weather_application.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.weather_application.models.WeatherResponse;

/** Immutable snapshot of the current-weather UI state observed by MainActivity. */
public final class WeatherUiState {

    @Nullable
    private final WeatherResponse data;
    private final boolean isCurrentLocation;
    @Nullable
    private final String errorMessage;
    private final boolean loading;

    private WeatherUiState(@Nullable WeatherResponse data,
                           boolean isCurrentLocation,
                           @Nullable String errorMessage,
                           boolean loading) {
        this.data = data;
        this.isCurrentLocation = isCurrentLocation;
        this.errorMessage = errorMessage;
        this.loading = loading;
    }

    @NonNull
    public static WeatherUiState loading() {
        return new WeatherUiState(null, false, null, true);
    }

    @NonNull
    public static WeatherUiState success(@NonNull WeatherResponse data, boolean isCurrentLocation) {
        return new WeatherUiState(data, isCurrentLocation, null, false);
    }

    @NonNull
    public static WeatherUiState error(@NonNull String message) {
        return new WeatherUiState(null, false, message, false);
    }

    @Nullable
    public WeatherResponse getData() {
        return data;
    }

    public boolean isCurrentLocation() {
        return isCurrentLocation;
    }

    @Nullable
    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean isError() {
        return errorMessage != null;
    }

    public boolean isLoading() {
        return loading;
    }
}
