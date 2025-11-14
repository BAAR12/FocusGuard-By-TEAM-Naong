package com.example.focus;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.activity.OnBackPressedCallback;

import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class ParentDashboardActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = "ParentDashboard";

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore mStore;
    private FirebaseUser mCurrentUser;
    private String mParentId;

    // UI
    private Toolbar mToolbar;
    private DrawerLayout mDrawerLayout;
    private NavigationView mNavigationView;
    private Spinner mSpinnerSelectChild;
    private Button mButtonParentalSettings, mButtonLinkChild;
    private LinearLayout mCardChildStats, mCardRecentActivity;
    private RecyclerView mRecyclerRecentActivity;

    // Child Stats UI
    private TextView mTextChildName, mTextChildGrade;
    private TextView mTextStatSessions, mTextStatTotalFocus, mTextStatStreak, mTextStatPerformance, mTextStatWeeklyStudy, mTextStatAvgDaily;

    private TextView mNotificationBadge;

    // Data
    private List<DocumentSnapshot> mLinkedChildren = new ArrayList<>();
    private ArrayAdapter<String> mChildSpinnerAdapter;
    private List<String> mChildNames = new ArrayList<>();
    private Map<String, String> mChildNameMap = new HashMap<>(); // Map<Name, UserID>
    private String mSelectedChildId = null;

    private RecentActivityAdapter mRecentActivityAdapter;
    private List<RecentActivityAdapter.Session> mSessionList = new ArrayList<>();

    // --- UPDATED: Listeners for real-time updates ---
    private ListenerRegistration mUserDocListener;
    private ListenerRegistration mSessionListener;
    private ListenerRegistration mNotificationListener; // <-- This is now real-time

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parent_dashboard);

        // --- Initialize Firebase ---
        mAuth = FirebaseAuth.getInstance();
        mStore = FirebaseFirestore.getInstance();
        mCurrentUser = mAuth.getCurrentUser();
        if (mCurrentUser == null) {
            goToLogin();
            return;
        }
        mParentId = mCurrentUser.getUid();

        // --- UI Initialization ---
        mSpinnerSelectChild = findViewById(R.id.spinnerSelectChild);
        mButtonParentalSettings = findViewById(R.id.buttonParentalSettings);
        mButtonLinkChild = findViewById(R.id.buttonLinkChild);
        mCardChildStats = findViewById(R.id.cardChildStats);
        mCardRecentActivity = findViewById(R.id.cardRecentActivity);
        mRecyclerRecentActivity = findViewById(R.id.recyclerRecentActivity);

        // Child Stats UI
        mTextChildName = findViewById(R.id.textChildName);
        mTextChildGrade = findViewById(R.id.textChildGrade);
        mTextStatSessions = findViewById(R.id.textStatSessions);
        mTextStatTotalFocus = findViewById(R.id.textStatTotalFocus);
        mTextStatStreak = findViewById(R.id.textStatStreak);
        mTextStatPerformance = findViewById(R.id.textStatPerformance);
        mTextStatWeeklyStudy = findViewById(R.id.textStatWeeklyStudy);
        mTextStatAvgDaily = findViewById(R.id.textStatAvgDaily);

        // --- Setup Navigation Drawer ---
        setupNavigationDrawer();

        // --- Setup Child Selector Spinner ---
        mChildNames.add("Select a child...");

        mChildSpinnerAdapter = new ArrayAdapter<>(this, R.layout.spinner_item_light_text, mChildNames);
        mChildSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinnerSelectChild.setAdapter(mChildSpinnerAdapter);
        mSpinnerSelectChild.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) {
                    String selectedName = mChildNames.get(position);
                    mSelectedChildId = mChildNameMap.get(selectedName);
                    if (mSelectedChildId != null) {
                        loadChildStats(mSelectedChildId);
                        mButtonParentalSettings.setEnabled(true);
                    }
                } else {
                    mSelectedChildId = null;
                    mCardChildStats.setVisibility(View.GONE);
                    mCardRecentActivity.setVisibility(View.GONE);
                    mButtonParentalSettings.setEnabled(false);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                mSelectedChildId = null;
                mButtonParentalSettings.setEnabled(false);
            }
        });

        // --- Setup RecyclerView ---
        mRecyclerRecentActivity.setLayoutManager(new LinearLayoutManager(this));
        mRecentActivityAdapter = new RecentActivityAdapter(this, mSessionList);
        mRecyclerRecentActivity.setAdapter(mRecentActivityAdapter);

        // --- Button Listeners ---
        mButtonParentalSettings.setEnabled(false);
        mButtonParentalSettings.setOnClickListener(v -> {
            if (mSelectedChildId != null) {
                Intent intent = new Intent(ParentDashboardActivity.this, ParentalControlsActivity.class);
                intent.putExtra("SELECTED_CHILD_ID", mSelectedChildId);
                startActivity(intent);
            } else {
                Toast.makeText(this, "Please select a child first.", Toast.LENGTH_SHORT).show();
            }
        });

        mButtonLinkChild.setOnClickListener(v -> {
            startActivity(new Intent(ParentDashboardActivity.this, LinkAccountActivity.class));
        });

        loadParentDataForNavHeader();
        getAndSaveFCMToken();

        // --- CHANGED: Now called in onResume to ensure it's always active ---
        // listenForNewNotifications();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.dashboard_menu, menu);

        final MenuItem menuItem = menu.findItem(R.id.action_notifications);
        View actionView = menuItem.getActionView();
        mNotificationBadge = actionView.findViewById(R.id.notification_badge);

        actionView.setOnClickListener(v -> onOptionsItemSelected(menuItem));

        // --- MOVED: Start listening *after* the badge is inflated ---
        listenForNewNotifications();

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_notifications) {
            Log.d(TAG, "Notification icon clicked.");
            Intent intent = new Intent(this, NotificationActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        fetchLinkedChildren();

        if (mSelectedChildId != null) {
            loadChildStats(mSelectedChildId);
        }

        // --- ADDED: Re-attach notification listener ---
        if (mNotificationListener == null) {
            listenForNewNotifications();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Detach listeners when app is paused
        if (mSessionListener != null) {
            mSessionListener.remove();
            mSessionListener = null;
        }
        if (mUserDocListener != null) {
            mUserDocListener.remove();
            mUserDocListener = null;
        }
        if (mNotificationListener != null) {
            mNotificationListener.remove();
            mNotificationListener = null;
        }
    }

    private void fetchLinkedChildren() {
        // Clear current list
        mChildNames.clear();
        mChildNameMap.clear();
        mLinkedChildren.clear();
        mChildNames.add("Select a child...");

        mStore.collection("users")
                .whereEqualTo("linkedParentId", mParentId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        Log.d(TAG, "No linked children found.");
                        mChildSpinnerAdapter.notifyDataSetChanged();
                        return;
                    }

                    for (DocumentSnapshot childDoc : querySnapshot.getDocuments()) {
                        String childName = childDoc.getString("firstName") + " " + childDoc.getString("lastName");
                        String childId = childDoc.getId();

                        mChildNames.add(childName);
                        mChildNameMap.put(childName, childId);
                        mLinkedChildren.add(childDoc);
                    }
                    mChildSpinnerAdapter.notifyDataSetChanged();

                    if(mSelectedChildId != null) {
                        for(int i=0; i < mChildNames.size(); i++) {
                            String name = mChildNames.get(i);
                            if(name != null && mSelectedChildId.equals(mChildNameMap.get(name))) {
                                mSpinnerSelectChild.setSelection(i);
                                break;
                            }
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Error getting linked children", e);
                    Toast.makeText(this, "Failed to load children.", Toast.LENGTH_SHORT).show();
                });
    }

    private void loadChildStats(String childId) {
        // Detach previous listeners
        if (mSessionListener != null) mSessionListener.remove();
        if (mUserDocListener != null) mUserDocListener.remove();

        // 1. Load data from the Child's main document (Real-time)
        mUserDocListener = mStore.collection("users").document(childId)
                .addSnapshotListener((childDoc, error) -> {
                    if (error != null) {
                        Log.w(TAG, "Listen failed for childDoc.", error);
                        return;
                    }
                    if (childDoc == null || !childDoc.exists()) {
                        Toast.makeText(this, "Could not find child data.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String name = childDoc.getString("firstName") + " " + childDoc.getString("lastName");
                    String grade = "Grade 12"; // Placeholder
                    String age = "Age 16"; // Placeholder
                    mTextChildName.setText(name);
                    mTextChildGrade.setText(grade + " | " + age);

                    Long totalPomos = childDoc.getLong("totalPomodoros");
                    Double totalHours = childDoc.getDouble("totalHours");

                    mTextStatSessions.setText(String.format(Locale.US, "%d", (totalPomos != null ? totalPomos : 0)));
                    mTextStatTotalFocus.setText(String.format(Locale.US, "%.1fh", (totalHours != null ? totalHours : 0.0)));

                    mCardChildStats.setVisibility(View.VISIBLE);
                    loadChildSessions(childId); // Re-attach session listener
                });
    }

    private void loadChildSessions(String childId) {
        // Detach old session listener if it exists
        if (mSessionListener != null) mSessionListener.remove();

        Calendar cal = Calendar.getInstance();
        cal.setFirstDayOfWeek(Calendar.MONDAY);
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        getStartOfDay(cal);
        long startOfWeekMillis = cal.getTimeInMillis();

        Query query = mStore.collection("users").document(childId).collection("sessions")
                .orderBy("timestamp", Query.Direction.DESCENDING);

        mSessionListener = query.addSnapshotListener((querySnapshot, error) -> {
            if (error != null) {
                Log.w(TAG, "Listen failed for sessions.", error);
                return;
            }

            if (querySnapshot == null || querySnapshot.isEmpty()) {
                mSessionList.clear();
                mRecentActivityAdapter.notifyDataSetChanged();
                mCardRecentActivity.setVisibility(View.GONE);
                return;
            }

            mSessionList.clear();
            double weeklyMinutes = 0;
            HashSet<Long> uniqueDays = new HashSet<>();

            for (DocumentSnapshot sessionDoc : querySnapshot.getDocuments()) {
                Long timestamp = sessionDoc.getLong("timestamp");
                Long duration = sessionDoc.getLong("durationMinutes");

                if(timestamp == null || duration == null) continue;

                if (mSessionList.size() < 5) {
                    mSessionList.add(new RecentActivityAdapter.Session(timestamp, duration));
                }
                if (timestamp >= startOfWeekMillis) {
                    weeklyMinutes += duration;
                }
                Calendar sessionCal = Calendar.getInstance();
                sessionCal.setTimeInMillis(timestamp);
                uniqueDays.add(getStartOfDay(sessionCal).getTimeInMillis());
            }

            double weeklyHours = weeklyMinutes / 60.0;
            mTextStatWeeklyStudy.setText(String.format(Locale.US, "%.1fh (This week)", weeklyHours));

            int dayStreak = calculateDayStreak(uniqueDays);
            mTextStatStreak.setText(String.format(Locale.US, "%d Days\nCurrent Streak", dayStreak));

            mTextStatAvgDaily.setText("2.5h\nAvg. Daily focus");
            mTextStatPerformance.setText("A+\nPerformance");

            mRecentActivityAdapter.notifyDataSetChanged();
            mCardRecentActivity.setVisibility(View.VISIBLE);
        });
    }

    // --- THIS IS THE FIX ---
    // This method now uses a real-time listener
    private void listenForNewNotifications() {
        if (mNotificationListener != null) {
            mNotificationListener.remove();
        }

        mNotificationListener = mStore.collection("notifications")
                .whereEqualTo("parentId", mParentId)
                .whereEqualTo("read", false) // Only count unread
                .addSnapshotListener((querySnapshot, error) -> {
                    if (error != null) {
                        Log.w(TAG, "Error listening for new notifications.", error);
                        return;
                    }

                    if (mNotificationBadge == null) {
                        Log.d(TAG, "Badge is null, can't update. View not inflated yet.");
                        return; // Badge hasn't been created yet
                    }

                    if (querySnapshot != null && !querySnapshot.isEmpty()) {
                        Log.d(TAG, "You have " + querySnapshot.size() + " unread notifications.");
                        mNotificationBadge.setVisibility(View.VISIBLE);
                    } else {
                        Log.d(TAG, "No unread notifications.");
                        mNotificationBadge.setVisibility(View.GONE);
                    }
                });
    }
    // --- END OF FIX ---

    // --- Helper Functions ---

    private Calendar getStartOfDay(Calendar cal) {
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal;
    }

    private int calculateDayStreak(HashSet<Long> uniqueDays) {
        if (uniqueDays.isEmpty()) return 0;
        int currentStreak = 0;
        Calendar today = getStartOfDay(Calendar.getInstance());
        long todayMillis = today.getTimeInMillis();
        if (uniqueDays.contains(todayMillis)) {
            currentStreak = 1;
            Calendar yesterday = getStartOfDay(Calendar.getInstance());
            yesterday.add(Calendar.DAY_OF_YEAR, -1);
            while (uniqueDays.contains(yesterday.getTimeInMillis())) {
                currentStreak++;
                yesterday.add(Calendar.DAY_OF_YEAR, -1);
            }
        } else {
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

        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
                    mDrawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    setEnabled(false);
                    ParentDashboardActivity.super.onBackPressed();
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

    private void loadParentDataForNavHeader() {
        mStore.collection("users").document(mParentId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String firstName = documentSnapshot.getString("firstName");
                        String email = documentSnapshot.getString("email");
                        updateNavHeader(firstName, email);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Failed to load parent data for nav header", e);
                    updateNavHeader("Parent User", mCurrentUser.getEmail());
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
                    mStore.collection("users").document(mParentId)
                            .update("fcmToken", token)
                            .addOnSuccessListener(aVoid -> Log.d(TAG, "FCM Token saved to Firestore"))
                            .addOnFailureListener(e -> Log.w(TAG, "Error saving FCM Token", e));
                });
    }

    private void goToLogin() {
        Intent intent = new Intent(ParentDashboardActivity.this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}