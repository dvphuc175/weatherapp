package com.example.weather_application.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.weather_application.models.ForecastItem;

import java.util.Collections;
import java.util.List;

/** Immutable snapshot of the forecast list observed by MainActivity. */
public final class ForecastUiState {

    @NonNull
    private final List<ForecastItem> items;
    @Nullable
    private final String errorMessage;
    private final boolean loading;

    private ForecastUiState(@NonNull List<ForecastItem> items,
                            @Nullable String errorMessage,
                            boolean loading) {
        this.items = items;
        this.errorMessage = errorMessage;
        this.loading = loading;
    }

    @NonNull
    public static ForecastUiState loading() {
        return new ForecastUiState(Collections.<ForecastItem>emptyList(), null, true);
    }

    @NonNull
    public static ForecastUiState success(@NonNull List<ForecastItem> items) {
        return new ForecastUiState(items, null, false);
    }

    @NonNull
    public static ForecastUiState error(@NonNull String message) {
        return new ForecastUiState(Collections.<ForecastItem>emptyList(), message, false);
    }

    @NonNull
    public List<ForecastItem> getItems() {
        return items;
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
