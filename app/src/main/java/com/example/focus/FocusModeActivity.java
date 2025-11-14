package com.example.focus;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FieldValue;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class FocusModeActivity extends AppCompatActivity {

    private static final String TAG = "FocusModeActivity";

    private long mFocusDurationMillis; // The *total* duration for this session

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore mStore;
    private String mUserId;
    private String mUserFirstName = "Student";
    private String mParentId;

    // UI
    private TextView mTimerText, mQuoteText, mToolbarTitle;
    private Button mEmergencyExitButton;
    private ImageButton mPausePlayButton, mResetButton, mButtonStartBreak;

    // Timer
    private CountDownTimer mCountDownTimer;
    private long mTimeLeftInMillis;
    private boolean mTimerRunning = false;
    private boolean mIsPaused = false;
    private boolean mIsInBreak = false;
    private boolean mKioskModeActive = false; // Tracks if startLockTask has been called

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_focus_mode);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);


        mAuth = FirebaseAuth.getInstance();
        mStore = FirebaseFirestore.getInstance();
        if (mAuth.getCurrentUser() != null) {
            mUserId = mAuth.getCurrentUser().getUid();
        } else {
            goToLogin();
            return;
        }

        SharedPreferences prefs = getSharedPreferences(StudentDashboardActivity.PREFS_NAME, MODE_PRIVATE);
        boolean isRelaunch = prefs.getBoolean(StudentDashboardActivity.PREF_FOCUS_MODE_ACTIVE, false);
        long defaultDuration = 25 * 60 * 1000;

        if (isRelaunch) {
            Log.d(TAG, "Relaunching in active focus mode.");
            mFocusDurationMillis = prefs.getLong(StudentDashboardActivity.PREF_LAST_FOCUS_DURATION, defaultDuration);
            mTimeLeftInMillis = prefs.getLong(StudentDashboardActivity.PREF_REMAINING_FOCUS_TIME, mFocusDurationMillis);

        } else {
            long intentDuration = getIntent().getLongExtra("FOCUS_DURATION", 0);
            if (intentDuration > 0) {
                mFocusDurationMillis = intentDuration;
            } else {
                mFocusDurationMillis = prefs.getLong(StudentDashboardActivity.PREF_FOCUS_DURATION_MILLIS, defaultDuration);
            }
            mTimeLeftInMillis = mFocusDurationMillis;
        }


        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        mToolbarTitle = findViewById(R.id.toolbar_title);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        mTimerText = findViewById(R.id.textTimer);
        mQuoteText = findViewById(R.id.textQuote);
        mEmergencyExitButton = findViewById(R.id.buttonEmergencyExit);
        mPausePlayButton = findViewById(R.id.buttonPausePlay);
        mResetButton = findViewById(R.id.buttonReset);
        mButtonStartBreak = findViewById(R.id.buttonStartBreak);

        mPausePlayButton.setImageResource(R.drawable.ic_play);

        // --- FIX: Disable buttons until parent ID is loaded ---
        mPausePlayButton.setEnabled(false);
        mPausePlayButton.setAlpha(0.5f);
        mResetButton.setEnabled(false);
        mResetButton.setAlpha(0.5f);
        mEmergencyExitButton.setEnabled(false);
        mEmergencyExitButton.setAlpha(0.5f);
        mButtonStartBreak.setEnabled(false);
        mButtonStartBreak.setAlpha(0.5f);
        // --- END FIX ---

        mPausePlayButton.setOnClickListener(v -> {
            if (mTimerRunning) {
                pauseTimer();
            } else {
                startTimer();
            }
        });

        mResetButton.setOnClickListener(v -> resetTimer());
        mEmergencyExitButton.setOnClickListener(v -> showEmergencyExitDialog());
        mButtonStartBreak.setOnClickListener(v -> startBreak());

        updateTimerText();
        // fetchStudentData(); // --- MOVED: This is now called from onResume() ---

        OnBackPressedCallback onBackPressedCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (mKioskModeActive) {
                    showEmergencyExitDialog();
                } else {
                    setEnabled(false);
                    finish();
                }
            }
        };
        getOnBackPressedDispatcher().addCallback(this, onBackPressedCallback);

        if (isRelaunch) {
            Toast.makeText(this, "Focus session re-locked.", Toast.LENGTH_SHORT).show();
            startTimer();
        }
    }

    // --- THIS IS THE FIX ---
    @Override
    protected void onResume() {
        super.onResume();
        // Fetch the parent ID every time the activity is shown
        // This ensures mParentId is never stale
        fetchStudentData();
    }
    // --- END OF FIX ---

    private void fetchStudentData() {
        mStore.collection("users").document(mUserId).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                mUserFirstName = doc.getString("firstName");
                mParentId = doc.getString("linkedParentId");

                // Now that we have mParentId, enable the buttons
                enableButtons();
            } else {
                // Failsafe if doc doesn't exist
                enableButtons();
            }
        });
    }

    private void enableButtons() {
        mPausePlayButton.setEnabled(true);
        mPausePlayButton.setAlpha(1.0f);
        mResetButton.setEnabled(true);
        mResetButton.setAlpha(1.0f);

        if (mParentId == null || mParentId.isEmpty()) {
            mEmergencyExitButton.setVisibility(View.INVISIBLE);
            mButtonStartBreak.setVisibility(View.GONE);
        } else {
            mEmergencyExitButton.setEnabled(true);
            mEmergencyExitButton.setAlpha(1.0f);
            mEmergencyExitButton.setVisibility(View.VISIBLE);
            mButtonStartBreak.setEnabled(true);
            mButtonStartBreak.setAlpha(1.0f);
            // Break button visibility is handled in pauseTimer()
        }
    }

    private void startTimer() {
        if (!mKioskModeActive && !mIsInBreak && mParentId != null && !mParentId.isEmpty()) {
            try {
                startLockTask();

                SharedPreferences.Editor editor = getSharedPreferences(StudentDashboardActivity.PREFS_NAME, MODE_PRIVATE).edit();
                editor.putBoolean(StudentDashboardActivity.PREF_FOCUS_MODE_ACTIVE, true);
                editor.putLong(StudentDashboardActivity.PREF_LAST_FOCUS_DURATION, mFocusDurationMillis);
                editor.apply();

                mKioskModeActive = true;
            } catch (Exception e) {
                Log.e(TAG, "Failed to start lock task", e);
                Toast.makeText(this, "Could not start focus mode. Is Device Admin active?", Toast.LENGTH_LONG).show();
                return;
            }
        }

        mCountDownTimer = new CountDownTimer(mTimeLeftInMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                mTimeLeftInMillis = millisUntilFinished;
                updateTimerText();

                if (!mIsInBreak && mKioskModeActive) {
                    getSharedPreferences(StudentDashboardActivity.PREFS_NAME, MODE_PRIVATE).edit()
                            .putLong(StudentDashboardActivity.PREF_REMAINING_FOCUS_TIME, mTimeLeftInMillis)
                            .apply();
                }
            }

            @Override
            public void onFinish() {
                mTimerRunning = false;
                mIsPaused = false;
                mPausePlayButton.setImageResource(R.drawable.ic_play);

                if (mIsInBreak) {
                    mIsInBreak = false;
                    mToolbarTitle.setText("Focus Mode");

                    SharedPreferences prefs = getSharedPreferences(StudentDashboardActivity.PREFS_NAME, MODE_PRIVATE);
                    mTimeLeftInMillis = prefs.getLong(StudentDashboardActivity.PREF_REMAINING_FOCUS_TIME, mFocusDurationMillis);

                    Toast.makeText(FocusModeActivity.this, "Break is over! Resuming focus.", Toast.LENGTH_LONG).show();
                    updateTimerText();
                    startTimer();
                } else {
                    mKioskModeActive = false;
                    logPomodoro();
                    showFinishedDialog();
                }
            }
        }.start();

        mTimerRunning = true;
        mIsPaused = false;
        mPausePlayButton.setImageResource(R.drawable.ic_pause);
        mButtonStartBreak.setVisibility(View.GONE);
        mResetButton.setVisibility(View.VISIBLE);
    }

    private void pauseTimer() {
        if (mCountDownTimer != null) {
            mCountDownTimer.cancel();
        }
        mTimerRunning = false;
        mIsPaused = true;
        mPausePlayButton.setImageResource(R.drawable.ic_play);

        if (!mIsInBreak && mParentId != null && !mParentId.isEmpty()) {
            mButtonStartBreak.setVisibility(View.VISIBLE);
        }
    }

    private void resetTimer() {
        if (mCountDownTimer != null) {
            mCountDownTimer.cancel();
        }
        mTimerRunning = false;
        mIsPaused = false;

        if (mIsInBreak) {
            mIsInBreak = false;
            mTimeLeftInMillis = getSharedPreferences(StudentDashboardActivity.PREFS_NAME, MODE_PRIVATE)
                    .getLong(StudentDashboardActivity.PREF_REMAINING_FOCUS_TIME, mFocusDurationMillis);
        } else {
            mTimeLeftInMillis = mFocusDurationMillis;
        }

        mToolbarTitle.setText("Focus Mode");
        updateTimerText();
        mPausePlayButton.setImageResource(R.drawable.ic_play);
        mButtonStartBreak.setVisibility(View.GONE);

        if (mKioskModeActive) {
            getSharedPreferences(StudentDashboardActivity.PREFS_NAME, MODE_PRIVATE).edit()
                    .putLong(StudentDashboardActivity.PREF_REMAINING_FOCUS_TIME, mTimeLeftInMillis)
                    .apply();
        }
    }

    private void startBreak() {
        if (mCountDownTimer != null) {
            mCountDownTimer.cancel();
        }

        SharedPreferences prefs = getSharedPreferences(StudentDashboardActivity.PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putLong(StudentDashboardActivity.PREF_REMAINING_FOCUS_TIME, mTimeLeftInMillis).apply();

        mIsInBreak = true;
        mTimerRunning = false;
        mIsPaused = true;
        mTimeLeftInMillis = 5 * 60 * 1000; // 5 minutes

        mToolbarTitle.setText("Break Time");
        mPausePlayButton.setImageResource(R.drawable.ic_play);
        mButtonStartBreak.setVisibility(View.GONE);
        mResetButton.setVisibility(View.GONE);

        updateTimerText();

        try {
            stopLockTask();
            prefs.edit().putBoolean(StudentDashboardActivity.PREF_FOCUS_MODE_ACTIVE, false).apply();
            Toast.makeText(this, "Starting 5-min break. You can leave the app.", Toast.LENGTH_LONG).show();
            mKioskModeActive = false; // Allow user to exit
        } catch (Exception e) {
            Log.e(TAG, "Failed to stop lock task for break", e);
        }

        createNotification(mUserFirstName + " has started a 5-minute break.");
    }

    private void updateTimerText() {
        long minutes = TimeUnit.MILLISECONDS.toMinutes(mTimeLeftInMillis);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(mTimeLeftInMillis) - TimeUnit.MINUTES.toSeconds(minutes);
        mTimerText.setText(String.format(Locale.US, "%02d:%02d", minutes, seconds));
    }

    private void clearFocusFlagsAndStopLock() {
        if (mCountDownTimer != null) {
            mCountDownTimer.cancel();
        }
        mTimerRunning = false;
        mIsPaused = false;
        mKioskModeActive = false;

        try {
            stopLockTask();
        } catch (Exception e) {
            Log.e(TAG, "Failed to stop lock task", e);
        }

        SharedPreferences.Editor editor = getSharedPreferences(StudentDashboardActivity.PREFS_NAME, MODE_PRIVATE).edit();
        editor.putBoolean(StudentDashboardActivity.PREF_FOCUS_MODE_ACTIVE, false);
        editor.remove(StudentDashboardActivity.PREF_REMAINING_FOCUS_TIME);
        editor.remove(StudentDashboardActivity.PREF_LAST_FOCUS_DURATION);
        editor.apply();
    }

    private void showEmergencyExitDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AlertDialogTheme);
        View view = LayoutInflater.from(this).inflate(R.layout.activity_pin_check, null);

        final EditText input = view.findViewById(R.id.editTextPin);
        Button submitButton = view.findViewById(R.id.buttonPinSubmit);
        Button cancelButton = view.findViewById(R.id.buttonPinCancel);

        TextView title = view.findViewById(R.id.textTitle);
        TextView subtitle = view.findViewById(R.id.textSubtitle);
        title.setText("Emergency Exit");
        subtitle.setText("Please enter the 4-digit parent PIN to exit the focus session.");

        builder.setView(view);
        final AlertDialog dialog = builder.create();

        submitButton.setOnClickListener(v -> {
            String enteredPin = input.getText().toString();
            SharedPreferences prefs = getSharedPreferences(StudentDashboardActivity.PREFS_NAME, MODE_PRIVATE);
            String savedPin = prefs.getString(StudentDashboardActivity.PREF_SECURITY_PIN, null);

            if (savedPin != null && savedPin.equals(enteredPin)) {
                // PIN is correct!
                Toast.makeText(this, "PIN Correct. Exiting focus mode.", Toast.LENGTH_SHORT).show();
                clearFocusFlagsAndStopLock();
                createNotification(mUserFirstName + " cancelled a focus session with a PIN.");
                dialog.dismiss();
                finish();
            } else {
                input.setError("Incorrect PIN");
            }
        });

        cancelButton.setOnClickListener(v -> dialog.cancel());

        dialog.show();
    }

    private void showFinishedDialog() {
        if (!mIsInBreak) {
            clearFocusFlagsAndStopLock();
        }

        String title, message, positiveButton;
        if (mIsInBreak) {
            title = "Break Over!";
            message = "Time to get back to focus.";
            positiveButton = "OK";
        } else {
            title = "Session Finished!";
            message = "Great job! Would you like to start a 5-minute break?";
            positiveButton = "Start Break";
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AlertDialogTheme);
        builder.setTitle(title)
                .setMessage(message)
                .setPositiveButton(positiveButton, (dialog, which) -> {
                    if (mIsInBreak) {
                        // Break is over, do nothing, the onFinish() will handle re-locking
                    } else {
                        startBreakTimer(); // Session is over, start a break
                    }
                });

        if (!mIsInBreak) {
            builder.setNegativeButton("Done", (dialog, which) -> {
                finish(); // Go back to dashboard
            });
            createNotification(mUserFirstName + " finished a focus session!");
        }

        builder.setCancelable(false)
                .show();
    }

    private void startBreakTimer() {
        mIsInBreak = true;
        mTimeLeftInMillis = 5 * 60 * 1000; // 5 minutes
        mToolbarTitle.setText("Break Time");
        updateTimerText();
        startTimer();

        createNotification(mUserFirstName + " has started a 5-minute break.");
    }

    private void logPomodoro() {
        Map<String, Object> session = new HashMap<>();
        session.put("timestamp", System.currentTimeMillis());
        long durationMinutes = TimeUnit.MILLISECONDS.toMinutes(mFocusDurationMillis);
        session.put("durationMinutes", durationMinutes);
        session.put("taskName", "Focus Session");

        mStore.collection("users").document(mUserId).collection("sessions")
                .add(session)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "Session logged successfully");
                    updateUserAggregates(durationMinutes);
                })
                .addOnFailureListener(e -> Log.w(TAG, "Error logging session", e));
    }

    private void updateUserAggregates(long durationMinutes) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("totalPomodoros", FieldValue.increment(1));
        updates.put("totalHours", FieldValue.increment(durationMinutes / 60.0));

        mStore.collection("users").document(mUserId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "User aggregates updated successfully");
                    Toast.makeText(this, "Session saved!", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Error updating user aggregates", e);
                });
    }

    // --- THIS IS THE FIX ---
    private void createNotification(String message) {
        if (mParentId == null || mParentId.isEmpty()) {
            Log.d(TAG, "No parent ID found, cannot send notification.");
            return;
        }

        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("parentId", mParentId);
        notificationData.put("studentId", mUserId);
        notificationData.put("studentName", mUserFirstName);
        notificationData.put("message", message);
        notificationData.put("timestamp", FieldValue.serverTimestamp());

        // --- ADDED: This field is required by the parent's query ---
        notificationData.put("read", false);
        // --- END OF FIX ---

        mStore.collection("notifications")
                .add(notificationData)
                .addOnSuccessListener(documentReference -> Log.d(TAG, "Notification request created successfully."))
                .addOnFailureListener(e -> Log.w(TAG, "Error creating notification request", e));
    }
    // --- END OF FIX ---

    private void goToLogin() {
        Intent intent = new Intent(FocusModeActivity.this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}