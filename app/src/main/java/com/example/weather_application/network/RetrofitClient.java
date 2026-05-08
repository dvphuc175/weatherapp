package com.example.weather_application.network;

import com.example.weather_application.BuildConfig;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class RetrofitClient {

    private static final String BASE_URL = "https://api.openweathermap.org/";

    private static volatile Retrofit retrofit;
    private static volatile WeatherApiService api;

    private RetrofitClient() {}

    public static Retrofit getClient() {
        Retrofit local = retrofit;
        if (local == null) {
            synchronized (RetrofitClient.class) {
                local = retrofit;
                if (local == null) {
                    local = new Retrofit.Builder()
                            .baseUrl(BASE_URL)
                            .client(buildOkHttpClient())
                            .addConverterFactory(GsonConverterFactory.create())
                            .build();
                    retrofit = local;
                }
            }
        }
        return local;
    }

    public static WeatherApiService api() {
        WeatherApiService local = api;
        if (local == null) {
            synchronized (RetrofitClient.class) {
                local = api;
                if (local == null) {
                    local = getClient().create(WeatherApiService.class);
                    api = local;
                }
            }
        }
        return local;
    }

    private static OkHttpClient buildOkHttpClient() {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(BuildConfig.DEBUG
                ? HttpLoggingInterceptor.Level.BASIC
                : HttpLoggingInterceptor.Level.NONE);

        return new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .build();
    }
}
