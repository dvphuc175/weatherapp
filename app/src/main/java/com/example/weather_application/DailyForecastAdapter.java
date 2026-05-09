package com.example.weather_application;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.airbnb.lottie.LottieAnimationView;
import com.example.weather_application.models.DailyForecast;
import com.example.weather_application.util.WeatherIconMapper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Vertical list adapter for {@link DailyForecast} entries. Renders one row per local day with
 * day name (Today / Tomorrow / weekday), short date, midday lottie icon, max-pop pill, and the
 * min/max temperature range.
 */
public class DailyForecastAdapter
        extends RecyclerView.Adapter<DailyForecastAdapter.DailyViewHolder> {

    private static final long ONE_DAY_MILLIS = 24L * 60L * 60L * 1000L;
    /** Show the pop pill only when chance is meaningful — matches the hourly threshold. */
    private static final double POP_VISIBILITY_THRESHOLD = 0.1d;

    private final Context context;
    private final List<DailyForecast> items;
    private final SimpleDateFormat weekdayFormat;
    private final SimpleDateFormat shortDateFormat;
    /** Synthetic UTC zone — we feed it dates already shifted into city-local time. */
    private final TimeZone utc;

    public DailyForecastAdapter(@NonNull Context context, @NonNull List<DailyForecast> items) {
        this.context = context;
        this.items = new ArrayList<>(items);
        this.utc = TimeZone.getTimeZone("UTC");
        this.weekdayFormat = new SimpleDateFormat("EEEE", Locale.getDefault());
        this.weekdayFormat.setTimeZone(utc);
        this.shortDateFormat = new SimpleDateFormat("dd/MM", Locale.getDefault());
        this.shortDateFormat.setTimeZone(utc);
    }

    public void submitList(@NonNull List<DailyForecast> next) {
        items.clear();
        items.addAll(next);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public DailyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context)
                .inflate(R.layout.item_daily_forecast, parent, false);
        return new DailyViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DailyViewHolder holder, int position) {
        DailyForecast item = items.get(position);
        long dayMillis = item.getLocalDayStartUtcMillis();

        holder.tvDayName.setText(dayLabel(dayMillis));
        holder.tvDate.setText(shortDateFormat.format(new Date(dayMillis)));

        holder.tvTempRange.setText(context.getString(
                R.string.daily_temp_range_format,
                Math.round(item.getMinTempCelsius()),
                Math.round(item.getMaxTempCelsius())));

        holder.lavIcon.setAnimation(WeatherIconMapper.rawForIconCode(item.getIconCode()));
        holder.lavIcon.playAnimation();

        double pop = item.getMaxPop();
        if (pop >= POP_VISIBILITY_THRESHOLD) {
            int percent = (int) Math.round(pop * 100d);
            holder.tvPop.setText(context.getString(R.string.pop_value_format, percent));
            holder.tvPop.setVisibility(View.VISIBLE);
        } else {
            holder.tvPop.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /**
     * "Today" / "Tomorrow" for the device's first two days, otherwise the city-local weekday name.
     * The "today" anchor is the device's current day-start so the user sees a familiar label
     * — using the city's day-start would mistakenly say "Today" for the city's calendar day even
     * when the device says it's tomorrow.
     */
    private String dayLabel(long localDayStartUtcMillis) {
        long todayLocalDayStart = startOfDeviceTodayUtcMillis();
        long diffDays = (localDayStartUtcMillis - todayLocalDayStart) / ONE_DAY_MILLIS;
        if (diffDays == 0L) {
            return context.getString(R.string.today_label);
        }
        if (diffDays == 1L) {
            return context.getString(R.string.tomorrow_label);
        }
        return weekdayFormat.format(new Date(localDayStartUtcMillis));
    }

    private long startOfDeviceTodayUtcMillis() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long deviceMidnightWallMillis = cal.getTimeInMillis();
        // Aggregator keys are computed against UTC clock walls, so shift the wall midnight back
        // into that synthetic UTC space.
        int offsetMillis = TimeZone.getDefault().getOffset(deviceMidnightWallMillis);
        return ((deviceMidnightWallMillis + offsetMillis) / ONE_DAY_MILLIS) * ONE_DAY_MILLIS;
    }

    static class DailyViewHolder extends RecyclerView.ViewHolder {
        final TextView tvDayName;
        final TextView tvDate;
        final TextView tvPop;
        final TextView tvTempRange;
        final LottieAnimationView lavIcon;

        DailyViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDayName = itemView.findViewById(R.id.tvDailyDayName);
            tvDate = itemView.findViewById(R.id.tvDailyDate);
            tvPop = itemView.findViewById(R.id.tvDailyPop);
            tvTempRange = itemView.findViewById(R.id.tvDailyTempRange);
            lavIcon = itemView.findViewById(R.id.lavDailyIcon);
        }
    }
}
