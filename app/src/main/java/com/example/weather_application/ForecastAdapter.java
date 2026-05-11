package com.example.weather_application;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.airbnb.lottie.LottieAnimationView;
import com.example.weather_application.models.CurrentWeather;
import com.example.weather_application.models.ForecastItem;
import com.example.weather_application.models.WeatherDescription;
import com.example.weather_application.util.TemperatureUnit;
import com.example.weather_application.util.UnitFormatter;
import com.example.weather_application.util.WeatherIconMapper;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class ForecastAdapter extends RecyclerView.Adapter<ForecastAdapter.ForecastViewHolder> {

    private final Context context;
    private final List<ForecastItem> forecastList;
    private final SimpleDateFormat apiFormat;
    private final SimpleDateFormat dateFormat;
    private final SimpleDateFormat timeFormat;
    @NonNull
    private TemperatureUnit temperatureUnit;

    public ForecastAdapter(@NonNull Context context, @NonNull List<ForecastItem> forecastList,
                           @NonNull TemperatureUnit temperatureUnit) {
        this.context = context;
        this.forecastList = new ArrayList<>(forecastList);
        this.temperatureUnit = temperatureUnit;
        // OWM trả dt_txt theo UTC ("yyyy-MM-dd HH:mm:ss"). Hiển thị theo giờ local.
        this.apiFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        this.apiFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        this.dateFormat = new SimpleDateFormat("dd/MM", Locale.getDefault());
        this.timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    }

    /** Re-render with a different temperature unit. Cheap — just rebinds the existing list. */
    public void setTemperatureUnit(@NonNull TemperatureUnit unit) {
        if (this.temperatureUnit == unit) return;
        this.temperatureUnit = unit;
        notifyDataSetChanged();
    }

    public void submitList(@NonNull List<ForecastItem> items) {
        forecastList.clear();
        forecastList.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ForecastViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_forecast, parent, false);
        return new ForecastViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ForecastViewHolder holder, int position) {
        ForecastItem item = forecastList.get(position);

        Date parsed = parseApiTime(item.getDtTxt());
        if (parsed != null) {
            holder.tvDate.setText(dateFormat.format(parsed));
            holder.tvTime.setText(timeFormat.format(parsed));
        } else {
            holder.tvDate.setText("");
            holder.tvTime.setText("");
        }

        CurrentWeather main = item.getMain();
        if (main != null) {
            holder.tvForecastTemp.setText(
                    UnitFormatter.formatTemperatureRounded(main.getTemp(), temperatureUnit));
        } else {
            holder.tvForecastTemp.setText("");
        }

        List<WeatherDescription> weather = item.getWeather();
        String iconCode = (weather != null && !weather.isEmpty())
                ? weather.get(0).getIcon()
                : null;
        holder.lavForecastIcon.setAnimation(WeatherIconMapper.rawForIconCode(iconCode));
        holder.lavForecastIcon.playAnimation();

        // Hide pop% when API didn't send it or when chance is too low to be useful (< 10%).
        Double pop = item.getPop();
        if (pop != null && pop >= 0.1) {
            int percent = (int) Math.round(pop * 100d);
            holder.tvForecastPop.setText(
                    context.getString(R.string.pop_value_format, percent));
            holder.tvForecastPop.setVisibility(View.VISIBLE);
        } else {
            holder.tvForecastPop.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return forecastList.size();
    }

    private Date parseApiTime(@androidx.annotation.Nullable String dtTxt) {
        if (dtTxt == null || dtTxt.isEmpty()) {
            return null;
        }
        try {
            return apiFormat.parse(dtTxt);
        } catch (ParseException e) {
            return null;
        }
    }

    public static class ForecastViewHolder extends RecyclerView.ViewHolder {
        final TextView tvDate;
        final TextView tvTime;
        final TextView tvForecastTemp;
        final TextView tvForecastPop;
        final LottieAnimationView lavForecastIcon;

        public ForecastViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvForecastTemp = itemView.findViewById(R.id.tvForecastTemp);
            tvForecastPop = itemView.findViewById(R.id.tvForecastPop);
            lavForecastIcon = itemView.findViewById(R.id.lavForecastIcon);
        }
    }
}
