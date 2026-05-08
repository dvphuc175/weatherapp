package com.example.weather_application;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.airbnb.lottie.LottieAnimationView;
import com.example.weather_application.models.CurrentWeather;
import com.example.weather_application.models.ForecastItem;
import com.example.weather_application.models.WeatherDescription;
import com.example.weather_application.models.WeatherResponse;
import com.example.weather_application.ui.ForecastUiState;
import com.example.weather_application.ui.MainViewModel;
import com.example.weather_application.ui.WeatherUiState;
import com.example.weather_application.util.WeatherIconMapper;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 100;
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 101;
    private static final long WORKER_INTERVAL_MINUTES = 15L;
    private static final String WORKER_UNIQUE_NAME = "WeatherAlertWork";
    private static final int CHART_POINTS = 8;

    private TextView tvLocation, tvTemperature, tvDescription, tvHumidity, tvFeelsLike;
    private LottieAnimationView lavWeatherIcon;
    private LottieAnimationView lavLocationIcon;
    private ImageView ivSearch;
    private EditText etSearchCity;

    private RecyclerView rvForecast;
    private LineChart lineChartTemp;
    private ForecastAdapter forecastAdapter;

    private FusedLocationProviderClient fusedLocationClient;
    private MainViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();
        rvForecast.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        observeViewModel();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST_CODE);
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        getLastLocation();

        ivSearch.setOnClickListener(this::triggerCitySearch);
        etSearchCity.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                triggerCitySearch(v);
                return true;
            }
            return false;
        });
    }

    private void bindViews() {
        tvLocation = findViewById(R.id.tvLocation);
        tvTemperature = findViewById(R.id.tvTemperature);
        tvDescription = findViewById(R.id.tvDescription);
        tvHumidity = findViewById(R.id.tvHumidity);
        tvFeelsLike = findViewById(R.id.tvFeelsLike);
        lavWeatherIcon = findViewById(R.id.lavWeatherIcon);
        lavLocationIcon = findViewById(R.id.lavLocationIcon);
        etSearchCity = findViewById(R.id.etSearchCity);
        ivSearch = findViewById(R.id.ivSearch);
        rvForecast = findViewById(R.id.rvForecast);
        lineChartTemp = findViewById(R.id.lineChartTemp);
    }

    private void observeViewModel() {
        viewModel.getWeather().observe(this, new Observer<WeatherUiState>() {
            @Override
            public void onChanged(WeatherUiState state) {
                renderWeather(state);
            }
        });
        viewModel.getForecast().observe(this, new Observer<ForecastUiState>() {
            @Override
            public void onChanged(ForecastUiState state) {
                renderForecast(state);
            }
        });
    }

    private void triggerCitySearch(View v) {
        String cityName = etSearchCity.getText().toString().trim();
        if (cityName.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập tên thành phố!", Toast.LENGTH_SHORT).show();
            return;
        }
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        }
        viewModel.loadByCity(cityName);
    }

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
                .addOnSuccessListener(this, location -> {
                    if (location == null) {
                        Toast.makeText(MainActivity.this,
                                "Vui lòng set vị trí GPS trong máy ảo.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    double lat = location.getLatitude();
                    double lon = location.getLongitude();
                    scheduleWeatherAlertWorker(lat, lon);
                    viewModel.loadByCoord(lat, lon);
                });
    }

    private void scheduleWeatherAlertWorker(double lat, double lon) {
        Data inputData = new Data.Builder()
                .putDouble("lat", lat)
                .putDouble("lon", lon)
                .build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                WeatherAlertWorker.class, WORKER_INTERVAL_MINUTES, TimeUnit.MINUTES)
                .setInputData(inputData)
                .build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                WORKER_UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    private void renderWeather(WeatherUiState state) {
        if (state.isError()) {
            Toast.makeText(this, "Không tìm thấy thành phố này!", Toast.LENGTH_SHORT).show();
            return;
        }
        WeatherResponse data = state.getData();
        if (data == null) {
            return;
        }

        String name = data.getName();
        tvLocation.setText(name == null ? "" : name.toUpperCase(Locale.ROOT));

        lavLocationIcon.setAnimation(state.isCurrentLocation() ? R.raw.anim_location : R.raw.anim_search);
        lavLocationIcon.playAnimation();

        CurrentWeather main = data.getMain();
        if (main != null) {
            tvTemperature.setText(formatCelsius(main.getTemp()));
            tvHumidity.setText(String.format(Locale.getDefault(), "%d%%", main.getHumidity()));
            tvFeelsLike.setText(formatCelsius(main.getFeelsLike()));
        }

        List<WeatherDescription> descriptions = data.getWeather();
        if (descriptions != null && !descriptions.isEmpty()) {
            WeatherDescription first = descriptions.get(0);
            tvDescription.setText(capitalize(first.getDescription()));
            lavWeatherIcon.setAnimation(WeatherIconMapper.rawForIconCode(first.getIcon()));
            lavWeatherIcon.playAnimation();
        }
    }

    private void renderForecast(ForecastUiState state) {
        if (state.isError()) {
            Toast.makeText(this, "Lỗi tải dự báo", Toast.LENGTH_SHORT).show();
            return;
        }
        List<ForecastItem> items = state.getItems();
        if (forecastAdapter == null) {
            forecastAdapter = new ForecastAdapter(this, items);
            rvForecast.setAdapter(forecastAdapter);
        } else {
            forecastAdapter.submitList(items);
        }
        drawTemperatureChart(items);
    }

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

    private static String formatCelsius(double temp) {
        return String.format(Locale.getDefault(), "%d°C", Math.round(temp));
    }

    private static String capitalize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }

    private void drawTemperatureChart(List<ForecastItem> forecastList) {
        if (forecastList == null || forecastList.isEmpty()) {
            lineChartTemp.clear();
            lineChartTemp.invalidate();
            return;
        }

        List<Entry> entries = new ArrayList<>();
        int count = Math.min(forecastList.size(), CHART_POINTS);
        for (int i = 0; i < count; i++) {
            CurrentWeather m = forecastList.get(i).getMain();
            if (m == null) {
                continue;
            }
            entries.add(new Entry(i, (float) m.getTemp()));
        }

        LineDataSet dataSet = new LineDataSet(entries, "Xu hướng nhiệt độ 24h");
        dataSet.setColor(android.graphics.Color.WHITE);
        dataSet.setValueTextColor(android.graphics.Color.WHITE);
        dataSet.setValueTextSize(12f);
        dataSet.setLineWidth(3f);
        dataSet.setCircleColor(android.graphics.Color.YELLOW);
        dataSet.setCircleRadius(5f);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(android.graphics.Color.WHITE);

        lineChartTemp.setData(new LineData(dataSet));
        lineChartTemp.getDescription().setEnabled(false);
        lineChartTemp.getAxisRight().setEnabled(false);
        lineChartTemp.getAxisLeft().setEnabled(false);
        lineChartTemp.getAxisLeft().setSpaceTop(20f);
        lineChartTemp.getAxisLeft().setSpaceBottom(20f);
        lineChartTemp.getXAxis().setSpaceMin(0.3f);
        lineChartTemp.getXAxis().setSpaceMax(0.3f);
        lineChartTemp.getXAxis().setTextColor(android.graphics.Color.WHITE);
        lineChartTemp.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        lineChartTemp.getLegend().setTextColor(android.graphics.Color.WHITE);
        lineChartTemp.invalidate();
    }
}
