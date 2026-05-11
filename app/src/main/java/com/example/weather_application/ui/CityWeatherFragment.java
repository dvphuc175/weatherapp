package com.example.weather_application.ui;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.airbnb.lottie.LottieAnimationView;
import com.example.weather_application.DailyForecastAdapter;
import com.example.weather_application.ForecastAdapter;
import com.example.weather_application.R;
import com.example.weather_application.data.UserPreferences;
import com.example.weather_application.models.CurrentWeather;
import com.example.weather_application.models.DailyForecast;
import com.example.weather_application.models.ForecastItem;
import com.example.weather_application.models.Sys;
import com.example.weather_application.models.WeatherDescription;
import com.example.weather_application.models.WeatherResponse;
import com.example.weather_application.models.Wind;
import com.example.weather_application.util.DailyForecastAggregator;
import com.example.weather_application.util.TemperatureUnit;
import com.example.weather_application.util.UnitFormatter;
import com.example.weather_application.util.WeatherGradientMapper;
import com.example.weather_application.util.WeatherIconMapper;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.android.material.button.MaterialButtonToggleGroup;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * One page of the multi-city pager. Owns its own SwipeRefreshLayout, gradient, loading/error/
 * content states, hourly/daily toggle, and offline banner. State is driven by a per-instance
 * {@link CityViewModel} so swiping between pages preserves per-city rendering.
 *
 * <p>The fragment is created with two args: {@link #ARG_CITY_NAME} (nullable) and
 * {@link #ARG_IS_CURRENT_LOCATION} (boolean). When {@code isCurrentLocation = true} the page
 * waits for the activity-shared {@link MainViewModel} to publish a GPS coord; otherwise it
 * immediately loads the city by name.
 */
public class CityWeatherFragment extends Fragment {

    public static final String ARG_CITY_NAME = "city_name";
    public static final String ARG_IS_CURRENT_LOCATION = "is_current_location";

    private static final int CHART_POINTS = 8;
    private static final int MAX_VISIBILITY_METERS = 10_000;
    private static final int GRADIENT_FADE_DURATION_MS = 600;
    private static final String STATE_DAILY_MODE = "state_daily_mode";
    /** Approximate height of the floating top bar (search row + margins). Used to pad the
     *  scrollable content so it isn't hidden under the activity's overlay. */
    private static final int TOP_BAR_OVERLAY_DP = 64;

    /** Build a fragment bound to a saved city name. */
    @NonNull
    public static CityWeatherFragment forCity(@NonNull String cityName) {
        CityWeatherFragment f = new CityWeatherFragment();
        Bundle args = new Bundle();
        args.putString(ARG_CITY_NAME, cityName);
        args.putBoolean(ARG_IS_CURRENT_LOCATION, false);
        // The CityViewModel reads its bound city out of the SavedStateHandle, which is
        // initialised from the fragment args by AbstractSavedStateViewModelFactory.
        args.putString(CityViewModel.KEY_CITY_NAME, cityName);
        args.putBoolean(CityViewModel.KEY_IS_CURRENT_LOCATION, false);
        f.setArguments(args);
        return f;
    }

    /** Build a fragment bound to the user's current GPS location. */
    @NonNull
    public static CityWeatherFragment forCurrentLocation() {
        CityWeatherFragment f = new CityWeatherFragment();
        Bundle args = new Bundle();
        args.putString(ARG_CITY_NAME, null);
        args.putBoolean(ARG_IS_CURRENT_LOCATION, true);
        args.putString(CityViewModel.KEY_CITY_NAME, null);
        args.putBoolean(CityViewModel.KEY_IS_CURRENT_LOCATION, true);
        f.setArguments(args);
        return f;
    }

    private CityViewModel viewModel;
    private MainViewModel sharedViewModel;

    private SwipeRefreshLayout swipeRefresh;
    private LinearLayout layoutFragmentRoot;
    private FrameLayout layoutLoading;
    private LinearLayout layoutError, layoutContent;
    private TextView tvErrorMessage;
    private Button btnRetry;
    private TextView tvOfflineBanner;

    private TextView tvLocation, tvTemperature, tvDescription, tvHumidity, tvFeelsLike;
    private TextView tvSunrise, tvSunset, tvWind, tvPressure, tvVisibility;
    private LinearLayout layoutSunCycle;
    private LottieAnimationView lavWeatherIcon, lavLocationIcon;
    private View cardHumidityFeels, cardWindPressureVisibility;
    private MaterialButtonToggleGroup toggleForecastMode;
    private RecyclerView rvForecast, rvDailyForecast;
    private LineChart lineChartTemp;

    private ForecastAdapter forecastAdapter;
    private DailyForecastAdapter dailyForecastAdapter;

    @NonNull
    private TemperatureUnit currentUnit = TemperatureUnit.CELSIUS;
    /** Sticky timezone offset (seconds from UTC) for the city showing on this page. */
    private int currentCityTimezoneOffsetSec;
    @Nullable
    private int[] currentGradientColors;
    private boolean dailyMode;
    private boolean initialLoadDispatched;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_city_weather, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViews(view);
        if (savedInstanceState != null) {
            dailyMode = savedInstanceState.getBoolean(STATE_DAILY_MODE, false);
        }
        wireForecastModeToggle();
        applyForecastModeVisibility();

        rvForecast.setLayoutManager(new LinearLayoutManager(
                requireContext(), LinearLayoutManager.HORIZONTAL, false));
        rvDailyForecast.setLayoutManager(new LinearLayoutManager(requireContext()));

        viewModel = new ViewModelProvider(this,
                new CityViewModel.Factory(this, getArguments(), requireActivity().getApplication()))
                .get(CityViewModel.class);
        sharedViewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        currentUnit = UserPreferences.get(requireContext()).getTemperatureUnit();
        applyPlaceholderTemperatures(currentUnit);

        swipeRefresh.setColorSchemeResources(R.color.gradient_start, R.color.gradient_end);
        swipeRefresh.setOnRefreshListener(viewModel::refresh);
        btnRetry.setOnClickListener(v -> viewModel.refresh());

        applyContentInsetsForOverlay();
        observeViewModels();
        dispatchInitialLoad();
    }

    /**
     * The activity hosts the search bar as a floating overlay so the page gradient bleeds
     * edge-to-edge. Pad the scrollable content by the status-bar inset + the overlay height
     * so the offline banner / weather card aren't hidden underneath it.
     */
    private void applyContentInsetsForOverlay() {
        int topBarPx = (int) (TOP_BAR_OVERLAY_DP
                * getResources().getDisplayMetrics().density);
        ViewCompat.setOnApplyWindowInsetsListener(layoutFragmentRoot, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), bars.top + topBarPx,
                    v.getPaddingRight(), bars.bottom);
            return insets;
        });
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_DAILY_MODE, dailyMode);
    }

    private void bindViews(@NonNull View root) {
        swipeRefresh = root.findViewById(R.id.swipeRefresh);
        layoutFragmentRoot = root.findViewById(R.id.layoutFragmentRoot);
        layoutLoading = root.findViewById(R.id.layoutLoading);
        layoutError = root.findViewById(R.id.layoutError);
        layoutContent = root.findViewById(R.id.layoutContent);
        tvErrorMessage = root.findViewById(R.id.tvErrorMessage);
        btnRetry = root.findViewById(R.id.btnRetry);
        tvOfflineBanner = root.findViewById(R.id.tvOfflineBanner);

        tvLocation = root.findViewById(R.id.tvLocation);
        tvTemperature = root.findViewById(R.id.tvTemperature);
        tvDescription = root.findViewById(R.id.tvDescription);
        tvHumidity = root.findViewById(R.id.tvHumidity);
        tvFeelsLike = root.findViewById(R.id.tvFeelsLike);
        tvSunrise = root.findViewById(R.id.tvSunrise);
        tvSunset = root.findViewById(R.id.tvSunset);
        tvWind = root.findViewById(R.id.tvWind);
        tvPressure = root.findViewById(R.id.tvPressure);
        tvVisibility = root.findViewById(R.id.tvVisibility);
        layoutSunCycle = root.findViewById(R.id.layoutSunCycle);

        lavWeatherIcon = root.findViewById(R.id.lavWeatherIcon);
        lavLocationIcon = root.findViewById(R.id.lavLocationIcon);

        cardHumidityFeels = root.findViewById(R.id.cardHumidityFeels);
        cardWindPressureVisibility = root.findViewById(R.id.cardWindPressureVisibility);
        toggleForecastMode = root.findViewById(R.id.toggleForecastMode);
        rvForecast = root.findViewById(R.id.rvForecast);
        rvDailyForecast = root.findViewById(R.id.rvDailyForecast);
        lineChartTemp = root.findViewById(R.id.lineChartTemp);
    }

    private void wireForecastModeToggle() {
        toggleForecastMode.check(dailyMode ? R.id.btnForecastDaily : R.id.btnForecastHourly);
        toggleForecastMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            boolean nextDaily = checkedId == R.id.btnForecastDaily;
            if (nextDaily == dailyMode) return;
            dailyMode = nextDaily;
            applyForecastModeVisibility();
        });
    }

    private void applyForecastModeVisibility() {
        rvForecast.setVisibility(dailyMode ? View.GONE : View.VISIBLE);
        lineChartTemp.setVisibility(dailyMode ? View.GONE : View.VISIBLE);
        rvDailyForecast.setVisibility(dailyMode ? View.VISIBLE : View.GONE);
        cardHumidityFeels.setVisibility(dailyMode ? View.GONE : View.VISIBLE);
        cardWindPressureVisibility.setVisibility(dailyMode ? View.GONE : View.VISIBLE);
    }

    private void observeViewModels() {
        viewModel.getWeather().observe(getViewLifecycleOwner(), this::renderWeather);
        viewModel.getForecast().observe(getViewLifecycleOwner(), this::renderForecast);
        viewModel.getServingFromCache().observe(getViewLifecycleOwner(), this::renderOfflineBanner);
        viewModel.getCacheSavedAt().observe(getViewLifecycleOwner(), savedAt ->
                renderOfflineBanner(viewModel.getServingFromCache().getValue()));

        // Track unit changes through the shared VM so all pages reflow when the user flips °C/°F.
        sharedViewModel.getTemperatureUnit().observe(getViewLifecycleOwner(), unit -> {
            if (unit == null || unit == currentUnit) return;
            currentUnit = unit;
            applyPlaceholderTemperatures(unit);
            if (forecastAdapter != null) {
                forecastAdapter.setTemperatureUnit(unit);
            }
        });

        // Current-location pages only know their coord once the activity hands it over.
        if (viewModel.isCurrentLocationPage()) {
            sharedViewModel.getCurrentLocation().observe(getViewLifecycleOwner(), coord -> {
                if (coord == null) return;
                viewModel.loadByCoord(coord.lat, coord.lon);
            });
        }
    }

    private void dispatchInitialLoad() {
        if (initialLoadDispatched) return;
        if (viewModel.isCurrentLocationPage()) {
            // Coord arrives via the observer above; nothing to do here.
            initialLoadDispatched = true;
            return;
        }
        String cityName = viewModel.getBoundCityName();
        if (cityName == null || cityName.isEmpty()) return;
        viewModel.loadByCity(cityName);
        initialLoadDispatched = true;
    }

    private void renderWeather(WeatherUiState state) {
        if (state.isLoading() && state.getData() == null) {
            showLoadingState();
            return;
        }
        if (state.isError() && layoutContent.getVisibility() != View.VISIBLE) {
            showErrorState(state.getErrorMessage());
            return;
        }
        if (state.isError()) {
            swipeRefresh.setRefreshing(false);
            Toast.makeText(requireContext(), R.string.toast_city_not_found, Toast.LENGTH_SHORT).show();
            return;
        }
        WeatherResponse data = state.getData();
        if (data == null) return;
        showContentState();

        String name = data.getName();
        tvLocation.setText(name == null ? "" : name.toUpperCase(Locale.ROOT));
        lavLocationIcon.setAnimation(state.isCurrentLocation() ? R.raw.anim_location : R.raw.anim_search);
        lavLocationIcon.playAnimation();

        CurrentWeather main = data.getMain();
        if (main != null) {
            tvTemperature.setText(UnitFormatter.formatTemperatureRounded(main.getTemp(), currentUnit));
            tvHumidity.setText(String.format(Locale.getDefault(), "%d%%", main.getHumidity()));
            tvFeelsLike.setText(UnitFormatter.formatTemperatureRounded(main.getFeelsLike(), currentUnit));
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
            Toast.makeText(requireContext(), R.string.toast_forecast_load_error, Toast.LENGTH_SHORT).show();
            return;
        }
        if (state.isLoading()) return;
        List<ForecastItem> items = state.getItems();
        if (forecastAdapter == null) {
            forecastAdapter = new ForecastAdapter(requireContext(), items, currentUnit);
            rvForecast.setAdapter(forecastAdapter);
        } else {
            forecastAdapter.setTemperatureUnit(currentUnit);
            forecastAdapter.submitList(items);
        }
        drawTemperatureChart(items);

        List<DailyForecast> dailyItems = DailyForecastAggregator.aggregate(
                items, currentCityTimezoneOffsetSec);
        if (dailyForecastAdapter == null) {
            dailyForecastAdapter = new DailyForecastAdapter(requireContext(), dailyItems);
            rvDailyForecast.setAdapter(dailyForecastAdapter);
        } else {
            dailyForecastAdapter.submitList(dailyItems);
        }
    }

    private void renderOfflineBanner(@Nullable Boolean servingFromCache) {
        boolean offline = Boolean.TRUE.equals(servingFromCache);
        if (!offline) {
            tvOfflineBanner.setVisibility(View.GONE);
            return;
        }
        Long savedAt = viewModel.getCacheSavedAt().getValue();
        tvOfflineBanner.setText(formatOfflineBanner(savedAt));
        tvOfflineBanner.setVisibility(View.VISIBLE);
    }

    private String formatOfflineBanner(@Nullable Long savedAt) {
        if (savedAt == null) {
            return getString(R.string.offline_banner_just_now);
        }
        long ageMs = Math.max(0L, System.currentTimeMillis() - savedAt);
        long minutes = ageMs / 60_000L;
        if (minutes < 1L) return getString(R.string.offline_banner_just_now);
        if (minutes < 60L) return getString(R.string.offline_banner_minutes, (int) minutes);
        long hours = minutes / 60L;
        if (hours < 24L) return getString(R.string.offline_banner_hours, (int) hours);
        long days = hours / 24L;
        return getString(R.string.offline_banner_days, (int) days);
    }

    private void applyPlaceholderTemperatures(@NonNull TemperatureUnit unit) {
        String placeholder = UnitFormatter.temperaturePlaceholder(unit);
        if (tvTemperature != null && (tvTemperature.getText() == null
                || tvTemperature.getText().toString().startsWith("--"))) {
            tvTemperature.setText(placeholder);
        }
        if (tvFeelsLike != null && (tvFeelsLike.getText() == null
                || tvFeelsLike.getText().toString().startsWith("--"))) {
            tvFeelsLike.setText(placeholder);
        }
    }

    private void showLoadingState() {
        if (layoutContent.getVisibility() == View.VISIBLE) return;
        layoutLoading.setVisibility(View.VISIBLE);
        layoutError.setVisibility(View.GONE);
        layoutContent.setVisibility(View.GONE);
    }

    private void showErrorState(@Nullable String message) {
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

    private void bindSunCycle(@Nullable Sys sys, int timezoneOffsetSec) {
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
     * Crossfade the page background to a gradient that matches the OWM icon code. First call
     * snaps; subsequent calls fade.
     */
    private void applyDynamicGradient(@Nullable String iconCode) {
        int[] next = WeatherGradientMapper.colorsForIconCode(iconCode);
        if (currentGradientColors != null && Arrays.equals(currentGradientColors, next)) {
            return;
        }
        GradientDrawable target = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, next.clone());
        if (currentGradientColors == null) {
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

    private void bindWind(@Nullable Wind wind) {
        if (wind == null) {
            tvWind.setText(R.string.placeholder_dash);
            return;
        }
        tvWind.setText(UnitFormatter.formatWind(wind.getSpeed(), currentUnit));
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

    private static String formatCityLocalTime(long unixSeconds, int timezoneOffsetSec) {
        long localMillis = (unixSeconds + timezoneOffsetSec) * 1000L;
        SimpleDateFormat fmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        return fmt.format(new Date(localMillis));
    }

    private static String capitalize(String text) {
        if (text == null || text.isEmpty()) return "";
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
            if (m == null) continue;
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
        if (currentCityTimezoneOffsetSec == 0) return TimeZone.getDefault();
        int totalMinutes = currentCityTimezoneOffsetSec / 60;
        int hours = Math.abs(totalMinutes) / 60;
        int minutes = Math.abs(totalMinutes) % 60;
        String sign = currentCityTimezoneOffsetSec >= 0 ? "+" : "-";
        return TimeZone.getTimeZone(String.format(Locale.US, "GMT%s%02d:%02d", sign, hours, minutes));
    }

    private static String formatHourLabel(@Nullable String dtTxt,
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
