package com.example.focus;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;

public class WelcomeActivity extends AppCompatActivity {

    private static final String TAG = "WelcomeActivity";

    private FirebaseAuth mAuth;
    private FirebaseFirestore mStore;

    // UI elements
    private ProgressBar mLoadingProgressBar;
    private ScrollView mContentScrollView;
    private Button mGetStartedButton, mCreateAccountButton;

    // Device Admin
    private DevicePolicyManager mDevicePolicyManager;
    private ComponentName mDeviceAdminComponent;
    private static final int DEVICE_ADMIN_REQUEST_CODE = 101;
    private boolean isWaitingForAccessibility = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(R.style.Theme_Guard);
        setContentView(R.layout.activity_welcome);

        // Init UI
        mLoadingProgressBar = findViewById(R.id.loadingProgressBar);
        mContentScrollView = findViewById(R.id.contentScrollView);
        mGetStartedButton = findViewById(R.id.buttonGetStarted);
        mCreateAccountButton = findViewById(R.id.buttonCreateAccount);

        // Init Device Admin components
        mDevicePolicyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        mDeviceAdminComponent = new ComponentName(this, DeviceAdminReceiver.class);

        showLoading(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume called.");

        // --- ACCESSIBILITY FIX: Check if we are returning from Settings ---
        if (isWaitingForAccessibility) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                isWaitingForAccessibility = false;
                if (isAccessibilityServiceEnabled()) {
                    Log.d(TAG, "User returned from settings and service is now ENABLED.");
                    Toast.makeText(this, "Accessibility Permission Enabled!", Toast.LENGTH_SHORT).show();
                    checkFlow();
                } else {
                    Log.w(TAG, "User returned from settings but service is still DISABLED.");
                    checkFlow();
                }
            }, 1000);
        } else {
            checkFlow();
        }
    }

    // This is the main check function
    private void checkFlow() {
        // 1. Check Internet
        if (!isNetworkAvailable()) {
            Log.d(TAG, "No internet connection. Redirecting to NoInternetActivity.");
            Intent intent = new Intent(this, NoInternetActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        // 2. Check for Device Admin (MANDATORY FOR ALL USERS)
        if (!mDevicePolicyManager.isAdminActive(mDeviceAdminComponent)) {
            Log.d(TAG, "Device Admin is required but not active. Showing dialog.");
            showDeviceAdminDialog();
            return; // Stop here, user must fix this first.
        }

        // 3. Check for Accessibility (MANDATORY FOR ALL USERS)
        if (!isAccessibilityServiceEnabled()) {
            Log.d(TAG, "Accessibility is required but not active. Showing dialog.");
            showAccessibilityDialog();
            return; // Stop here, user must fix this second.
        }

        // 4. All permissions are active. Now check Firebase Auth.
        Log.d(TAG, "All core permissions are active. Proceeding to auth check.");
        mAuth = FirebaseAuth.getInstance();
        mStore = FirebaseFirestore.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null || !currentUser.isEmailVerified()) {
            if (currentUser != null) {
                Log.w(TAG, "User logged in but email not verified. Signing out.");
                mAuth.signOut();
            }
            Log.d(TAG, "No user logged in. Showing welcome screen.");
            showWelcomeScreen();
        } else {
            Log.d(TAG, "User already logged in and verified. Checking role and permissions.");
            checkUserRoleAndPermissions(currentUser);
        }
    }

    private void showLoading(boolean isLoading) {
        if (isLoading) {
            mLoadingProgressBar.setVisibility(View.VISIBLE);
            mContentScrollView.setVisibility(View.GONE);
            mGetStartedButton.setVisibility(View.GONE);
            mCreateAccountButton.setVisibility(View.GONE);
        } else {
            mLoadingProgressBar.setVisibility(View.GONE);
            mContentScrollView.setVisibility(View.VISIBLE);
            mGetStartedButton.setVisibility(View.VISIBLE);
            mCreateAccountButton.setVisibility(View.VISIBLE);
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        Network activeNetwork = cm.getActiveNetwork();
        if (activeNetwork == null) return false;

        NetworkCapabilities caps = cm.getNetworkCapabilities(activeNetwork);
        if (caps == null) return false;

        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
    }

    // --- UPDATED: This method now checks for the Parent PIN ---
    private void checkUserRoleAndPermissions(FirebaseUser user) {
        String userId = user.getUid();
        mStore.collection("users").document(userId).get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        DocumentSnapshot document = task.getResult();
                        if (document != null && document.exists()) {
                            String role = document.getString("role");
                            Log.d(TAG, "User role is: " + role);

                            if ("Parent".equals(role)) {
                                // --- THIS IS THE FIX ---
                                // Check if the parent has created a PIN
                                String pin = document.getString("securityPin");
                                if (pin == null || pin.isEmpty()) {
                                    // Parent has no PIN. Force them to create one.
                                    Log.d(TAG, "Parent has no PIN. Redirecting to ParentalControlsActivity.");
                                    Intent intent = new Intent(WelcomeActivity.this, ParentalControlsActivity.class);
                                    // Send a flag to tell the next activity it MUST create a PIN
                                    intent.putExtra("FORCE_PIN_CREATION", true);
                                    startActivity(intent);
                                    finish();
                                } else {
                                    // Parent has a PIN. Proceed to dashboard.
                                    goToActivity(ParentDashboardActivity.class);
                                }
                                // --- END OF FIX ---
                            } else if ("Student".equals(role)) {
                                // Student permissions were already checked in checkFlow()
                                goToActivity(StudentDashboardActivity.class);
                            } else {
                                Log.w(TAG, "User has invalid role: " + role);
                                mAuth.signOut();
                                showWelcomeScreen();
                            }
                        } else {
                            Log.w(TAG, "User logged in but no user document found.");
                            mAuth.signOut();
                            showWelcomeScreen();
                        }
                    } else {
                        Log.w(TAG, "Failed to get user document.", task.getException());
                        mAuth.signOut();
                        showWelcomeScreen();
                    }
                });
    }

    private void showWelcomeScreen() {
        // Stop loading, show buttons
        showLoading(false);

        mGetStartedButton.setOnClickListener(v -> {
            Intent intent = new Intent(WelcomeActivity.this, LoginActivity.class);
            startActivity(intent);
        });
        mCreateAccountButton.setOnClickListener(v -> {
            Intent intent = new Intent(WelcomeActivity.this, RegisterActivity.class);
            startActivity(intent);
        });
    }

    private void goToActivity(Class<?> activityClass) {
        Intent intent = new Intent(WelcomeActivity.this, activityClass);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showDeviceAdminDialog() {
        new AlertDialog.Builder(this, R.style.AlertDialogTheme)
                .setTitle("Protection Required")
                .setMessage("To use FocusGuard, you must activate device admin. This allows the app's Kiosk Mode and Uninstall Protection to function.")
                .setPositiveButton("Activate", (dialog, which) -> {
                    Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                    intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, mDeviceAdminComponent);
                    intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "This permission is required to prevent unauthorized uninstallation and enable focus mode.");
                    startActivityForResult(intent, DEVICE_ADMIN_REQUEST_CODE);
                })
                .setNegativeButton("Exit App", (dialog, which) -> {
                    Toast.makeText(this, "This permission is required to use the app.", Toast.LENGTH_LONG).show();
                    finishAffinity(); // Closes the entire app
                })
                .setCancelable(false)
                .show();
    }

    private void showAccessibilityDialog() {
        isWaitingForAccessibility = true;

        getSharedPreferences(StudentDashboardActivity.PREFS_NAME, MODE_PRIVATE).edit()
                .putBoolean(StudentDashboardActivity.PREF_AWAITING_ACCESSIBILITY, true)
                .apply();

        new AlertDialog.Builder(this, R.style.AlertDialogTheme)
                .setTitle("Protection Required")
                .setMessage("To use FocusGuard, you must enable the Accessibility Service. This is required to block access to system settings and other apps.")
                .setPositiveButton("Activate", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    Toast.makeText(this, "Find 'FocusGuard' in the list and enable it.", Toast.LENGTH_LONG).show();
                    startActivity(intent);
                })
                .setNegativeButton("Exit App", (dialog, which) -> {
                    isWaitingForAccessibility = false;
                    Toast.makeText(this, "This permission is required to use the app.", Toast.LENGTH_LONG).show();
                    finishAffinity(); // Closes the entire app
                })
                .setCancelable(false)
                .show();
    }

    private boolean isAccessibilityServiceEnabled() {
        AccessibilityManager am = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am == null) return false;

        List<AccessibilityServiceInfo> enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC);
        if (enabledServices == null) return false;

        final String expectedClassName = AppLockService.class.getSimpleName();
        Log.d(TAG, "Looking for service containing class name: " + expectedClassName);

        for (AccessibilityServiceInfo service : enabledServices) {
            Log.d(TAG, "Found running service: " + service.getId());
            if (service.getId().contains(expectedClassName)) {
                Log.d(TAG, "Accessibility Service is ENABLED.");
                return true;
            }
        }

        Log.d(TAG, "Accessibility Service is DISABLED.");
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == DEVICE_ADMIN_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "Device Admin enabled!", Toast.LENGTH_SHORT).show();
                mDevicePolicyManager.setLockTaskPackages(mDeviceAdminComponent, new String[]{getPackageName()});
                // Re-run the check flow
                checkFlow();
            } else {
                Toast.makeText(this, "Failed to enable Device Admin. This is required.", Toast.LENGTH_LONG).show();
                // The dialog will re-appear on its own when checkFlow() runs again in onResume()
            }
        }
    }
}