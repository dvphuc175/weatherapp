package com.example.weather_application; // Nhớ kiểm tra lại tên package của bạn

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.weather_application.models.ForecastItem;
import java.util.List;

public class ForecastAdapter extends RecyclerView.Adapter<ForecastAdapter.ForecastViewHolder> {

    private Context context;
    private List<ForecastItem> forecastList;

    public ForecastAdapter(Context context, List<ForecastItem> forecastList) {
        this.context = context;
        this.forecastList = forecastList;
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

            String timeToShow = rawTime.substring(11, 16);
            holder.tvTime.setText(timeToShow);
        }

        holder.tvForecastTemp.setText(Math.round(item.getMain().getTemp()) + "°C");

        // Gọi Lottie Animation thay vì Glide
        if (item.getWeather() != null && !item.getWeather().isEmpty()) {
            String iconCode = item.getWeather().get(0).getIcon();
            int lottieRes = getLottieRawRes(iconCode);
            holder.lavForecastIcon.setAnimation(lottieRes);
            holder.lavForecastIcon.playAnimation();
        }
    }

    @Override
    public int getItemCount() {
        return forecastList == null ? 0 : forecastList.size();
    }

    // Hàm ánh xạ Icon tương tự như bên MainActivity
    private int getLottieRawRes(String iconCode) {
        switch (iconCode) {
            case "01d": return R.raw.weather_sunny;
            case "01n": return R.raw.weather_night;
            case "02d": case "03d": case "04d": return R.raw.weather_partly_cloudy;
            case "02n": case "03n": case "04n": return R.raw.weather_cloudy_night;
            case "09d": case "10d": return R.raw.weather_partly_shower;
            case "09n": case "10n": return R.raw.weather_rainy_night;
            case "11d": case "11n": return R.raw.weather_thunder;
            case "13d": return R.raw.weather_snow_sunny;
            case "13n": return R.raw.weather_snow_night;
            case "50d": case "50n": return R.raw.weather_mist;
            default: return R.raw.weather_partly_cloudy;
        }
    }

    public class ForecastViewHolder extends RecyclerView.ViewHolder {
        TextView tvDate, tvTime, tvForecastTemp;
        // Đổi ImageView thành LottieAnimationView
        com.airbnb.lottie.LottieAnimationView lavForecastIcon;

        public ForecastViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvForecastTemp = itemView.findViewById(R.id.tvForecastTemp);
            // Ánh xạ Lottie
            lavForecastIcon = itemView.findViewById(R.id.lavForecastIcon);
        }
    }
}