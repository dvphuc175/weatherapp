package com.example.weather_application.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pager adapter that backs the multi-city {@code ViewPager2}. Items are either the user's
 * current GPS location (page 0, optional) or a saved city. Stable IDs are derived from the
 * item key so {@link FragmentStateAdapter} can preserve per-page state across list changes.
 */
public class CityPagerAdapter extends FragmentStateAdapter {

    private final List<PagerItem> items = new ArrayList<>();

    public CityPagerAdapter(@NonNull FragmentActivity activity) {
        super(activity);
    }

    /** Replace the backing list. The pager reuses fragments whose stable ID is still present. */
    public void submitItems(@NonNull List<PagerItem> next) {
        items.clear();
        items.addAll(next);
        notifyDataSetChanged();
    }

    /** {@code -1} if the city isn't currently in the pager. */
    public int indexOfCity(@NonNull String cityName) {
        for (int i = 0; i < items.size(); i++) {
            PagerItem item = items.get(i);
            if (item.type == PagerItem.Type.SAVED
                    && item.cityName != null
                    && item.cityName.equalsIgnoreCase(cityName)) {
                return i;
            }
        }
        return -1;
    }

    @NonNull
    public List<PagerItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    @Nullable
    public PagerItem itemAt(int position) {
        if (position < 0 || position >= items.size()) return null;
        return items.get(position);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        PagerItem item = items.get(position);
        if (item.type == PagerItem.Type.CURRENT_LOCATION) {
            return CityWeatherFragment.forCurrentLocation();
        }
        // SAVED — cityName guaranteed non-null by PagerItem constructor.
        return CityWeatherFragment.forCity(item.cityName);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).stableId();
    }

    @Override
    public boolean containsItem(long itemId) {
        for (PagerItem item : items) {
            if (item.stableId() == itemId) return true;
        }
        return false;
    }

    /** One slot in the pager. {@link Type#CURRENT_LOCATION} has a null city name. */
    public static final class PagerItem {
        public enum Type { CURRENT_LOCATION, SAVED }

        @NonNull public final Type type;
        @Nullable public final String cityName;

        private PagerItem(@NonNull Type type, @Nullable String cityName) {
            this.type = type;
            this.cityName = cityName;
        }

        @NonNull
        public static PagerItem currentLocation() {
            return new PagerItem(Type.CURRENT_LOCATION, null);
        }

        @NonNull
        public static PagerItem saved(@NonNull String cityName) {
            return new PagerItem(Type.SAVED, cityName);
        }

        /** Stable id used by {@link FragmentStateAdapter} for fragment reuse. */
        public long stableId() {
            if (type == Type.CURRENT_LOCATION) {
                return 1L; // single, fixed slot
            }
            // Pinned-city ids start at 2 to avoid colliding with the current-location slot.
            return 2L + (cityName == null ? 0L : (long) cityName.toLowerCase().hashCode() & 0xFFFFFFFFL);
        }
    }
}
