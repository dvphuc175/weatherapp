package com.example.weather_application.ui;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.example.weather_application.R;
import com.example.weather_application.data.UserPreferences;
import com.example.weather_application.util.TemperatureUnit;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;

/**
 * Settings panel surfaced from the gear icon in {@code MainActivity}'s search row. Hosts the
 * temperature-unit toggle and the "clear recent searches" action. Language is locale-driven,
 * so we only surface a hint instead of building our own picker — Android Settings → Language
 * is the single source of truth.
 */
public class SettingsBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = "SettingsBottomSheet";

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

        MaterialButton btnClear = view.findViewById(R.id.btnClearRecents);
        btnClear.setOnClickListener(v -> {
            // Reuse the activity-scoped MainViewModel so the ChipGroup observer fires.
            MainViewModel vm = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
            vm.clearRecentSearches();
            Toast.makeText(requireContext(),
                    R.string.settings_recent_cleared,
                    Toast.LENGTH_SHORT).show();
        });
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        return super.onCreateDialog(savedInstanceState);
    }
}
