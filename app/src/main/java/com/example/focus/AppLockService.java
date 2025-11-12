package com.example.focus;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class AppLockService extends AccessibilityService {

    private static final String TAG = "AppLockService";
    private static final String SETTINGS_PACKAGE = "com.android.settings";
    private String mPreviousPackage = "";

    public static final String PREF_SETTINGS_LOCK_ACTIVE = "settingsLockActive";
    public static final String PREF_LOCKED_APPS_SET = "lockedAppsSet";
    public static final String PREF_SETTINGS_UNLOCKED_UNTIL = "settingsUnlockedUntil";

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // ... (this method is correct, no changes needed) ...
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }

        String packageName = event.getPackageName() != null ? event.getPackageName().toString() : null;
        String className = event.getClassName() != null ? event.getClassName().toString() : "";

        if (packageName == null) return;

        if (packageName.equals(getPackageName())) {
            mPreviousPackage = getPackageName();
            return;
        }

        SharedPreferences prefs = getSharedPreferences(StudentDashboardActivity.PREFS_NAME, MODE_PRIVATE);
        boolean isFocusModeActive = prefs.getBoolean(StudentDashboardActivity.PREF_FOCUS_MODE_ACTIVE, false);

        if (isFocusModeActive) {
            // Job A: KIOSK MODE WATCHDOG
            Log.d(TAG, "Focus Mode is Active. Blocking app: " + packageName);

            Intent intent = new Intent(this, FocusModeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);

        } else {
            // Job B & C: SETTINGS and BLOCKED APP CHECK
            boolean isSettingsLockActive = prefs.getBoolean(PREF_SETTINGS_LOCK_ACTIVE, false);
            Set<String> lockedApps = prefs.getStringSet(PREF_LOCKED_APPS_SET, new HashSet<>());
            String savedPin = prefs.getString(StudentDashboardActivity.PREF_SECURITY_PIN, null);
            long currentTime = System.currentTimeMillis();

            if (isSettingsLockActive && savedPin != null) {

                if (className.contains("DeviceAdmin") || className.contains("DevicePolicy")) {
                    Log.d(TAG, "Device Admin screen detected. Forcing PIN check.");
                    launchPinCheck("ACTION_DISABLE_ADMIN");
                    return;
                }

                if (packageName.equals(SETTINGS_PACKAGE)) {
                    long unlockUntil = prefs.getLong(PREF_SETTINGS_UNLOCKED_UNTIL, 0);
                    if (currentTime > unlockUntil) {
                        Log.d(TAG, "Settings app detected. Launching PIN check.");
                        launchPinCheck("UNLOCK_SETTINGS");
                        return;
                    }
                }
            }

            if (lockedApps.contains(packageName)) {
                long appBypassTime = prefs.getLong("BYPASS_" + packageName, 0);
                if (currentTime > appBypassTime) {
                    Log.w(TAG, "Blocked App: " + packageName + " launched. Launching AppBlockActivity.");
                    launchAppBlock(packageName);
                    return;
                }
            }
        }

        mPreviousPackage = packageName;
    }

    private void launchPinCheck(String actionType) {
        mPreviousPackage = getPackageName();
        Intent intent = new Intent(this, PinCheckActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("ACTION_TYPE", actionType);
        startActivity(intent);
    }

    private void launchAppBlock(String packageName) {
        mPreviousPackage = getPackageName();
        Intent intent = new Intent(this, AppBlockActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("BLOCKED_PACKAGE_NAME", packageName);
        startActivity(intent);
    }

    @Override
    public void onInterrupt() {
        Log.e(TAG, "Accessibility service interrupted.");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "Accessibility service connected.");

        // --- THIS IS THE FIX ---
        // Check if the WelcomeActivity is waiting for this service to be enabled.
        SharedPreferences prefs = getSharedPreferences(StudentDashboardActivity.PREFS_NAME, MODE_PRIVATE);
        if (prefs.getBoolean(StudentDashboardActivity.PREF_AWAITING_ACCESSIBILITY, false)) {
            Log.d(TAG, "Service connected, and WelcomeActivity is waiting. Relaunching app.");

            // Clear the flag so this doesn't run every time
            prefs.edit().putBoolean(StudentDashboardActivity.PREF_AWAITING_ACCESSIBILITY, false).apply();

            // Relaunch WelcomeActivity to the front
            Intent intent = new Intent(this, WelcomeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
        }
        // --- END OF FIX ---
    }
}