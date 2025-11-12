package com.example.focus;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {

    private Context mContext;
    private List<Notification> mNotificationList;

    public NotificationAdapter(Context context, List<Notification> notificationList) {
        this.mContext = context;
        this.mNotificationList = notificationList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(mContext).inflate(R.layout.item_notification, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Notification notification = mNotificationList.get(position);

        String fullMessage = notification.getStudentName() + " " + notification.getMessage();
        holder.textMessage.setText(fullMessage);

        if (notification.getTimestamp() != null) {
            holder.textTime.setText(formatTimestamp(notification.getTimestamp().getTime()));
        } else {
            holder.textTime.setText("Just now");
        }

        // Set icon based on message content
        String message = notification.getMessage().toLowerCase();
        if (message.contains("break")) {
            holder.iconType.setImageResource(R.drawable.ic_break_time);
            holder.iconType.clearColorFilter();
        } else if (message.contains("cancelled")) {
            holder.iconType.setImageResource(android.R.drawable.ic_dialog_alert);
            holder.iconType.setColorFilter(ContextCompat.getColor(mContext, android.R.color.holo_red_light));
        } else if (message.contains("linked")) {
            holder.iconType.setImageResource(R.drawable.ic_link_code_icon);
            holder.iconType.clearColorFilter();
        } else {
            holder.iconType.setImageResource(R.drawable.ic_pomodoro);
            holder.iconType.clearColorFilter();
        }
    }

    @Override
    public int getItemCount() {
        return mNotificationList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public ImageView iconType;
        public TextView textMessage;
        public TextView textTime;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            iconType = itemView.findViewById(R.id.iconNotificationType);
            textMessage = itemView.findViewById(R.id.textNotificationMessage);
            textTime = itemView.findViewById(R.id.textNotificationTime);
        }
    }

    private String formatTimestamp(long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;
        long diffMinutes = TimeUnit.MILLISECONDS.toMinutes(diff);
        long diffHours = TimeUnit.MILLISECONDS.toHours(diff);
        long diffDays = TimeUnit.MILLISECONDS.toDays(diff);

        if (diffMinutes < 1) {
            return "Just now";
        } else if (diffMinutes < 60) {
            return String.format(Locale.US, "%d min ago", diffMinutes);
        } else if (diffHours < 24) {
            return String.format(Locale.US, "%d hr ago", diffHours);
        } else if (diffDays < 7) {
            return String.format(Locale.US, "%d days ago", diffDays);
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd", Locale.US);
            return sdf.format(new Date(timestamp));
        }
    }
}