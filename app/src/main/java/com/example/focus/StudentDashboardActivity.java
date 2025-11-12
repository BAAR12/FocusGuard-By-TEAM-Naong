package com.example.focus;

// --- (imports are correct) ---
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.ActionBarDrawerToggle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityManager;
import java.util.HashSet;
import java.util.Set;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.messaging.FirebaseMessaging;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class StudentDashboardActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = "StudentDashboard";

    // --- SharedPreferences Keys ---
    public static final String PREFS_NAME = "FocusGuardPrefs";
    public static final String PREF_SECURITY_PIN = "securityPin";
    public static final String PREF_SETTINGS_UNLOCKED_UNTIL = "settingsUnlockedUntil";
    public static final String PREF_FOCUS_MODE_ACTIVE = "focusModeActive";
    public static final String PREF_FOCUS_DURATION_MILLIS = "focusDurationMillis";
    public static final String PREF_LAST_FOCUS_DURATION = "lastFocusDuration";
    public static final String PREF_REMAINING_FOCUS_TIME = "remainingFocusTime";
    public static final String PREF_AWAITING_ACCESSIBILITY = "awaitingAccessibility";
    public static final String PREF_SETTINGS_LOCK_ACTIVE = "settingsLockActive";
    public static final String PREF_UNINSTALL_LOCK_ACTIVE = "uninstallLockActive";
    public static final String PREF_LOCKED_APPS_SET = "lockedAppsSet";
    public static final String PREF_FOCUS_MODE_LOCK_END = "focusModeLockEnd";

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore mStore;
    private FirebaseUser mCurrentUser;
    private String mUserId;
    private String mParentId;

    // UI
    private TextView mWelcomeText, mTextTimer, mPomodoroCount, mHoursToday;
    private Button mStartButton, mProgressReportButton;
    private Toolbar mToolbar;
    private ConstraintLayout mTimerCard;
    private DrawerLayout mDrawerLayout;
    private NavigationView mNavigationView;
    private TextView mTextReadyToFocus;

    private long mFocusDurationMillis = 25 * 60 * 1000; // 25 minutes default

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_dashboard);

        mAuth = FirebaseAuth.getInstance();
        mStore = FirebaseFirestore.getInstance();
        mCurrentUser = mAuth.getCurrentUser();
        if (mCurrentUser == null) {
            goToLogin();
            return;
        }
        mUserId = mCurrentUser.getUid();

        // Init UI
        setupNavigationDrawer();
        mWelcomeText = findViewById(R.id.textWelcome);
        mTextTimer = findViewById(R.id.textTimer);
        mPomodoroCount = findViewById(R.id.textPomodoroCount);
        mHoursToday = findViewById(R.id.textHoursToday);
        mStartButton = findViewById(R.id.buttonStart);
        mProgressReportButton = findViewById(R.id.buttonProgressReport);
        mTimerCard = findViewById(R.id.timerCard);
        mTextReadyToFocus = findViewById(R.id.textReadyToFocus);

        // Set Listeners
        mStartButton.setOnClickListener(v -> {
            Intent intent = new Intent(StudentDashboardActivity.this, FocusModeActivity.class);
            intent.putExtra("FOCUS_DURATION", mFocusDurationMillis);
            startActivity(intent);
        });

        mProgressReportButton.setOnClickListener(v -> {
            startActivity(new Intent(StudentDashboardActivity.this, ProgressReportActivity.class));
        });

        mTimerCard.setOnClickListener(v -> showEditTimeDialog());

        // Failsafe: Turn off watchdog service if the app starts normally
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.putBoolean(PREF_FOCUS_MODE_ACTIVE, false);
        editor.remove(PREF_REMAINING_FOCUS_TIME);
        editor.remove(PREF_LAST_FOCUS_DURATION);
        editor.apply();

        getAndSaveFCMToken();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume called.");

        // Refresh data every time the screen becomes visible
        Log.d(TAG, "onResume: Fetching latest user data and stats...");
        fetchUserData();
        fetchDashboardStats();
    }

    private void setupNavigationDrawer() {
        mDrawerLayout = findViewById(R.id.drawer_layout);
        mToolbar = findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        mNavigationView = findViewById(R.id.nav_view);
        mNavigationView.setNavigationItemSelectedListener(this);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, mDrawerLayout, mToolbar,
                R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        mDrawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        OnBackPressedCallback callback = new OnBackPressedCallback(true /* enabled by default */) {
            @Override
            public void handleOnBackPressed() {
                if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
                    mDrawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    setEnabled(false);
                    StudentDashboardActivity.super.onBackPressed();
                }
            }
        };
        getOnBackPressedDispatcher().addCallback(this, callback);

        mDrawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(View drawerView) {
                callback.setEnabled(true);
            }
        });
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_settings) {
            startActivity(new Intent(this, ProfileSettingsActivity.class));
        } else if (id == R.id.nav_link_account) {
            startActivity(new Intent(this, LinkAccountActivity.class));
        } else if (id == R.id.nav_logout) {
            mAuth.signOut();
            goToLogin();
        }

        mDrawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    private void updateNavHeader(String name, String email) {
        View headerView = mNavigationView.getHeaderView(0);
        TextView navHeaderName = headerView.findViewById(R.id.nav_header_name);
        TextView navHeaderEmail = headerView.findViewById(R.id.nav_header_email);

        navHeaderName.setText(name);
        navHeaderEmail.setText(email);
    }

    private void showEditTimeDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AlertDialogTheme);

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_time, null);
        final EditText input = view.findViewById(R.id.editTextFocusMinutes);
        Button saveButton = view.findViewById(R.id.buttonDialogSave);
        Button cancelButton = view.findViewById(R.id.buttonDialogCancel);

        long currentMinutes = TimeUnit.MILLISECONDS.toMinutes(mFocusDurationMillis);
        input.setText(String.valueOf(currentMinutes));

        builder.setView(view);
        final AlertDialog dialog = builder.create();

        saveButton.setOnClickListener(v -> {
            String minutesStr = input.getText().toString();
            if (!TextUtils.isEmpty(minutesStr)) {
                try {
                    long minutes = Long.parseLong(minutesStr);
                    if (minutes > 0) {
                        mFocusDurationMillis = TimeUnit.MINUTES.toMillis(minutes);
                        updateTimerDisplay();
                        saveTimePreference(mFocusDurationMillis);
                        dialog.dismiss();
                    } else {
                        Toast.makeText(this, "Please enter a positive number.", Toast.LENGTH_SHORT).show();
                    }
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Invalid number.", Toast.LENGTH_SHORT).show();
                }
            }
        });

        cancelButton.setOnClickListener(v -> dialog.cancel());
        dialog.show();
    }

    private void saveTimePreference(long millis) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("focusDurationMillis", millis);

        mStore.collection("users").document(mUserId)
                .update(userData)
                .addOnSuccessListener(aVoid -> {
                    long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
                    Toast.makeText(StudentDashboardActivity.this, "Focus time saved: " + minutes + " minutes.", Toast.LENGTH_SHORT).show();

                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                            .putLong(PREF_FOCUS_DURATION_MILLIS, millis)
                            .apply();
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Error saving time preference", e);
                    Toast.makeText(StudentDashboardActivity.this, "Failed to save time preference.", Toast.LENGTH_SHORT).show();
                });
    }

    private void updateTimerDisplay() {
        long minutes = TimeUnit.MILLISECONDS.toMinutes(mFocusDurationMillis);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(mFocusDurationMillis) % 60;
        mTextTimer.setText(String.format(Locale.US, "%02d:%02d", minutes, seconds));
    }

    // --- THIS IS THE FIX ---
    private void fetchUserData() {
        mStore.collection("users").document(mUserId).get()
                .addOnSuccessListener(studentDoc -> {
                    long defaultTime = 25 * 60 * 1000;

                    if (studentDoc.exists()) {
                        String firstName = studentDoc.getString("firstName");
                        String email = studentDoc.getString("email");

                        mWelcomeText.setText("Hi, " + firstName + "! 👋");
                        updateNavHeader(firstName, email);

                        if (studentDoc.contains("focusDurationMillis")) {
                            mFocusDurationMillis = studentDoc.getLong("focusDurationMillis");
                        } else {
                            mFocusDurationMillis = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                                    .getLong(PREF_FOCUS_DURATION_MILLIS, defaultTime);
                        }

                        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                                .putLong(PREF_FOCUS_DURATION_MILLIS, mFocusDurationMillis)
                                .apply();
                        updateTimerDisplay();

                        Long totalPomos = studentDoc.getLong("totalPomodoros");
                        Double totalHours = studentDoc.getDouble("totalHours");

                        mPomodoroCount.setText(totalPomos != null ? String.valueOf(totalPomos) : "0");

                        // --- Get security settings from student doc ---
                        mParentId = studentDoc.getString("linkedParentId");
                        Boolean settingsLock = studentDoc.getBoolean("settingsLock");
                        Boolean uninstallLock = studentDoc.getBoolean("uninstallLock");
                        Map<String, Boolean> lockedAppMap = (Map<String, Boolean>) studentDoc.get("lockedAppMap");

                        // Update UI based on link status
                        updateUIForLinkStatus();

                        // --- Now, fetch the PARENT'S doc to get the PIN ---
                        if (mParentId != null && !mParentId.isEmpty()) {
                            mStore.collection("users").document(mParentId).get()
                                    .addOnSuccessListener(parentDoc -> {
                                        if (parentDoc.exists()) {
                                            String securityPin = parentDoc.getString("securityPin");
                                            // Now we have all data. Sync to SharedPreferences.
                                            syncSettingsToPrefs(securityPin, settingsLock, uninstallLock, lockedAppMap);
                                        } else {
                                            // Parent doc doesn't exist? Sync with no PIN.
                                            syncSettingsToPrefs(null, settingsLock, uninstallLock, lockedAppMap);
                                        }
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.w(TAG, "Failed to fetch parent doc", e);
                                        syncSettingsToPrefs(null, settingsLock, uninstallLock, lockedAppMap);
                                    });
                        } else {
                            // No parent is linked. Sync with no PIN.
                            syncSettingsToPrefs(null, false, false, new HashMap<>());
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Failed to load user data.", e);
                    mWelcomeText.setText("Hi, User! 👋");
                    updateNavHeader("FocusGuard User", "error@loading.com");
                    updateTimerDisplay();
                });
    }

    // --- NEW: Helper method to save all settings to SharedPreferences ---
    private void syncSettingsToPrefs(String pin, Boolean settingsLock, Boolean uninstallLock, Map<String, Boolean> lockedAppMap) {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();

        if (pin != null) {
            editor.putString(PREF_SECURITY_PIN, pin);
            Log.d(TAG, "Security PIN synced to SharedPreferences.");
        } else {
            editor.remove(PREF_SECURITY_PIN);
            Log.d(TAG, "No security PIN found. Cleared from SharedPreferences.");
        }

        Set<String> lockedAppPackages = new HashSet<>();
        if (lockedAppMap != null) {
            for (Map.Entry<String, Boolean> entry : lockedAppMap.entrySet()) {
                if (entry.getValue() == Boolean.TRUE) {
                    lockedAppPackages.add(entry.getKey());
                }
            }
        }

        editor.putBoolean(PREF_SETTINGS_LOCK_ACTIVE, settingsLock != null && settingsLock);
        editor.putBoolean(PREF_UNINSTALL_LOCK_ACTIVE, uninstallLock != null && uninstallLock);
        editor.putStringSet(PREF_LOCKED_APPS_SET, lockedAppPackages);

        editor.apply();
        Log.d(TAG, "All security settings synced to SharedPreferences.");
    }

    private void updateUIForLinkStatus() {
        if (mParentId == null || mParentId.isEmpty()) {
            // NOT LINKED
            mTextReadyToFocus.setText("Please link a parent account to use focus mode.");
            mTextReadyToFocus.setTextColor(ContextCompat.getColor(this, R.color.dashboard_accent_yellow)); // Make it stand out

            mStartButton.setEnabled(false);
            mStartButton.setAlpha(0.5f); // Make it look disabled
            mProgressReportButton.setEnabled(false);
            mProgressReportButton.setAlpha(0.5f);
            mTimerCard.setEnabled(false); // Disable editing time

        } else {
            // LINKED
            mTextReadyToFocus.setText("Ready to focus?");
            mTextReadyToFocus.setTextColor(ContextCompat.getColor(this, R.color.dashboard_text_light)); // Default color

            mStartButton.setEnabled(true);
            mStartButton.setAlpha(1.0f);
            mProgressReportButton.setEnabled(true);
            mProgressReportButton.setAlpha(1.0f);
            mTimerCard.setEnabled(true); // Enable editing time
        }
    }

    private void fetchDashboardStats() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long startOfTodayMillis = cal.getTimeInMillis();

        mStore.collection("users").document(mUserId).collection("sessions")
                .whereGreaterThanOrEqualTo("timestamp", startOfTodayMillis)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    double totalMinutes = 0;
                    if (!querySnapshot.isEmpty()) {
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            Long duration = doc.getLong("durationMinutes");
                            if (duration != null) {
                                totalMinutes += duration;
                            }
                        }
                    }
                    double totalHours = totalMinutes / 60.0;
                    mHoursToday.setText(String.format(Locale.US, "%.1fh", totalHours));
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Failed to load session stats.", e);
                    mHoursToday.setText("0.0h");
                });
    }

    private void getAndSaveFCMToken() {
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.w(TAG, "Fetching FCM registration token failed", task.getException());
                        return;
                    }
                    String token = task.getResult();
                    Log.d(TAG, "FCM Token: " + token);
                    mStore.collection("users").document(mUserId)
                            .update("fcmToken", token)
                            .addOnSuccessListener(aVoid -> Log.d(TAG, "FCM Token saved to Firestore"))
                            .addOnFailureListener(e -> Log.w(TAG, "Error saving FCM Token", e));
                });
    }

    private void goToLogin() {
        Intent intent = new Intent(StudentDashboardActivity.this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}