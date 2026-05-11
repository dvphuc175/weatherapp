package com.example.weather_application.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.weather_application.BuildConfig;
import com.example.weather_application.models.ForecastResponse;
import com.example.weather_application.models.WeatherResponse;
import com.example.weather_application.network.RetrofitClient;
import com.example.weather_application.network.WeatherApiService;
import com.example.weather_application.util.TemperatureUnit;

import java.io.IOException;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Bao bọc {@link WeatherApiService} để các tầng UI/ViewModel không phải biết về Retrofit.
 * Trả về {@link Cancellable} để caller có thể huỷ request khi lifecycle kết thúc.
 *
 * <p>Each call now takes a {@link TemperatureUnit} so OWM returns °C/°F directly — no
 * client-side conversion. Caching is the caller's responsibility (see {@code MainViewModel}).
 */
public final class WeatherRepository {

    private static final String LANG = "vi";

    public interface ResultCallback<T> {
        void onSuccess(@NonNull T data);
        void onError(@Nullable String message);
    }

    public interface Cancellable {
        void cancel();
    }

    private final WeatherApiService api;
    private final String apiKey;

    public WeatherRepository() {
        this(RetrofitClient.api(), BuildConfig.OPENWEATHER_API_KEY);
    }

    WeatherRepository(@NonNull WeatherApiService api, @NonNull String apiKey) {
        this.api = api;
        this.apiKey = apiKey;
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isEmpty();
    }

    public Cancellable getCurrentWeatherByCoord(double lat, double lon,
                                                @NonNull TemperatureUnit unit,
                                                @NonNull ResultCallback<WeatherResponse> cb) {
        return enqueue(api.getCurrentWeather(lat, lon, apiKey, unit.owmUnitsParam, LANG), cb);
    }

    public Cancellable getCurrentWeatherByCity(@NonNull String city,
                                               @NonNull TemperatureUnit unit,
                                               @NonNull ResultCallback<WeatherResponse> cb) {
        return enqueue(api.getWeatherByCity(city, apiKey, unit.owmUnitsParam, LANG), cb);
    }

    public Cancellable getForecastByCoord(double lat, double lon,
                                          @NonNull TemperatureUnit unit,
                                          @NonNull ResultCallback<ForecastResponse> cb) {
        return enqueue(api.getForecast(lat, lon, apiKey, unit.owmUnitsParam, LANG), cb);
    }

    public Cancellable getForecastByCity(@NonNull String city,
                                         @NonNull TemperatureUnit unit,
                                         @NonNull ResultCallback<ForecastResponse> cb) {
        return enqueue(api.getForecastByCity(city, apiKey, unit.owmUnitsParam, LANG), cb);
    }

    /**
     * Synchronous variant for {@link androidx.work.Worker} threads. Returns null if the request
     * fails or the body is missing — caller decides whether to retry.
     */
    @Nullable
    public WeatherResponse executeCurrentWeatherByCoord(double lat, double lon,
                                                        @NonNull TemperatureUnit unit)
            throws IOException {
        Response<WeatherResponse> response = api
                .getCurrentWeather(lat, lon, apiKey, unit.owmUnitsParam, LANG)
                .execute();
        if (!response.isSuccessful()) {
            return null;
        }
        return response.body();
    }

    private static <T> Cancellable enqueue(@NonNull final Call<T> call,
                                           @NonNull final ResultCallback<T> cb) {
        call.enqueue(new Callback<T>() {
            @Override
            public void onResponse(@NonNull Call<T> c, @NonNull Response<T> response) {
                if (response.isSuccessful() && response.body() != null) {
                    cb.onSuccess(response.body());
                } else {
                    cb.onError("HTTP " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<T> c, @NonNull Throwable t) {
                if (!c.isCanceled()) {
                    cb.onError(t.getMessage());
                }
            }
        });
        return call::cancel;
    }
}
