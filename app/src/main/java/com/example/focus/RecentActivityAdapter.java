package com.example.focus;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

// This class is a "bridge" between your data (List<Session>) and the RecyclerView
public class RecentActivityAdapter extends RecyclerView.Adapter<RecentActivityAdapter.ViewHolder> {

    // Simple data model for this adapter
    public static class Session {
        public long timestamp;
        public long durationMinutes;
        public String taskName; // Assuming you might add this later

        public Session(long timestamp, long durationMinutes) {
            this.timestamp = timestamp;
            this.durationMinutes = durationMinutes;
            this.taskName = "Focus Session"; // Default task name
        }
    }

    private Context mContext;
    private List<Session> mSessionList;

    public RecentActivityAdapter(Context context, List<Session> sessionList) {
        this.mContext = context;
        this.mSessionList = sessionList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(mContext).inflate(R.layout.item_recent_activity, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Session session = mSessionList.get(position);

        // Set Task Name
        holder.textActivityName.setText(session.taskName);

        // Set Formatted Date (e.g., "Today", "Yesterday", "Nov 05")
        holder.textActivityTime.setText(formatTimestamp(session.timestamp));

        // Set Duration (e.g., "2.5h Completed", "30m Completed")
        holder.textActivityDuration.setText(formatDuration(session.durationMinutes));
    }

    @Override
    public int getItemCount() {
        return mSessionList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView textActivityName, textActivityTime, textActivityDuration;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            textActivityName = itemView.findViewById(R.id.textActivityName);
            textActivityTime = itemView.findViewById(R.id.textActivityTime);
            textActivityDuration = itemView.findViewById(R.id.textActivityDuration);
        }
    }

    // --- Helper Functions for Formatting ---

    private String formatDuration(long minutes) {
        if (minutes < 60) {
            return String.format(Locale.US, "%dm Completed", minutes);
        } else {
            double hours = minutes / 60.0;
            return String.format(Locale.US, "%.1fh Completed", hours);
        }
    }

    private String formatTimestamp(long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;
        long diffDays = TimeUnit.MILLISECONDS.toDays(diff);

        if (diffDays == 0) {
            return "Today";
        } else if (diffDays == 1) {
            return "Yesterday";
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd", Locale.US);
            return sdf.format(new Date(timestamp));
        }
    }
}