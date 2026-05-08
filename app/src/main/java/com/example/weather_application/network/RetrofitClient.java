package com.example.weather_application.network;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {
    // Đường dẫn gốc của API
    private static final String BASE_URL = "https://api.openweathermap.org/";
    private static Retrofit retrofit = null;

    // Hàm trả về đối tượng Retrofit
    public static Retrofit getClient() {
        if (retrofit == null) {
            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    // Thêm Gson vào để nó tự động ép kiểu JSON sang các class trong package models
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }
}