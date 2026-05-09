package com.example.weather_application;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.airbnb.lottie.LottieAnimationView;
import com.example.weather_application.models.CurrentWeather;
import com.example.weather_application.models.DailyForecast;
import com.example.weather_application.models.ForecastItem;
import com.example.weather_application.models.Sys;
import com.example.weather_application.models.WeatherDescription;
import com.example.weather_application.models.WeatherResponse;
import com.example.weather_application.models.Wind;
import com.example.weather_application.ui.ForecastUiState;
import com.example.weather_application.ui.MainViewModel;
import com.example.weather_application.ui.WeatherUiState;
import com.example.weather_application.util.DailyForecastAggregator;
import com.example.weather_application.util.WeatherGradientMapper;
import com.example.weather_application.util.WeatherIconMapper;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 100;
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 101;
    private static final long WORKER_INTERVAL_MINUTES = 15L;
    private static final String WORKER_UNIQUE_NAME = "WeatherAlertWork";
    private static final int CHART_POINTS = 8;
    /** OpenWeatherMap visibility caps at 10000 m even on perfectly clear days. */
    private static final int MAX_VISIBILITY_METERS = 10_000;
    /** Crossfade duration when swapping the background gradient between weather conditions. */
    private static final int GRADIENT_FADE_DURATION_MS = 600;

    private TextView tvLocation, tvTemperature, tvDescription, tvHumidity, tvFeelsLike;
    private TextView tvSunrise, tvSunset;
    private TextView tvWind, tvPressure, tvVisibility;
    private LinearLayout layoutSunCycle;
    private LottieAnimationView lavWeatherIcon;
    private LottieAnimationView lavLocationIcon;
    private ImageView ivSearch;
    private EditText etSearchCity;

    private SwipeRefreshLayout swipeRefresh;
    private FrameLayout layoutLoading;
    private LinearLayout layoutError, layoutContent;
    private TextView tvErrorMessage;
    private Button btnRetry;
    /** Current-conditions cards. Hidden in daily mode where they make less sense. */
    private View cardHumidityFeels, cardWindPressureVisibility;

    private RecyclerView rvForecast;
    private RecyclerView rvDailyForecast;
    private MaterialButtonToggleGroup toggleForecastMode;
    private LineChart lineChartTemp;
    private ForecastAdapter forecastAdapter;
    private DailyForecastAdapter dailyForecastAdapter;

    private FusedLocationProviderClient fusedLocationClient;
    private MainViewModel viewModel;
    /** Sticky timezone offset (seconds from UTC) of the city currently being shown. Used for chart x-axis. */
    private int currentCityTimezoneOffsetSec;
    /** Current background gradient (start/center/end ARGBs); null until the first weather render. */
    @androidx.annotation.Nullable
    private int[] currentGradientColors;
    /** {@code true} = daily/aggregated mode; {@code false} = hourly. Persisted across rotation via
     *  {@link #onSaveInstanceState}. */
    private boolean dailyMode;
    /** Saved-state key for {@link #dailyMode}. */
    private static final String STATE_DAILY_MODE = "state_daily_mode";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Edge-to-edge: let the gradient run under the system bars. We push the search row
        // down by the status-bar inset and the metrics card up by the navigation-bar inset.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);

        bindViews();
        applyEdgeToEdgeInsets();
        rvForecast.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvDailyForecast.setLayoutManager(new LinearLayoutManager(this));

        if (savedInstanceState != null) {
            dailyMode = savedInstanceState.getBoolean(STATE_DAILY_MODE, false);
        }
        wireForecastModeToggle();
        applyForecastModeVisibility();

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        observeViewModel();

        swipeRefresh.setColorSchemeResources(R.color.gradient_start, R.color.gradient_end);
        swipeRefresh.setOnRefreshListener(viewModel::refresh);
        btnRetry.setOnClickListener(v -> viewModel.refresh());

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
        swipeRefresh = findViewById(R.id.swipeRefresh);
        layoutLoading = findViewById(R.id.layoutLoading);
        layoutError = findViewById(R.id.layoutError);
        layoutContent = findViewById(R.id.layoutContent);
        tvErrorMessage = findViewById(R.id.tvErrorMessage);
        btnRetry = findViewById(R.id.btnRetry);

        tvLocation = findViewById(R.id.tvLocation);
        tvTemperature = findViewById(R.id.tvTemperature);
        tvDescription = findViewById(R.id.tvDescription);
        tvHumidity = findViewById(R.id.tvHumidity);
        tvFeelsLike = findViewById(R.id.tvFeelsLike);
        tvSunrise = findViewById(R.id.tvSunrise);
        tvSunset = findViewById(R.id.tvSunset);
        tvWind = findViewById(R.id.tvWind);
        tvPressure = findViewById(R.id.tvPressure);
        tvVisibility = findViewById(R.id.tvVisibility);
        layoutSunCycle = findViewById(R.id.layoutSunCycle);

        lavWeatherIcon = findViewById(R.id.lavWeatherIcon);
        lavLocationIcon = findViewById(R.id.lavLocationIcon);
        etSearchCity = findViewById(R.id.etSearchCity);
        ivSearch = findViewById(R.id.ivSearch);
        rvForecast = findViewById(R.id.rvForecast);
        rvDailyForecast = findViewById(R.id.rvDailyForecast);
        toggleForecastMode = findViewById(R.id.toggleForecastMode);
        lineChartTemp = findViewById(R.id.lineChartTemp);
        cardHumidityFeels = findViewById(R.id.cardHumidityFeels);
        cardWindPressureVisibility = findViewById(R.id.cardWindPressureVisibility);
    }

    /** Pre-select the toggle button matching {@link #dailyMode} and listen for user changes. */
    private void wireForecastModeToggle() {
        toggleForecastMode.check(dailyMode ? R.id.btnForecastDaily : R.id.btnForecastHourly);
        toggleForecastMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            boolean nextDaily = checkedId == R.id.btnForecastDaily;
            if (nextDaily == dailyMode) {
                return;
            }
            dailyMode = nextDaily;
            applyForecastModeVisibility();
        });
    }

    /** Show only the views that belong to the active forecast mode. The chart is hourly-specific
     *  so it follows {@code rvForecast}. The humidity/feels-like and wind/pressure/visibility
     *  cards represent <em>current</em> conditions, which feels out of place next to a 5-day
     *  outlook, so we hide them in daily mode and let the daily list breathe. The sun pill
     *  stays since sunrise/sunset is a useful anchor in either mode. */
    private void applyForecastModeVisibility() {
        rvForecast.setVisibility(dailyMode ? View.GONE : View.VISIBLE);
        lineChartTemp.setVisibility(dailyMode ? View.GONE : View.VISIBLE);
        rvDailyForecast.setVisibility(dailyMode ? View.VISIBLE : View.GONE);
        cardHumidityFeels.setVisibility(dailyMode ? View.GONE : View.VISIBLE);
        cardWindPressureVisibility.setVisibility(dailyMode ? View.GONE : View.VISIBLE);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_DAILY_MODE, dailyMode);
    }

    private void applyEdgeToEdgeInsets() {
        View root = findViewById(R.id.layoutRoot);
        ViewCompat.setOnApplyWindowInsetsListener(swipeRefresh, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            // Push the whole scroll content down so the search row clears the status bar without
            // its own contents being compressed by the inset (the row has a fixed height).
            // Also pad the bottom so the last card isn't hidden behind the navigation bar.
            root.setPadding(root.getPaddingLeft(), bars.top,
                    root.getPaddingRight(), bars.bottom);
            return insets;
        });
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
            Toast.makeText(this, R.string.toast_enter_city, Toast.LENGTH_SHORT).show();
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
                                R.string.toast_gps_unavailable,
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
        // Drive the inline state UI off the weather LiveData. Forecast errors only Toast — the
        // primary screen ought to keep showing the current weather even if the forecast fails.
        if (state.isLoading() && state.getData() == null) {
            showLoadingState();
            return;
        }
        if (state.isError() && layoutContent.getVisibility() != View.VISIBLE) {
            // First load failed; inline retry view replaces the content.
            showErrorState(state.getErrorMessage());
            return;
        }
        if (state.isError()) {
            // Refresh after a successful load failed; keep showing the old data and Toast.
            swipeRefresh.setRefreshing(false);
            Toast.makeText(this, R.string.toast_city_not_found, Toast.LENGTH_SHORT).show();
            return;
        }

        WeatherResponse data = state.getData();
        if (data == null) {
            return;
        }
        showContentState();

        String name = data.getName();
        tvLocation.setText(name == null ? "" : name.toUpperCase(Locale.ROOT));

        lavLocationIcon.setAnimation(state.isCurrentLocation() ? R.raw.anim_location : R.raw.anim_search);
        lavLocationIcon.playAnimation();

        CurrentWeather main = data.getMain();
        if (main != null) {
            tvTemperature.setText(formatCelsius(main.getTemp()));
            tvHumidity.setText(String.format(Locale.getDefault(), "%d%%", main.getHumidity()));
            tvFeelsLike.setText(formatCelsius(main.getFeelsLike()));
            tvPressure.setText(getString(R.string.pressure_value_format, main.getPressure()));
        }

        List<WeatherDescription> descriptions = data.getWeather();
        if (descriptions != null && !descriptions.isEmpty()) {
            WeatherDescription first = descriptions.get(0);
            tvDescription.setText(capitalize(first.getDescription()));
            lavWeatherIcon.setAnimation(WeatherIconMapper.rawForIconCode(first.getIcon()));
            lavWeatherIcon.playAnimation();
            applyDynamicGradient(first.getIcon());
        }

        currentCityTimezoneOffsetSec = data.getTimezone();
        bindSunCycle(data.getSys(), data.getTimezone());
        bindWind(data.getWind());
        bindVisibility(data.getVisibility());
    }

    private void renderForecast(ForecastUiState state) {
        if (state.isError()) {
            Toast.makeText(this, R.string.toast_forecast_load_error, Toast.LENGTH_SHORT).show();
            return;
        }
        if (state.isLoading()) {
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

        // Aggregate the same hourly items into per-day buckets and feed the daily adapter.
        // The aggregator uses the city's UTC offset so day boundaries match the city, not the device.
        List<DailyForecast> dailyItems = DailyForecastAggregator.aggregate(
                items, currentCityTimezoneOffsetSec);
        if (dailyForecastAdapter == null) {
            dailyForecastAdapter = new DailyForecastAdapter(this, dailyItems);
            rvDailyForecast.setAdapter(dailyForecastAdapter);
        } else {
            dailyForecastAdapter.submitList(dailyItems);
        }
    }

    private void showLoadingState() {
        // Initial load: hide content and show centered spinner. Pull-to-refresh has its own
        // SwipeRefreshLayout spinner, so we do NOT also show the inline ProgressBar in that case.
        if (layoutContent.getVisibility() == View.VISIBLE) {
            return;
        }
        layoutLoading.setVisibility(View.VISIBLE);
        layoutError.setVisibility(View.GONE);
        layoutContent.setVisibility(View.GONE);
    }

    private void showErrorState(@androidx.annotation.Nullable String message) {
        swipeRefresh.setRefreshing(false);
        layoutLoading.setVisibility(View.GONE);
        layoutContent.setVisibility(View.GONE);
        layoutError.setVisibility(View.VISIBLE);
        if (message == null || message.isEmpty()) {
            tvErrorMessage.setText(R.string.error_default_message);
        } else {
            tvErrorMessage.setText(message);
        }
    }

    private void showContentState() {
        swipeRefresh.setRefreshing(false);
        layoutLoading.setVisibility(View.GONE);
        layoutError.setVisibility(View.GONE);
        layoutContent.setVisibility(View.VISIBLE);
    }

    private void bindSunCycle(@androidx.annotation.Nullable Sys sys, int timezoneOffsetSec) {
        if (sys == null || (sys.getSunrise() == 0L && sys.getSunset() == 0L)) {
            layoutSunCycle.setVisibility(View.GONE);
            return;
        }
        tvSunrise.setText(getString(R.string.sunrise_value_format,
                formatCityLocalTime(sys.getSunrise(), timezoneOffsetSec)));
        tvSunset.setText(getString(R.string.sunset_value_format,
                formatCityLocalTime(sys.getSunset(), timezoneOffsetSec)));
        layoutSunCycle.setVisibility(View.VISIBLE);
    }

    /**
     * Crossfade the screen background to a gradient that matches the OWM icon code. The first
     * call snaps directly (no transition from the static XML background); subsequent calls fade
     * over {@link #GRADIENT_FADE_DURATION_MS} ms.
     */
    private void applyDynamicGradient(@androidx.annotation.Nullable String iconCode) {
        int[] next = WeatherGradientMapper.colorsForIconCode(iconCode);
        if (currentGradientColors != null && Arrays.equals(currentGradientColors, next)) {
            return;
        }
        GradientDrawable target = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, next.clone());
        if (currentGradientColors == null) {
            // First render: snap. Animating from the static XML drawable produces an awkward
            // fade since the orientation/stops don't match exactly.
            swipeRefresh.setBackground(target);
        } else {
            GradientDrawable from = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM, currentGradientColors.clone());
            TransitionDrawable transition = new TransitionDrawable(new Drawable[]{from, target});
            transition.setCrossFadeEnabled(true);
            swipeRefresh.setBackground(transition);
            transition.startTransition(GRADIENT_FADE_DURATION_MS);
        }
        currentGradientColors = next;
    }

    private void bindWind(@androidx.annotation.Nullable Wind wind) {
        if (wind == null) {
            tvWind.setText(R.string.placeholder_dash);
            return;
        }
        tvWind.setText(getString(R.string.wind_value_format, wind.getSpeed()));
    }

    private void bindVisibility(int visibilityMeters) {
        if (visibilityMeters <= 0) {
            tvVisibility.setText(R.string.placeholder_dash);
            return;
        }
        if (visibilityMeters >= MAX_VISIBILITY_METERS) {
            tvVisibility.setText(getString(R.string.visibility_value_km_format,
                    visibilityMeters / 1000));
        } else {
            tvVisibility.setText(getString(R.string.visibility_value_km_decimal_format,
                    visibilityMeters / 1000d));
        }
    }

    /**
     * Render a Unix timestamp (seconds, UTC) into the hour:minute that the city itself is seeing.
     * We don't use the device's TimeZone — sunrise should look right even when the user searches
     * for another city.
     */
    private static String formatCityLocalTime(long unixSeconds, int timezoneOffsetSec) {
        long localMillis = (unixSeconds + timezoneOffsetSec) * 1000L;
        SimpleDateFormat fmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        return fmt.format(new Date(localMillis));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getLastLocation();
            } else {
                Toast.makeText(this, R.string.toast_location_permission_required, Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this,
                        R.string.toast_notification_permission_denied,
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
        final List<String> hourLabels = new ArrayList<>();
        int count = Math.min(forecastList.size(), CHART_POINTS);
        SimpleDateFormat apiFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        apiFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        SimpleDateFormat hourFormat = new SimpleDateFormat("HH'h'", Locale.getDefault());
        hourFormat.setTimeZone(cityTimeZoneOrDefault());

        for (int i = 0; i < count; i++) {
            ForecastItem item = forecastList.get(i);
            CurrentWeather m = item.getMain();
            if (m == null) {
                continue;
            }
            entries.add(new Entry(entries.size(), (float) m.getTemp()));
            hourLabels.add(formatHourLabel(item.getDtTxt(), apiFormat, hourFormat));
        }

        LineDataSet dataSet = new LineDataSet(entries, getString(R.string.chart_temp_trend_label));
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

        XAxis xAxis = lineChartTemp.getXAxis();
        xAxis.setSpaceMin(0.3f);
        xAxis.setSpaceMax(0.3f);
        xAxis.setTextColor(android.graphics.Color.WHITE);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setLabelCount(hourLabels.size(), false);
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getAxisLabel(float value, com.github.mikephil.charting.components.AxisBase axis) {
                int idx = Math.round(value);
                if (idx < 0 || idx >= hourLabels.size()) return "";
                return hourLabels.get(idx);
            }
        });
        lineChartTemp.getLegend().setTextColor(android.graphics.Color.WHITE);
        lineChartTemp.invalidate();
    }

    private TimeZone cityTimeZoneOrDefault() {
        if (currentCityTimezoneOffsetSec == 0) {
            return TimeZone.getDefault();
        }
        // GMT[+/-]hh:mm; SimpleDateFormat respects this.
        int totalMinutes = currentCityTimezoneOffsetSec / 60;
        int hours = Math.abs(totalMinutes) / 60;
        int minutes = Math.abs(totalMinutes) % 60;
        String sign = currentCityTimezoneOffsetSec >= 0 ? "+" : "-";
        return TimeZone.getTimeZone(String.format(Locale.US, "GMT%s%02d:%02d", sign, hours, minutes));
    }

    private static String formatHourLabel(@androidx.annotation.Nullable String dtTxt,
                                          SimpleDateFormat apiFormat,
                                          SimpleDateFormat hourFormat) {
        if (dtTxt == null || dtTxt.isEmpty()) return "";
        try {
            Date parsed = apiFormat.parse(dtTxt);
            return parsed == null ? "" : hourFormat.format(parsed);
        } catch (ParseException e) {
            return "";
        }
    }
}
