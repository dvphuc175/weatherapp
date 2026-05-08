package com.example.weather_application;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.weather_application.models.ForecastResponse;
import java.util.List;
import java.util.Locale;
import com.example.weather_application.models.ForecastItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.example.weather_application.models.CurrentWeather;
import com.example.weather_application.models.WeatherDescription;
import com.example.weather_application.models.WeatherResponse;
import com.example.weather_application.network.RetrofitClient;
import com.example.weather_application.network.WeatherApiService;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.gms.tasks.OnSuccessListener;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    // Ánh xạ các UI Component mới
    private TextView tvLocation, tvTemperature, tvDescription, tvHumidity, tvFeelsLike;
    private com.airbnb.lottie.LottieAnimationView lavWeatherIcon;
    private ImageView ivSearch;
    private EditText etSearchCity;

    private RecyclerView rvForecast;
    private com.github.mikephil.charting.charts.LineChart lineChartTemp;
    private com.airbnb.lottie.LottieAnimationView lavLocationIcon;
    private ForecastAdapter forecastAdapter;

    private FusedLocationProviderClient fusedLocationClient;

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 100;
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 101;
    private static final String UNITS = "metric";
    private static final String LANG = "vi";
    private static final long WORKER_INTERVAL_MINUTES = 15L;
    private static final String WORKER_UNIQUE_NAME = "WeatherAlertWork";

    private static final String API_KEY = BuildConfig.OPENWEATHER_API_KEY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. Ánh xạ Giao diện
        tvLocation = findViewById(R.id.tvLocation);
        tvTemperature = findViewById(R.id.tvTemperature);
        tvDescription = findViewById(R.id.tvDescription);
        tvHumidity = findViewById(R.id.tvHumidity);
        tvFeelsLike = findViewById(R.id.tvFeelsLike); // Mới thêm
        lavWeatherIcon = findViewById(R.id.lavWeatherIcon);
        etSearchCity = findViewById(R.id.etSearchCity); // Mới thêm
        ivSearch = findViewById(R.id.ivSearch); // Mới thêm
        rvForecast = findViewById(R.id.rvForecast);
        lineChartTemp = findViewById(R.id.lineChartTemp);
        lavLocationIcon = findViewById(R.id.lavLocationIcon);

        // Thiết lập cuộn ngang
        rvForecast.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        // 2. Xin quyền Thông báo (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_REQUEST_CODE);
            }
        }

        // 3. Khởi tạo GPS và Lấy vị trí lúc mới mở app
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        getLastLocation();

        // 4. Bắt sự kiện khi người dùng bấm nút Tìm kiếm kính lúp
        ivSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String cityName = etSearchCity.getText().toString().trim();
                if (!cityName.isEmpty()) {
                    // Ẩn bàn phím đi cho đẹp sau khi bấm tìm
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0);

                    // Gọi hàm tìm thời tiết theo tên thành phố
                    fetchWeatherByCity(cityName);
                    fetchForecastByCity(cityName); // THÊM DÒNG NÀY ĐỂ CẬP NHẬT CẢ DỰ BÁO
                } else {
                    Toast.makeText(MainActivity.this, "Vui lòng nhập tên thành phố!", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    // Hàm xin quyền và lấy tọa độ GPS
    private void getLastLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        CancellationTokenSource cancellationTokenSource = new CancellationTokenSource();
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.getToken())
                .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        if (location != null) {
                            double lat = location.getLatitude();
                            double lon = location.getLongitude();

                            // 🚀 CHẠY WORKMANAGER NGẦM Ở ĐÂY (Đã giữ nguyên code của bạn)
                            androidx.work.Data inputData = new androidx.work.Data.Builder()
                                    .putDouble("lat", lat)
                                    .putDouble("lon", lon)
                                    .build();

                            androidx.work.PeriodicWorkRequest weatherWorkRequest =
                                    new androidx.work.PeriodicWorkRequest.Builder(
                                            WeatherAlertWorker.class,
                                            WORKER_INTERVAL_MINUTES,
                                            java.util.concurrent.TimeUnit.MINUTES)
                                            .setInputData(inputData)
                                            .build();

                            // Dùng KEEP để không reset chu kỳ 15 phút mỗi lần mở app.
                            androidx.work.WorkManager.getInstance(MainActivity.this).enqueueUniquePeriodicWork(
                                    WORKER_UNIQUE_NAME,
                                    androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                                    weatherWorkRequest);

                            // Lấy thời tiết theo tọa độ để hiển thị lên UI
                            fetchWeatherData(lat, lon);
                            fetchForecastData(lat, lon);
                        } else {
                            Toast.makeText(MainActivity.this, "Vui lòng set vị trí GPS trong máy ảo.", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    // Hàm gọi API bằng Tọa độ (GPS)
    private void fetchWeatherData(double lat, double lon) {
        WeatherApiService apiService = RetrofitClient.getClient().create(WeatherApiService.class);
        Call<WeatherResponse> call = apiService.getCurrentWeather(lat, lon, API_KEY, UNITS, LANG);

        // Truyền true vì đây là vị trí GPS hiện tại
        call.enqueue(new WeatherCallback(true));
    }

    // Hàm gọi API bằng Tên thành phố (Ô tìm kiếm)
    private void fetchWeatherByCity(String cityName) {
        WeatherApiService apiService = RetrofitClient.getClient().create(WeatherApiService.class);
        Call<WeatherResponse> call = apiService.getWeatherByCity(cityName, API_KEY, UNITS, LANG);

        // Truyền false vì đây là kết quả tìm kiếm
        call.enqueue(new WeatherCallback(false));
    }

    // Khối lệnh ép dữ liệu lên giao diện (Gom lại cho gọn code)
    private class WeatherCallback implements Callback<WeatherResponse> {
        private boolean isCurrentLocation; // Dùng cờ boolean thay vì chuỗi

        // Constructor mới nhận vào true/false
        public WeatherCallback(boolean isCurrentLocation) {
            this.isCurrentLocation = isCurrentLocation;
        }

        @Override
        public void onResponse(Call<WeatherResponse> call, Response<WeatherResponse> response) {
            if (!response.isSuccessful() || response.body() == null) {
                Toast.makeText(MainActivity.this, "Không tìm thấy thành phố này!", Toast.LENGTH_SHORT).show();
                return;
            }

            WeatherResponse weatherData = response.body();

            String name = weatherData.getName();
            tvLocation.setText(name == null ? "" : name.toUpperCase(Locale.ROOT));

            if (isCurrentLocation) {
                lavLocationIcon.setAnimation(R.raw.anim_location);
            } else {
                lavLocationIcon.setAnimation(R.raw.anim_search);
            }
            lavLocationIcon.playAnimation();

            CurrentWeather main = weatherData.getMain();
            if (main != null) {
                tvTemperature.setText(formatCelsius(main.getTemp()));
                tvHumidity.setText(String.format(Locale.getDefault(), "%d%%", main.getHumidity()));
                tvFeelsLike.setText(formatCelsius(main.getFeelsLike()));
            }

            List<WeatherDescription> descriptions = weatherData.getWeather();
            if (descriptions != null && !descriptions.isEmpty()) {
                WeatherDescription first = descriptions.get(0);
                tvDescription.setText(capitalize(first.getDescription()));
                lavWeatherIcon.setAnimation(getLottieRawRes(first.getIcon()));
                lavWeatherIcon.playAnimation();
            }
        }

        @Override
        public void onFailure(Call<WeatherResponse> call, Throwable t) {
            Toast.makeText(MainActivity.this, "Lỗi mạng: " + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // Xử lý kết quả xin quyền
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getLastLocation();
            } else {
                Toast.makeText(this, "Bạn cần cấp quyền vị trí để ứng dụng hoạt động", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this,
                        "Không có quyền thông báo, sẽ không nhận được cảnh báo thời tiết",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }
    private void fetchForecastData(double lat, double lon) {
        WeatherApiService apiService = RetrofitClient.getClient().create(WeatherApiService.class);
        Call<ForecastResponse> call = apiService.getForecast(lat, lon, API_KEY, UNITS, LANG);
        call.enqueue(new ForecastCallback());
    }

    private void fetchForecastByCity(String cityName) {
        WeatherApiService apiService = RetrofitClient.getClient().create(WeatherApiService.class);
        Call<ForecastResponse> call = apiService.getForecastByCity(cityName, API_KEY, UNITS, LANG);
        call.enqueue(new ForecastCallback());
    }

    private class ForecastCallback implements Callback<ForecastResponse> {
        @Override
        public void onResponse(Call<ForecastResponse> call, Response<ForecastResponse> response) {
            if (!response.isSuccessful() || response.body() == null) {
                return;
            }
            List<ForecastItem> forecastList = response.body().getList();
            if (forecastList == null) {
                return;
            }
            forecastAdapter = new ForecastAdapter(MainActivity.this, forecastList);
            rvForecast.setAdapter(forecastAdapter);
            drawTemperatureChart(forecastList);
        }

        @Override
        public void onFailure(Call<ForecastResponse> call, Throwable t) {
            Toast.makeText(MainActivity.this, "Lỗi tải dự báo", Toast.LENGTH_SHORT).show();
        }
    }

    private static String formatCelsius(double temp) {
        return String.format(Locale.getDefault(), "%d°C", Math.round(temp));
    }

    private static String capitalize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }
    private int getLottieRawRes(String iconCode) {
        switch (iconCode) {
            case "01d":
                return R.raw.weather_sunny; // Nắng trong
            case "01n":
                return R.raw.weather_night; // Trăng sáng
            case "02d": case "03d": case "04d":
                return R.raw.weather_partly_cloudy; // Mây ban ngày
            case "02n": case "03n": case "04n":
                return R.raw.weather_cloudy_night; // Mây ban đêm
            case "09d": case "10d":
                return R.raw.weather_partly_shower; // Mưa ban ngày
            case "09n": case "10n":
                return R.raw.weather_rainy_night; // Mưa ban đêm
            case "11d": case "11n":
                return R.raw.weather_thunder; // Sấm chớp
            case "13d":
                return R.raw.weather_snow_sunny; // Tuyết ban ngày
            case "13n":
                return R.raw.weather_snow_night; // Tuyết ban đêm
            case "50d": case "50n":
                return R.raw.weather_mist; // Sương mù
            default:
                return R.raw.weather_partly_cloudy; // Mặc định nếu không khớp
        }
    }
    private void drawTemperatureChart(List<ForecastItem> forecastList) {
        List<com.github.mikephil.charting.data.Entry> entries = new java.util.ArrayList<>();

        // Chỉ lấy 8 mốc dự báo đầu tiên (tương đương 24 giờ tới) cho biểu đồ đỡ bị rối
        int count = Math.min(forecastList.size(), 8);
        for (int i = 0; i < count; i++) {
            float temp = (float) forecastList.get(i).getMain().getTemp();
            entries.add(new com.github.mikephil.charting.data.Entry(i, temp));
        }

        com.github.mikephil.charting.data.LineDataSet dataSet = new com.github.mikephil.charting.data.LineDataSet(entries, "Xu hướng nhiệt độ 24h");
        dataSet.setColor(android.graphics.Color.WHITE); // Màu đường kẻ
        dataSet.setValueTextColor(android.graphics.Color.WHITE); // Màu chữ số nhiệt độ
        dataSet.setValueTextSize(12f);
        dataSet.setLineWidth(3f); // Độ dày đường kẻ
        dataSet.setCircleColor(android.graphics.Color.YELLOW); // Màu các điểm chấm
        dataSet.setCircleRadius(5f);
        dataSet.setMode(com.github.mikephil.charting.data.LineDataSet.Mode.CUBIC_BEZIER); // Làm cong đường kẻ cho mượt
        dataSet.setDrawFilled(true); // Đổ bóng phía dưới đường kẻ
        dataSet.setFillColor(android.graphics.Color.WHITE);

        com.github.mikephil.charting.data.LineData lineData = new com.github.mikephil.charting.data.LineData(dataSet);
        lineChartTemp.setData(lineData);

        // Tắt bớt các đường lưới cho đồ thị hiện đại hơn
        lineChartTemp.getDescription().setEnabled(false);
        lineChartTemp.getAxisRight().setEnabled(false);

        // 1. TẮT LUÔN TRỤC Y BÊN TRÁI để không bị đè chữ
        lineChartTemp.getAxisLeft().setEnabled(false);

        // 2. THÊM KHOẢNG ĐỆM TRÊN/DƯỚI để đồ thị không cắn lên viền
        lineChartTemp.getAxisLeft().setSpaceTop(20f);
        lineChartTemp.getAxisLeft().setSpaceBottom(20f);

        // 3. THÊM KHOẢNG ĐỆM TRÁI/PHẢI để điểm đầu cuối không dính vào vách
        lineChartTemp.getXAxis().setSpaceMin(0.3f);
        lineChartTemp.getXAxis().setSpaceMax(0.3f);

        lineChartTemp.getXAxis().setTextColor(android.graphics.Color.WHITE);
        lineChartTemp.getXAxis().setPosition(com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM);
        lineChartTemp.getLegend().setTextColor(android.graphics.Color.WHITE);

        lineChartTemp.invalidate(); // Lệnh làm mới biểu đồ
    }
}
