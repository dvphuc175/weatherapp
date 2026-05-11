package com.example.weather_application;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.weather_application.data.UserPreferences;
import com.example.weather_application.data.WeatherRepository;
import com.example.weather_application.models.WeatherDescription;
import com.example.weather_application.models.WeatherResponse;
import com.example.weather_application.util.TemperatureUnit;

import java.util.List;

public class WeatherAlertWorker extends Worker {

    private static final String CHANNEL_ID = "weather_alert_channel";
    private static final int NOTIFICATION_ID = 1;

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

        WeatherRepository repository = new WeatherRepository();
        if (!repository.hasApiKey()) {
            return Result.failure();
        }

        try {
            // The worker only checks "is it raining" — the unit doesn't change the answer, but
            // we still use the user's preference so the upstream HTTP cache (if any) stays warm.
            TemperatureUnit unit = UserPreferences.get(getApplicationContext()).getTemperatureUnit();
            WeatherResponse weather = repository.executeCurrentWeatherByCoord(lat, lon, unit);
            if (weather == null) {
                return Result.retry();
            }

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
                Context context = getApplicationContext();
                String body = context.getString(R.string.notification_bad_weather_body,
                        description == null ? mainWeather : description);
                sendNotification(context.getString(R.string.notification_bad_weather_title), body);
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
                    CHANNEL_ID,
                    getApplicationContext().getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_HIGH);
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
