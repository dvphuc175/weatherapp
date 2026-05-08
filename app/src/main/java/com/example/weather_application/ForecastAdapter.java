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
import com.example.weather_application.util.WeatherIconMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ForecastAdapter extends RecyclerView.Adapter<ForecastAdapter.ForecastViewHolder> {

    private final Context context;
    private final List<ForecastItem> forecastList;

    public ForecastAdapter(@NonNull Context context, @NonNull List<ForecastItem> forecastList) {
        this.context = context;
        this.forecastList = new ArrayList<>(forecastList);
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

        String rawTime = item.getDtTxt();
        if (rawTime != null && rawTime.length() >= 16) {
            String month = rawTime.substring(5, 7);
            String day = rawTime.substring(8, 10);
            holder.tvDate.setText(day + "/" + month);
            holder.tvTime.setText(rawTime.substring(11, 16));
        } else {
            holder.tvDate.setText("");
            holder.tvTime.setText("");
        }

        CurrentWeather main = item.getMain();
        if (main != null) {
            holder.tvForecastTemp.setText(
                    String.format(Locale.getDefault(), "%d°C", Math.round(main.getTemp())));
        } else {
            holder.tvForecastTemp.setText("");
        }

        List<WeatherDescription> weather = item.getWeather();
        String iconCode = (weather != null && !weather.isEmpty())
                ? weather.get(0).getIcon()
                : null;
        holder.lavForecastIcon.setAnimation(WeatherIconMapper.rawForIconCode(iconCode));
        holder.lavForecastIcon.playAnimation();
    }

    @Override
    public int getItemCount() {
        return forecastList.size();
    }

    public static class ForecastViewHolder extends RecyclerView.ViewHolder {
        final TextView tvDate;
        final TextView tvTime;
        final TextView tvForecastTemp;
        final LottieAnimationView lavForecastIcon;

        public ForecastViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvForecastTemp = itemView.findViewById(R.id.tvForecastTemp);
            lavForecastIcon = itemView.findViewById(R.id.lavForecastIcon);
        }
    }
}
