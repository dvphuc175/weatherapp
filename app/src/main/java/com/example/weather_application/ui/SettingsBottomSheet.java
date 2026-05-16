package com.example.weather_application.ui;

import android.Manifest;
import android.app.Dialog;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.example.weather_application.R;
import com.example.weather_application.WeatherAlertWorker;
import com.example.weather_application.data.UserPreferences;
import com.example.weather_application.data.local.SavedCity;
import com.example.weather_application.util.TemperatureUnit;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Settings panel: temperature unit toggle, opt-in weather alerts, saved-cities list (with per-row
 * remove), clear recent searches. Language is system-driven so we only show a hint.
 */
public class SettingsBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = "SettingsBottomSheet";

    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 201;
    private static final long WORKER_INTERVAL_MINUTES = 15L;

    private LinearLayout containerSavedCities;
    private TextView tvNoSavedCities;
    private MaterialSwitch switchWeatherAlerts;
    private MainViewModel sharedViewModel;
    private UserPreferences prefs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        prefs = UserPreferences.get(requireContext());
        TemperatureUnit current = prefs.getTemperatureUnit();

        MaterialButtonToggleGroup toggle = view.findViewById(R.id.toggleUnit);
        toggle.check(current == TemperatureUnit.FAHRENHEIT
                ? R.id.btnUnitFahrenheit
                : R.id.btnUnitCelsius);
        toggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            TemperatureUnit next = checkedId == R.id.btnUnitFahrenheit
                    ? TemperatureUnit.FAHRENHEIT
                    : TemperatureUnit.CELSIUS;
            if (next == prefs.getTemperatureUnit()) return;
            prefs.setTemperatureUnit(next);
        });

        containerSavedCities = view.findViewById(R.id.containerSavedCities);
        tvNoSavedCities = view.findViewById(R.id.tvNoSavedCities);
        switchWeatherAlerts = view.findViewById(R.id.switchWeatherAlerts);

        sharedViewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        sharedViewModel.getSavedCities().observe(getViewLifecycleOwner(), this::renderSavedCities);

        wireWeatherAlertsSwitch();

        MaterialButton btnClear = view.findViewById(R.id.btnClearRecents);
        btnClear.setOnClickListener(v -> {
            sharedViewModel.clearRecentSearches();
            Toast.makeText(requireContext(),
                    R.string.settings_recent_cleared,
                    Toast.LENGTH_SHORT).show();
        });
    }

    private void wireWeatherAlertsSwitch() {
        switchWeatherAlerts.setChecked(prefs.isWeatherAlertsEnabled());
        switchWeatherAlerts.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked == prefs.isWeatherAlertsEnabled()) return;
            if (isChecked && needsNotificationPermission()) {
                buttonView.setChecked(false);
                requestPermissions(
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_REQUEST_CODE);
                return;
            }
            setWeatherAlertsEnabled(isChecked);
        });
    }

    private boolean needsNotificationPermission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED;
    }

    private void setWeatherAlertsEnabled(boolean enabled) {
        prefs.setWeatherAlertsEnabled(enabled);
        if (enabled) {
            MainViewModel.LatLon coord = sharedViewModel.getCurrentLocation().getValue();
            if (coord != null) {
                scheduleWeatherAlertWorker(coord.lat, coord.lon);
            }
            Toast.makeText(requireContext(),
                    R.string.settings_weather_alerts_enabled,
                    Toast.LENGTH_SHORT).show();
        } else {
            WorkManager.getInstance(requireContext())
                    .cancelUniqueWork(WeatherAlertWorker.UNIQUE_WORK_NAME);
            Toast.makeText(requireContext(),
                    R.string.settings_weather_alerts_disabled,
                    Toast.LENGTH_SHORT).show();
        }
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
        WorkManager.getInstance(requireContext()).enqueueUniquePeriodicWork(
                WeatherAlertWorker.UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != NOTIFICATION_PERMISSION_REQUEST_CODE) return;
        boolean granted = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (granted) {
            if (switchWeatherAlerts != null) {
                switchWeatherAlerts.setChecked(true);
            } else {
                setWeatherAlertsEnabled(true);
            }
        } else {
            prefs.setWeatherAlertsEnabled(false);
            Toast.makeText(requireContext(),
                    R.string.toast_notification_permission_denied,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void renderSavedCities(@Nullable List<SavedCity> cities) {
        containerSavedCities.removeAllViews();
        if (cities == null || cities.isEmpty()) {
            tvNoSavedCities.setVisibility(View.VISIBLE);
            containerSavedCities.setVisibility(View.GONE);
            return;
        }
        tvNoSavedCities.setVisibility(View.GONE);
        containerSavedCities.setVisibility(View.VISIBLE);

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (SavedCity city : cities) {
            View row = inflater.inflate(R.layout.item_saved_city, containerSavedCities, false);
            TextView tvName = row.findViewById(R.id.tvSavedCityName);
            ImageView btnRemove = row.findViewById(R.id.btnRemoveSavedCity);
            tvName.setText(city.cityName);
            btnRemove.setContentDescription(getString(
                    R.string.settings_saved_city_remove_content_description, city.cityName));
            btnRemove.setOnClickListener(v -> sharedViewModel.removeSavedCity(city.cityName));
            containerSavedCities.addView(row);
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        return super.onCreateDialog(savedInstanceState);
    }
}
