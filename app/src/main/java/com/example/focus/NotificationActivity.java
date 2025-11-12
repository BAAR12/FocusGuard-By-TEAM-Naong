package com.example.focus;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.List;

public class NotificationActivity extends AppCompatActivity {

    private static final String TAG = "NotificationActivity";

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore mStore;
    private String mParentId;

    // UI
    private RecyclerView mRecyclerNotifications;
    private TextView mTextNoNotifications;
    private NotificationAdapter mAdapter;
    private List<Notification> mNotificationList;

    private ListenerRegistration mNotificationListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification);

        // --- Initialize Firebase ---
        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        mStore = FirebaseFirestore.getInstance();
        if (currentUser == null) {
            finish();
            return;
        }
        mParentId = currentUser.getUid();

        // --- Toolbar Setup ---
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        // --- UI Initialization ---
        mRecyclerNotifications = findViewById(R.id.recyclerNotifications);
        mTextNoNotifications = findViewById(R.id.textNoNotifications);

        // --- Setup RecyclerView ---
        mNotificationList = new ArrayList<>();
        mAdapter = new NotificationAdapter(this, mNotificationList);
        mRecyclerNotifications.setLayoutManager(new LinearLayoutManager(this));
        mRecyclerNotifications.setAdapter(mAdapter);

        // Load notifications
        loadNotifications();

        // Mark all as read when the user opens the screen
        markNotificationsAsRead();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Remove the real-time listener when the activity is destroyed
        if (mNotificationListener != null) {
            mNotificationListener.remove();
        }
    }

    private void loadNotifications() {
        mNotificationListener = mStore.collection("notifications")
                .whereEqualTo("parentId", mParentId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(50) // Get the latest 50
                .addSnapshotListener((querySnapshot, error) -> {
                    if (error != null) {
                        Log.w(TAG, "Error listening for notifications.", error);
                        return;
                    }

                    if (querySnapshot == null || querySnapshot.isEmpty()) {
                        mTextNoNotifications.setVisibility(View.VISIBLE);
                        mRecyclerNotifications.setVisibility(View.GONE);
                    } else {
                        mTextNoNotifications.setVisibility(View.GONE);
                        mRecyclerNotifications.setVisibility(View.VISIBLE);

                        mNotificationList.clear();
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            Notification notification = doc.toObject(Notification.class);
                            mNotificationList.add(notification);
                        }
                        mAdapter.notifyDataSetChanged();
                    }
                });
    }

    // This method finds all unread notifications for this parent and marks them as read
    private void markNotificationsAsRead() {
        mStore.collection("notifications")
                .whereEqualTo("parentId", mParentId)
                .whereEqualTo("read", false) // Find only unread ones
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        Log.d(TAG, "No unread notifications to mark as read.");
                        return;
                    }

                    WriteBatch batch = mStore.batch();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        batch.update(doc.getReference(), "read", true);
                    }

                    batch.commit()
                            .addOnSuccessListener(aVoid -> Log.d(TAG, "Successfully marked " + querySnapshot.size() + " notifications as read."))
                            .addOnFailureListener(e -> Log.w(TAG, "Failed to mark notifications as read", e));
                })
                .addOnFailureListener(e -> Log.w(TAG, "Failed to query for unread notifications", e));
    }
}