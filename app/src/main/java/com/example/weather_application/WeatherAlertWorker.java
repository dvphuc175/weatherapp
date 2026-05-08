package com.example.weather_application; // Đổi lại đúng tên package của bạn

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.example.weather_application.models.WeatherResponse;
import com.example.weather_application.network.RetrofitClient;
import com.example.weather_application.network.WeatherApiService;
import retrofit2.Response;

public class WeatherAlertWorker extends Worker {

    public WeatherAlertWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        // Lấy tọa độ do MainActivity truyền sang
        double lat = getInputData().getDouble("lat", 21.0285);
        double lon = getInputData().getDouble("lon", 105.8542);

        WeatherApiService apiService = RetrofitClient.getClient().create(WeatherApiService.class);
        try {
            // Chạy ngầm nên dùng execute() (đồng bộ) thay vì enqueue() (bất đồng bộ)
            // NHỚ ĐỔI API KEY Ở DÒNG DƯỚI
            Response<WeatherResponse> response = apiService.getCurrentWeather(lat, lon, "a3260fb92f43242142dd55e9dceaf3c6", "metric", "vi").execute();

            if (response.isSuccessful() && response.body() != null) {
                WeatherResponse weather = response.body();
                String mainWeather = weather.getWeather().get(0).getMain(); // Trạng thái thời tiết chính tiếng Anh
                String description = weather.getWeather().get(0).getDescription();

                // Logic cảnh báo: Nếu có mưa (Rain) hoặc bão (Thunderstorm) thì bắn thông báo
                if (mainWeather.equalsIgnoreCase("Rain") || mainWeather.equalsIgnoreCase("Thunderstorm")) {
                    sendNotification("Cảnh báo thời tiết xấu!", "Sắp tới có " + description + ". Hãy chú ý an toàn!");
                } else {
                    // (Tùy chọn) Bỏ comment dòng dưới nếu muốn cứ 15 phút báo 1 lần cho dễ test
                    // sendNotification("Cập nhật thời tiết", "Hiện tại: " + description + ", nhiệt độ: " + weather.getMain().getTemp() + "°C");
                }
                return Result.success();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Result.retry(); // Lỗi mạng thì bảo hệ thống thử lại sau
    }

    private void sendNotification(String title, String message) {
        NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "weather_alert_channel";

        // Từ Android 8 (Oreo) trở lên bắt buộc phải có Channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "Weather Alerts", NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), channelId)
                .setSmallIcon(R.drawable.ic_launcher_foreground) // Icon mặc định của Android
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        notificationManager.notify(1, builder.build());
    }
}