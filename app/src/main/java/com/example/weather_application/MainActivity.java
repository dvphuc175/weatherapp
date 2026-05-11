package com.example.weather_application;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
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
import androidx.core.content.ContextCompat;
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

import com.example.weather_application.data.local.RecentSearch;
import com.example.weather_application.data.local.SavedCity;
import com.example.weather_application.ui.CityPagerAdapter;
import com.example.weather_application.ui.CityPagerAdapter.PagerItem;
import com.example.weather_application.ui.MainViewModel;
import com.example.weather_application.ui.SettingsBottomSheet;
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
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 101;
    private static final long WORKER_INTERVAL_MINUTES = 15L;
    private static final String WORKER_UNIQUE_NAME = "WeatherAlertWork";

    /** Translucent fill that matches the search field so chips don't pop. */
    private static final int CHIP_BACKGROUND_ARGB = 0x1AFFFFFF;
    /** Faint white stroke for chip outlines. */
    private static final int CHIP_STROKE_ARGB = 0x55FFFFFF;

    private EditText etSearchCity;
    private ImageView ivSearch, ivSettings;
    private HorizontalScrollView scrollRecentSearches;
    private ChipGroup chipGroupRecent;
    private LinearLayout layoutEmpty;
    private ViewPager2 viewPagerCities;
    private com.google.android.material.tabs.TabLayout pagerDots;

    private CityPagerAdapter pagerAdapter;
    private TabLayoutMediator dotsMediator;

    private MainViewModel viewModel;
    private FusedLocationProviderClient fusedLocationClient;

    /** {@code true} once we've granted GPS permission and asked for a fix; controls whether
     *  the pager includes a current-location page slot. */
    private boolean currentLocationEnabled;
    /** Set after a search successfully resolves; consumed once to swipe + pin. */
    @androidx.annotation.Nullable
    private String pendingSwipeAfterAdd;
    /** Latest snapshot of the recent-search list; consulted when the search field focuses. */
    @androidx.annotation.Nullable
    private List<RecentSearch> lastRecentSearches;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);

        bindViews();
        applyEdgeToEdgeInsets();

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST_CODE);
        }

        requestLocationIfNeeded();
    }

    private void bindViews() {
        etSearchCity = findViewById(R.id.etSearchCity);
        ivSearch = findViewById(R.id.ivSearch);
        ivSettings = findViewById(R.id.ivSettings);
        scrollRecentSearches = findViewById(R.id.scrollRecentSearches);
        chipGroupRecent = findViewById(R.id.chipGroupRecent);
        layoutEmpty = findViewById(R.id.layoutEmpty);
        viewPagerCities = findViewById(R.id.viewPagerCities);
        pagerDots = findViewById(R.id.pagerDots);
    }

    private void applyEdgeToEdgeInsets() {
        View root = findViewById(R.id.layoutActivityRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            root.setPadding(root.getPaddingLeft(), bars.top,
                    root.getPaddingRight(), bars.bottom);
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
     * Builds a translucent outlined chip styled for the dark page gradient. Material's default
     * surface-tinted chip looks like a white sticker on the gradient; this variant matches the
     * search field instead.
     */
    @NonNull
    private Chip buildRecentChip(@NonNull RecentSearch entry) {
        Chip chip = new Chip(this);
        chip.setText(entry.cityName);
        chip.setChipBackgroundColor(ColorStateList.valueOf(CHIP_BACKGROUND_ARGB));
        chip.setChipStrokeColor(ColorStateList.valueOf(CHIP_STROKE_ARGB));
        chip.setChipStrokeWidth(1f);
        int onBrand = ContextCompat.getColor(this, R.color.text_primary_on_brand);
        chip.setTextColor(onBrand);
        chip.setCloseIconTint(ColorStateList.valueOf(onBrand));
        chip.setCloseIconVisible(true);
        chip.setRippleColor(ColorStateList.valueOf(Color.argb(0x33, 0xFF, 0xFF, 0xFF)));
        chip.setCloseIconContentDescription(getString(
                R.string.recent_search_delete_content_description, entry.cityName));
        chip.setOnClickListener(v -> {
            etSearchCity.setText(entry.cityName);
            jumpToOrPinCity(entry.cityName);
            hideKeyboard(v);
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
        viewModel.recordRecentSearch(cityName);
        jumpToOrPinCity(cityName);
    }

    private void hideKeyboard(View v) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        }
    }

    /**
     * If the city is already pinned: just swipe to its page. Otherwise: pin it and the
     * {@code savedCities} observer will rebuild the pager + swipe via
     * {@link #pendingSwipeAfterAdd}.
     */
    private void jumpToOrPinCity(@NonNull String cityName) {
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
                    scheduleWeatherAlertWorker(lat, lon);
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
                WORKER_UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
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
        } else if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, R.string.toast_notification_permission_denied,
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dotsMediator != null) {
            dotsMediator.detach();
            dotsMediator = null;
        }
        if (viewPagerCities != null) {
            viewPagerCities.setAdapter(null);
        }
    }
}
