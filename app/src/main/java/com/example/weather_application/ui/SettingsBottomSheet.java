package com.example.weather_application.ui;

import android.app.Dialog;
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
import androidx.lifecycle.ViewModelProvider;

import com.example.weather_application.R;
import com.example.weather_application.data.UserPreferences;
import com.example.weather_application.data.local.SavedCity;
import com.example.weather_application.util.TemperatureUnit;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;

import java.util.List;

/**
 * Settings panel: temperature unit toggle, saved-cities list (with per-row remove), clear
 * recent searches. Language is system-driven so we only show a hint.
 */
public class SettingsBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = "SettingsBottomSheet";

    private LinearLayout containerSavedCities;
    private TextView tvNoSavedCities;
    private MainViewModel sharedViewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        UserPreferences prefs = UserPreferences.get(requireContext());
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

        sharedViewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        sharedViewModel.getSavedCities().observe(getViewLifecycleOwner(), this::renderSavedCities);

        MaterialButton btnClear = view.findViewById(R.id.btnClearRecents);
        btnClear.setOnClickListener(v -> {
            sharedViewModel.clearRecentSearches();
            Toast.makeText(requireContext(),
                    R.string.settings_recent_cleared,
                    Toast.LENGTH_SHORT).show();
        });
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
