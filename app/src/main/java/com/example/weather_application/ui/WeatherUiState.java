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

    private WeatherUiState(@Nullable WeatherResponse data,
                           boolean isCurrentLocation,
                           @Nullable String errorMessage) {
        this.data = data;
        this.isCurrentLocation = isCurrentLocation;
        this.errorMessage = errorMessage;
    }

    @NonNull
    public static WeatherUiState success(@NonNull WeatherResponse data, boolean isCurrentLocation) {
        return new WeatherUiState(data, isCurrentLocation, null);
    }

    @NonNull
    public static WeatherUiState error(@NonNull String message) {
        return new WeatherUiState(null, false, message);
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
}
