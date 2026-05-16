package com.example.weather_application;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import androidx.viewpager2.widget.ViewPager2;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.example.weather_application.data.UserPreferences;
import com.example.weather_application.data.WeatherRepository;
import com.example.weather_application.data.local.RecentSearch;
import com.example.weather_application.data.local.SavedCity;
import com.example.weather_application.models.WeatherResponse;
import com.example.weather_application.ui.CityPagerAdapter;
import com.example.weather_application.ui.CityPagerAdapter.PagerItem;
import com.example.weather_application.ui.MainViewModel;
import com.example.weather_application.ui.SettingsBottomSheet;
import com.example.weather_application.util.TemperatureUnit;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Activity shell for the multi-city pager. Hosts the top bar (search + settings), the
 * recent-searches chips (revealed on search focus only), and a {@link ViewPager2} of
 * {@link com.example.weather_application.ui.CityWeatherFragment} pages. All per-city weather
 * state lives in the fragments; this activity only owns cross-page concerns (saved-city list,
 * temperature unit, GPS coord) via the shared {@link MainViewModel}.
 */
public class MainActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 100;
    private static final long WORKER_INTERVAL_MINUTES = 15L;

    private EditText etSearchCity;
    private ImageView ivSearch, ivSettings;
    private HorizontalScrollView scrollRecentSearches;
    private ChipGroup chipGroupRecent;
    private LinearLayout layoutEmpty;
    private LinearLayout layoutTopBar;
    private ViewPager2 viewPagerCities;
    private com.google.android.material.tabs.TabLayout pagerDots;

    private CityPagerAdapter pagerAdapter;
    private TabLayoutMediator dotsMediator;

    private MainViewModel viewModel;
    private FusedLocationProviderClient fusedLocationClient;
    private WeatherRepository weatherRepository;
    private UserPreferences userPreferences;

    /** {@code true} once we've granted GPS permission and asked for a fix; controls whether
     *  the pager includes a current-location page slot. */
    private boolean currentLocationEnabled;
    /** Set after a search successfully resolves; consumed once to swipe + pin. */
    @androidx.annotation.Nullable
    private String pendingSwipeAfterAdd;
    /** Latest snapshot of the recent-search list; consulted when the search field focuses. */
    @androidx.annotation.Nullable
    private List<RecentSearch> lastRecentSearches;
    @androidx.annotation.Nullable
    private WeatherRepository.Cancellable cityValidationRequest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);

        bindViews();
        applyEdgeToEdgeInsets();

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        weatherRepository = new WeatherRepository();
        userPreferences = UserPreferences.get(this);

        pagerAdapter = new CityPagerAdapter(this);
        viewPagerCities.setAdapter(pagerAdapter);
        // The dots indicator follows the pager 1:1; no labels.
        dotsMediator = new TabLayoutMediator(pagerDots, viewPagerCities,
                (tab, position) -> { /* dots only */ });
        dotsMediator.attach();

        observeViewModel();
        wireSearchInteractions();

        ivSettings.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            new SettingsBottomSheet().show(getSupportFragmentManager(), SettingsBottomSheet.TAG);
        });

        requestLocationIfNeeded();
    }

    private void bindViews() {
        etSearchCity = findViewById(R.id.etSearchCity);
        ivSearch = findViewById(R.id.ivSearch);
        ivSettings = findViewById(R.id.ivSettings);
        scrollRecentSearches = findViewById(R.id.scrollRecentSearches);
        chipGroupRecent = findViewById(R.id.chipGroupRecent);
        layoutEmpty = findViewById(R.id.layoutEmpty);
        layoutTopBar = findViewById(R.id.layoutTopBar);
        viewPagerCities = findViewById(R.id.viewPagerCities);
        pagerDots = findViewById(R.id.pagerDots);
    }

    /**
     * Apply the status-bar inset as top padding on the floating top bar only — the pager and
     * its per-page gradient fill the entire window (including under the status bar) so there
     * is no opaque panel above the search row.
     */
    private void applyEdgeToEdgeInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(layoutTopBar, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), bars.top,
                    v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });
    }

    private void wireSearchInteractions() {
        ivSearch.setOnClickListener(this::triggerCitySearch);
        etSearchCity.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                triggerCitySearch(v);
                return true;
            }
            return false;
        });
        // Reveal the recent chips only while the user is interacting with the search field.
        // Mirrors iOS Spotlight: tap the field → suggestions appear; leave → suggestions hide.
        etSearchCity.setOnFocusChangeListener((v, hasFocus) ->
                updateRecentChipsVisibility(hasFocus));
    }

    private void observeViewModel() {
        viewModel.getRecentSearches().observe(this, this::renderRecentSearches);
        viewModel.getSavedCities().observe(this, new Observer<List<SavedCity>>() {
            @Override
            public void onChanged(List<SavedCity> cities) {
                rebuildPager(cities);
            }
        });
    }

    private void rebuildPager(@androidx.annotation.Nullable List<SavedCity> cities) {
        List<PagerItem> items = new ArrayList<>();
        if (currentLocationEnabled) {
            items.add(PagerItem.currentLocation());
        }
        if (cities != null) {
            for (SavedCity city : cities) {
                items.add(PagerItem.saved(city.cityName));
            }
        }
        pagerAdapter.submitItems(items);
        boolean hasAnyPage = !items.isEmpty();
        layoutEmpty.setVisibility(hasAnyPage ? View.GONE : View.VISIBLE);
        viewPagerCities.setVisibility(hasAnyPage ? View.VISIBLE : View.GONE);
        pagerDots.setVisibility(items.size() > 1 ? View.VISIBLE : View.GONE);

        if (pendingSwipeAfterAdd != null) {
            int idx = pagerAdapter.indexOfCity(pendingSwipeAfterAdd);
            if (idx >= 0) {
                viewPagerCities.setCurrentItem(idx, true);
                pendingSwipeAfterAdd = null;
            }
        }
    }

    private void renderRecentSearches(@androidx.annotation.Nullable List<RecentSearch> items) {
        lastRecentSearches = items;
        chipGroupRecent.removeAllViews();
        if (items == null || items.isEmpty()) {
            scrollRecentSearches.setVisibility(View.GONE);
            return;
        }
        for (final RecentSearch entry : items) {
            Chip chip = buildRecentChip(entry);
            chipGroupRecent.addView(chip);
        }
        updateRecentChipsVisibility(etSearchCity.isFocused());
    }

    /**
     * Builds a chip with default Material styling (light surface + dark text). Keeps text
     * readable on both light and dark page gradients without custom color overrides.
     */
    @NonNull
    private Chip buildRecentChip(@NonNull RecentSearch entry) {
        Chip chip = new Chip(this);
        chip.setText(entry.cityName);
        chip.setCloseIconVisible(true);
        chip.setCloseIconContentDescription(getString(
                R.string.recent_search_delete_content_description, entry.cityName));
        chip.setOnClickListener(v -> {
            etSearchCity.setText(entry.cityName);
            hideKeyboard(v);
            etSearchCity.clearFocus();
            validateThenPinCity(entry.cityName, false);
        });
        chip.setOnCloseIconClickListener(v -> viewModel.removeRecentSearch(entry.cityName));
        return chip;
    }

    /**
     * Recent chips are visible only while the search field has focus AND there is at least one
     * entry. Other state transitions (typing, blur, submit) reuse this single decision point.
     */
    private void updateRecentChipsVisibility(boolean searchFocused) {
        boolean hasRecents = lastRecentSearches != null && !lastRecentSearches.isEmpty();
        scrollRecentSearches.setVisibility(
                searchFocused && hasRecents ? View.VISIBLE : View.GONE);
    }

    private void triggerCitySearch(View v) {
        String cityName = etSearchCity.getText().toString().trim();
        if (cityName.isEmpty()) {
            Toast.makeText(this, R.string.toast_enter_city, Toast.LENGTH_SHORT).show();
            return;
        }
        hideKeyboard(v);
        // Drop focus so the chips collapse — the search has been issued, no need to keep them
        // showing.
        etSearchCity.clearFocus();
        validateThenPinCity(cityName, true);
    }

    private void hideKeyboard(View v) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        }
    }

    /**
     * Validate unknown city names before mutating saved/recent lists. If the page already exists we
     * can jump immediately; otherwise one lightweight current-weather request confirms the city and
     * provides the canonical display name used for the pinned page.
     */
    private void validateThenPinCity(@NonNull String cityName, boolean recordRecentWhenValid) {
        int idx = pagerAdapter.indexOfCity(cityName);
        if (idx >= 0) {
            viewPagerCities.setCurrentItem(idx, true);
            if (recordRecentWhenValid) {
                viewModel.recordRecentSearch(cityName);
            }
            return;
        }
        if (!weatherRepository.hasApiKey()) {
            Toast.makeText(this, R.string.error_default_message, Toast.LENGTH_SHORT).show();
            return;
        }
        if (cityValidationRequest != null) {
            cityValidationRequest.cancel();
        }
        TemperatureUnit unit = userPreferences.getTemperatureUnit();
        cityValidationRequest = weatherRepository.getCurrentWeatherByCity(cityName, unit,
                new WeatherRepository.ResultCallback<WeatherResponse>() {
                    @Override
                    public void onSuccess(@NonNull WeatherResponse data) {
                        cityValidationRequest = null;
                        String canonicalName = data.getName();
                        String pageCity = canonicalName == null || canonicalName.trim().isEmpty()
                                ? cityName
                                : canonicalName.trim();
                        if (recordRecentWhenValid) {
                            viewModel.recordRecentSearch(pageCity);
                        }
                        pinValidatedCity(pageCity);
                    }

                    @Override
                    public void onError(@androidx.annotation.Nullable String message) {
                        cityValidationRequest = null;
                        Toast.makeText(MainActivity.this,
                                R.string.toast_city_not_found,
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * Pin a city that has already been validated by the API; savedCities observer rebuilds the
     * pager and consumes {@link #pendingSwipeAfterAdd}.
     */
    private void pinValidatedCity(@NonNull String cityName) {
        int idx = pagerAdapter.indexOfCity(cityName);
        if (idx >= 0) {
            viewPagerCities.setCurrentItem(idx, true);
            return;
        }
        pendingSwipeAfterAdd = cityName;
        viewModel.addSavedCity(cityName);
    }

    private void requestLocationIfNeeded() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }
        enableCurrentLocationPage();
        fetchGpsAndDispatch();
    }

    private void enableCurrentLocationPage() {
        if (currentLocationEnabled) return;
        currentLocationEnabled = true;
        // Trigger a rebuild so the GPS page slot appears. Re-use the current saved-cities value.
        rebuildPager(viewModel.getSavedCities().getValue());
    }

    private void fetchGpsAndDispatch() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        CancellationTokenSource cts = new CancellationTokenSource();
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.getToken())
                .addOnSuccessListener(this, location -> {
                    if (location == null) {
                        Toast.makeText(MainActivity.this,
                                R.string.toast_gps_unavailable,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    double lat = location.getLatitude();
                    double lon = location.getLongitude();
                    if (userPreferences.isWeatherAlertsEnabled()) {
                        scheduleWeatherAlertWorker(lat, lon);
                    }
                    viewModel.setCurrentLocation(lat, lon);
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
                WeatherAlertWorker.UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.REPLACE, request);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableCurrentLocationPage();
                fetchGpsAndDispatch();
            } else {
                Toast.makeText(this, R.string.toast_location_permission_required, Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cityValidationRequest != null) {
            cityValidationRequest.cancel();
            cityValidationRequest = null;
        }
        if (dotsMediator != null) {
            dotsMediator.detach();
            dotsMediator = null;
        }
        if (viewPagerCities != null) {
            viewPagerCities.setAdapter(null);
        }
    }
}
