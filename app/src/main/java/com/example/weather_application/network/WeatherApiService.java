package com.example.weather_application.network;
import com.example.weather_application.models.ForecastResponse;
import com.example.weather_application.models.WeatherResponse;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface WeatherApiService {
    // Đã đổi thành bản 2.5 hoàn toàn miễn phí
    @GET("data/2.5/weather")
    Call<WeatherResponse> getCurrentWeather(
            @Query("lat") double lat,
            @Query("lon") double lon,
            @Query("appid") String apiKey,
            @Query("units") String units,
            @Query("lang") String lang
    );
    // Thêm hàm này xuống ngay dưới hàm getCurrentWeather cũ
    @GET("data/2.5/weather")
    Call<WeatherResponse> getWeatherByCity(
            @Query("q") String cityName,   // Nhận vào tên thành phố thay vì tọa độ
            @Query("appid") String apiKey,
            @Query("units") String units,
            @Query("lang") String lang
    );
    // Lấy danh sách dự báo 5 ngày (mỗi 3 giờ)
    @GET("data/2.5/forecast")
    Call<ForecastResponse> getForecast(
            @Query("lat") double lat,
            @Query("lon") double lon,
            @Query("appid") String apiKey,
            @Query("units") String units,
            @Query("lang") String lang
    );
    @GET("data/2.5/forecast")
    Call<ForecastResponse> getForecastByCity(
            @Query("q") String cityName,
            @Query("appid") String apiKey,
            @Query("units") String units,
            @Query("lang") String lang
    );
}
