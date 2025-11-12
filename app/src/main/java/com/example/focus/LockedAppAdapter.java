package com.example.focus;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import java.util.Map;

public class LockedAppAdapter extends RecyclerView.Adapter<LockedAppAdapter.ViewHolder> {

    // Simple data model for this adapter
    public static class AppInfo {
        public String appName;
        public String packageName;
        public Drawable icon;
        public boolean isLocked;

        public AppInfo(String name, String pkg, Drawable icon, boolean isLocked) {
            this.appName = name;
            this.packageName = pkg;
            this.icon = icon;
            this.isLocked = isLocked;
        }
    }

    private Context mContext;
    private List<AppInfo> mAppList;
    private Map<String, Boolean> mLockedAppMap; // To track changes

    public LockedAppAdapter(Context context, List<AppInfo> appList, Map<String, Boolean> lockedAppMap) {
        this.mContext = context;
        this.mAppList = appList;
        this.mLockedAppMap = lockedAppMap;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(mContext).inflate(R.layout.item_locked_app, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppInfo app = mAppList.get(position);

        holder.textAppName.setText(app.appName);
        holder.textAppPackage.setText(app.packageName); // Set the package name
        holder.imageAppIcon.setImageDrawable(app.icon);
        holder.switchAppLock.setChecked(app.isLocked);

        holder.switchAppLock.setOnCheckedChangeListener((buttonView, isChecked) -> {
            app.isLocked = isChecked;
            mLockedAppMap.put(app.packageName, isChecked);
        });
    }

    @Override
    public int getItemCount() {
        return mAppList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imageAppIcon;
        TextView textAppName;
        TextView textAppPackage; // <-- Added TextView for package name
        SwitchCompat switchAppLock;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imageAppIcon = itemView.findViewById(R.id.imageAppIcon);
            textAppName = itemView.findViewById(R.id.textAppName);
            textAppPackage = itemView.findViewById(R.id.textAppPackage); // <-- Find the new ID
            switchAppLock = itemView.findViewById(R.id.switchAppLock);
        }
    }
}