package com.example.focus;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import androidx.annotation.NonNull;

/**
 * This class is required by the Android system to handle Device Admin events.
 */
public class DeviceAdminReceiver extends android.app.admin.DeviceAdminReceiver {

    @Override
    public void onEnabled(@NonNull Context context, @NonNull Intent intent) {
        super.onEnabled(context, intent);
        Toast.makeText(context, "FocusGuard Admin: Enabled", Toast.LENGTH_SHORT).show();
    }

    @Override
    public CharSequence onDisableRequested(@NonNull Context context, @NonNull Intent intent) {
        // --- THIS IS THE FIX ---
        // Instead of just showing a message, we launch our PIN check screen.
        // This will appear *over* the settings screen, blocking the user.
        Intent pinIntent = new Intent(context, PinCheckActivity.class);
        // We must add this flag because we are starting an activity from a (non-activity) Receiver
        pinIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        pinIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(pinIntent);

        // This is the warning message that appears *before* our activity launches.
        return "A PIN is required to make this change.";
    }

    @Override
    public void onDisabled(@NonNull Context context, @NonNull Intent intent) {
        super.onDisabled(context, intent);
        Toast.makeText(context, "FocusGuard Admin: Disabled", Toast.LENGTH_SHORT).show();

        // Optional: Notify the parent via Firestore that protection was disabled.
        // This would require a service or WorkManager, as this receiver is short-lived.
    }

    @Override
    public void onPasswordFailed(@NonNull Context context, @NonNull Intent intent) {
        super.onPasswordFailed(context, intent);
        // This is not for our custom PIN, but for the device's main lock screen.
    }
}