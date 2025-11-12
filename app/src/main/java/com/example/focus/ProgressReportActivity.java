package com.example.focus;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class ProgressReportActivity extends AppCompatActivity {

    private static final String TAG = "ProgressReport";

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore mStore;
    private String mUserId;

    // UI Elements
    private TextView mTotalSessions, mTotalHours, mAvgDaily, mDayStreak, mSummaryText;
    private Button mWeekToggle, mMonthToggle, mAllToggle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_progress_report);

        // --- Initialize Firebase ---
        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        mStore = FirebaseFirestore.getInstance();
        if (currentUser != null) {
            mUserId = currentUser.getUid();
        } else {
            goToLogin();
            return;
        }

        // --- Toolbar Setup ---
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        // --- UI Element Initialization ---
        mTotalSessions = findViewById(R.id.textTotalSessions);
        mTotalHours = findViewById(R.id.textTotalHours);
        mAvgDaily = findViewById(R.id.textAvgDaily);
        mDayStreak = findViewById(R.id.textDayStreak);
        mSummaryText = findViewById(R.id.textSummary);

        // --- FIX: Use correct IDs from activity_progress_report.xml ---
        mWeekToggle = findViewById(R.id.buttonWeek);
        mMonthToggle = findViewById(R.id.buttonMonth);
        mAllToggle = findViewById(R.id.buttonAll);

        // Set default selection
        selectToggle(mWeekToggle);
        fetchDataForRange(TimeRange.WEEK);

        // --- Toggle Listeners ---
        mWeekToggle.setOnClickListener(v -> {
            selectToggle(mWeekToggle);
            fetchDataForRange(TimeRange.WEEK);
        });

        mMonthToggle.setOnClickListener(v -> {
            selectToggle(mMonthToggle);
            fetchDataForRange(TimeRange.MONTH);
        });

        mAllToggle.setOnClickListener(v -> {
            selectToggle(mAllToggle);
            fetchDataForRange(TimeRange.ALL);
        });
    }

    private void selectToggle(Button selectedButton) {
        Button[] toggles = {mWeekToggle, mMonthToggle, mAllToggle};
        for (Button button : toggles) {
            if (button == selectedButton) {
                button.setBackground(ContextCompat.getDrawable(this, R.drawable.toggle_button_selected));
                button.setTextColor(ContextCompat.getColor(this, R.color.dark_text));
            } else {
                button.setBackground(ContextCompat.getDrawable(this, R.drawable.toggle_button_unselected));
                button.setTextColor(ContextCompat.getColor(this, R.color.white));
            }
        }
    }

    private enum TimeRange {
        WEEK,
        MONTH,
        ALL
    }

    // Helper to get start of a given day (sets H:M:S:MS to 0)
    private Calendar getStartOfDay(Calendar cal) {
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal;
    }

    private void fetchDataForRange(TimeRange range) {
        Calendar today = Calendar.getInstance();
        Long startTimestamp = null;

        switch (range) {
            case WEEK:
                Calendar weekStart = Calendar.getInstance();
                weekStart.setFirstDayOfWeek(Calendar.MONDAY); // Set Monday as the first day of the week
                weekStart.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
                startTimestamp = getStartOfDay(weekStart).getTimeInMillis();
                break;
            case MONTH:
                Calendar monthStart = Calendar.getInstance();
                monthStart.set(Calendar.DAY_OF_MONTH, 1);
                startTimestamp = getStartOfDay(monthStart).getTimeInMillis();
                break;
            case ALL:
            default:
                startTimestamp = null; // Will fetch all
                break;
        }

        Query query = mStore.collection("users").document(mUserId).collection("sessions")
                .orderBy("timestamp", Query.Direction.DESCENDING);

        if (startTimestamp != null) {
            query = query.whereGreaterThanOrEqualTo("timestamp", startTimestamp);
        }

        query.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                processSessionData(task.getResult(), range);
            } else {
                Log.w(TAG, "Error getting documents.", task.getException());
                Toast.makeText(ProgressReportActivity.this, "Failed to load data.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void processSessionData(QuerySnapshot sessions, TimeRange range) {
        List<DocumentSnapshot> sessionList = sessions.getDocuments();

        int totalSessions = sessionList.size();
        double totalMinutes = 0;
        // Using Integer for Day of Week (e.g., Calendar.MONDAY)
        Map<Integer, Double> dailyTotals = new HashMap<>();
        // Using Long for unique day (milliseconds at start of day)
        Set<Long> uniqueDays = new HashSet<>();

        for (DocumentSnapshot session : sessionList) {
            Long timestamp = session.getLong("timestamp");
            Long durationMinutes = session.getLong("durationMinutes");

            if (timestamp == null || durationMinutes == null) continue;

            totalMinutes += durationMinutes;

            // Use Calendar to get date info
            Date date = new Date(timestamp);
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);

            // Get unique day by creating a Calendar instance, setting its time, and zeroing out time fields
            Calendar dayCal = Calendar.getInstance();
            dayCal.setTime(date);
            long uniqueDayMillis = getStartOfDay(dayCal).getTimeInMillis();
            uniqueDays.add(uniqueDayMillis);


            // Get day of week (e.g., Calendar.MONDAY, Calendar.TUESDAY)
            int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);

            // --- FIX FOR API 23 (Replaces getOrDefault) ---
            Double currentTotal = dailyTotals.get(dayOfWeek);
            if (currentTotal == null) {
                currentTotal = 0.0;
            }
            dailyTotals.put(dayOfWeek, currentTotal + (durationMinutes / 60.0));
            // --- END FIX ---
        }

        // Calculate Stats
        double totalHours = totalMinutes / 60.0;
        int numberOfDaysWithSessions = uniqueDays.size();
        double avgDaily = (numberOfDaysWithSessions == 0) ? 0.0 : (totalHours / numberOfDaysWithSessions);

        // For simplicity, we'll calculate streak based on *all* data, not just range
        int dayStreak = calculateDayStreak(uniqueDays);

        // Update UI
        mTotalSessions.setText(String.format(Locale.US, "%d", totalSessions));
        mTotalHours.setText(String.format(Locale.US, "%.1fh", totalHours));
        mAvgDaily.setText(String.format(Locale.US, "%.1fh", avgDaily));
        mDayStreak.setText(String.format(Locale.US, "%d", dayStreak));

        String rangeText = "total";
        if (range == TimeRange.WEEK) rangeText = "this week";
        if (range == TimeRange.MONTH) rangeText = "this month";

        mSummaryText.setText(String.format(Locale.US, "Great progress! You've completed %d Pomodoros %s.", totalSessions, rangeText));
    }

    private int calculateDayStreak(Set<Long> uniqueDays) {
        if (uniqueDays.isEmpty()) return 0;

        int currentStreak = 0;
        Calendar today = getStartOfDay(Calendar.getInstance());
        long todayMillis = today.getTimeInMillis();

        // Check if today has a session
        if (uniqueDays.contains(todayMillis)) {
            currentStreak = 1;
            Calendar yesterday = getStartOfDay(Calendar.getInstance());
            yesterday.add(Calendar.DAY_OF_YEAR, -1);

            while (uniqueDays.contains(yesterday.getTimeInMillis())) {
                currentStreak++;
                yesterday.add(Calendar.DAY_OF_YEAR, -1);
            }
        }
        // Check if yesterday has a session (if today doesn't)
        else {
            Calendar yesterday = getStartOfDay(Calendar.getInstance());
            yesterday.add(Calendar.DAY_OF_YEAR, -1);
            long yesterdayMillis = yesterday.getTimeInMillis();

            if (uniqueDays.contains(yesterdayMillis)) {
                currentStreak = 1;
                Calendar dayBefore = getStartOfDay(Calendar.getInstance());
                dayBefore.add(Calendar.DAY_OF_YEAR, -2);

                while (uniqueDays.contains(dayBefore.getTimeInMillis())) {
                    currentStreak++;
                    dayBefore.add(Calendar.DAY_OF_YEAR, -1);
                }
            }
        }
        return currentStreak;
    }

    private void goToLogin() {
        Intent intent = new Intent(ProgressReportActivity.this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}

