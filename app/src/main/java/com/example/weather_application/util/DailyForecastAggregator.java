package com.example.weather_application.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.weather_application.models.CurrentWeather;
import com.example.weather_application.models.DailyForecast;
import com.example.weather_application.models.ForecastItem;
import com.example.weather_application.models.WeatherDescription;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Folds a 3-hour {@link ForecastItem} list (40 entries / 5 days from OWM) into one
 * {@link DailyForecast} per local day for the city being shown. The "local day" is computed
 * using the city's UTC offset, not the device's, so a user in Tokyo searching for London still
 * sees London-local days.
 */
public final class DailyForecastAggregator {

    private static final long ONE_DAY_MILLIS = 24L * 60L * 60L * 1000L;
    /** Synthetic timezone used to bucket items by the city's local date. */
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    private DailyForecastAggregator() {}

    /**
     * @param items              raw 3-hour forecast list from {@link com.example.weather_application.models.ForecastResponse}
     * @param timezoneOffsetSec  city's offset from UTC in seconds (positive east)
     * @return one {@link DailyForecast} per day, ordered chronologically. Empty if {@code items}
     *         is null/empty or contains no usable data.
     */
    @NonNull
    public static List<DailyForecast> aggregate(@Nullable List<ForecastItem> items,
                                                int timezoneOffsetSec) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }

        SimpleDateFormat apiFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        apiFormat.setTimeZone(UTC);
        long offsetMillis = timezoneOffsetSec * 1000L;

        // Preserve insertion order so the output is chronological.
        Map<Long, Bucket> buckets = new LinkedHashMap<>();

        for (ForecastItem item : items) {
            if (item == null) {
                continue;
            }
            Date parsedUtc = parse(apiFormat, item.getDtTxt());
            if (parsedUtc == null) {
                continue;
            }
            long localMillis = parsedUtc.getTime() + offsetMillis;
            long dayKey = (localMillis / ONE_DAY_MILLIS) * ONE_DAY_MILLIS;

            Bucket bucket = buckets.get(dayKey);
            if (bucket == null) {
                bucket = new Bucket(dayKey);
                buckets.put(dayKey, bucket);
            }
            bucket.add(item, localMillis);
        }

        List<DailyForecast> out = new ArrayList<>(buckets.size());
        for (Bucket bucket : buckets.values()) {
            DailyForecast d = bucket.build();
            if (d != null) {
                out.add(d);
            }
        }
        return out;
    }

    @Nullable
    private static Date parse(@NonNull SimpleDateFormat fmt, @Nullable String dtTxt) {
        if (dtTxt == null || dtTxt.isEmpty()) {
            return null;
        }
        try {
            return fmt.parse(dtTxt);
        } catch (ParseException e) {
            return null;
        }
    }

    /**
     * Per-day accumulator. Tracks min/max temp, max pop, and the icon/description from the
     * forecast slot closest to local 12:00 (so the day's icon represents midday weather).
     */
    private static final class Bucket {
        final long localDayStartMillis;
        double minTemp = Double.POSITIVE_INFINITY;
        double maxTemp = Double.NEGATIVE_INFINITY;
        double maxPop = 0d;
        boolean hasTemp = false;

        @Nullable String noonIconCode;
        @Nullable String noonDescription;
        long bestNoonDistanceMillis = Long.MAX_VALUE;

        Bucket(long localDayStartMillis) {
            this.localDayStartMillis = localDayStartMillis;
        }

        void add(@NonNull ForecastItem item, long localItemMillis) {
            CurrentWeather main = item.getMain();
            if (main != null) {
                double temp = main.getTemp();
                if (temp < minTemp) minTemp = temp;
                if (temp > maxTemp) maxTemp = temp;
                hasTemp = true;
            }

            Double pop = item.getPop();
            if (pop != null && pop > maxPop) {
                maxPop = pop;
            }

            // Pick the icon from the slot whose local time is closest to noon (12:00).
            long noonMillis = localDayStartMillis + 12L * 60L * 60L * 1000L;
            long distance = Math.abs(localItemMillis - noonMillis);
            if (distance < bestNoonDistanceMillis) {
                List<WeatherDescription> weather = item.getWeather();
                if (weather != null && !weather.isEmpty()) {
                    WeatherDescription first = weather.get(0);
                    if (first != null) {
                        noonIconCode = first.getIcon();
                        noonDescription = first.getDescription();
                        bestNoonDistanceMillis = distance;
                    }
                }
            }
        }

        @Nullable
        DailyForecast build() {
            if (!hasTemp) {
                return null;
            }
            return new DailyForecast(
                    localDayStartMillis, minTemp, maxTemp, noonIconCode, noonDescription, maxPop);
        }
    }
}
