package com.example.weather_application;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.example.weather_application.models.WeatherDescription;
import com.example.weather_application.models.WeatherResponse;
import com.example.weather_application.network.RetrofitClient;
import com.example.weather_application.network.WeatherApiService;

import java.util.List;
import java.util.Locale;

import retrofit2.Response;

public class WeatherAlertWorker extends Worker {

    private static final String CHANNEL_ID = "weather_alert_channel";
    private static final String CHANNEL_NAME = "Weather Alerts";
    private static final int NOTIFICATION_ID = 1;
    private static final String UNITS = "metric";
    private static final String LANG = "vi";

    // Hà Nội làm fallback nếu input không có toạ độ
    private static final double DEFAULT_LAT = 21.0285;
    private static final double DEFAULT_LON = 105.8542;

    public WeatherAlertWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        double lat = getInputData().getDouble("lat", DEFAULT_LAT);
        double lon = getInputData().getDouble("lon", DEFAULT_LON);

        if (BuildConfig.OPENWEATHER_API_KEY.isEmpty()) {
            return Result.failure();
        }

        WeatherApiService apiService = RetrofitClient.getClient().create(WeatherApiService.class);
        try {
            Response<WeatherResponse> response = apiService
                    .getCurrentWeather(lat, lon, BuildConfig.OPENWEATHER_API_KEY, UNITS, LANG)
                    .execute();

            if (!response.isSuccessful() || response.body() == null) {
                return Result.retry();
            }

            WeatherResponse weather = response.body();
            List<WeatherDescription> descriptions = weather.getWeather();
            if (descriptions == null || descriptions.isEmpty()) {
                return Result.success();
            }

            WeatherDescription first = descriptions.get(0);
            String mainWeather = first.getMain();
            String description = first.getDescription();
            if (mainWeather == null) {
                return Result.success();
            }

            if (mainWeather.equalsIgnoreCase("Rain") || mainWeather.equalsIgnoreCase("Thunderstorm")) {
                String body = String.format(Locale.getDefault(),
                        "Sắp tới có %s. Hãy chú ý an toàn!",
                        description == null ? mainWeather : description);
                sendNotification("Cảnh báo thời tiết xấu!", body);
            }
            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }

    private void sendNotification(String title, String message) {
        NotificationManager notificationManager =
                (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }
}
