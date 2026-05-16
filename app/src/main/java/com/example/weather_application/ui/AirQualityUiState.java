package com.example.weather_application.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.weather_application.models.AirQualityResponse;

/** Immutable snapshot of the air-quality state for one weather page. */
public final class AirQualityUiState {

    @Nullable
    private final AirQualityResponse data;
    @Nullable
    private final String errorMessage;
    private final boolean loading;

    private AirQualityUiState(@Nullable AirQualityResponse data,
                              @Nullable String errorMessage,
                              boolean loading) {
        this.data = data;
        this.errorMessage = errorMessage;
        this.loading = loading;
    }

    @NonNull
    public static AirQualityUiState loading() {
        return new AirQualityUiState(null, null, true);
    }

    @NonNull
    public static AirQualityUiState success(@NonNull AirQualityResponse data) {
        return new AirQualityUiState(data, null, false);
    }

    @NonNull
    public static AirQualityUiState error(@NonNull String message) {
        return new AirQualityUiState(null, message, false);
    }

    @Nullable
    public AirQualityResponse getData() {
        return data;
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
