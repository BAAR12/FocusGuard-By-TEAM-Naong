package com.example.focus;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import java.util.concurrent.TimeUnit;

public class AppBlockActivity extends AppCompatActivity {

    private static final String TAG = "AppBlockActivity";

    // --- FIX: ADDED MISSING CONSTANTS (Copied from StudentDashboardActivity) ---
    // These constants must be defined here for the service to compile and work reliably.
    public static final String PREFS_NAME = "FocusGuardPrefs";
    public static final String PREF_SECURITY_PIN = "securityPin";
    // --- END FIX ---

    // UI
    private ImageView mAppIcon;
    private TextView mAppName;
    private EditText mEditTextPin;
    private Button mButtonSubmit, mButtonCancel;

    // Data passed via intent
    private String mBlockedPackageName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Use a simple, non-fullscreen dialog theme
        setTheme(R.style.Theme_Guard_Dialog);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_app_block);

        setFinishOnTouchOutside(false);

        mBlockedPackageName = getIntent().getStringExtra("BLOCKED_PACKAGE_NAME");
        if (mBlockedPackageName == null) {
            finish();
            return;
        }

        // --- UI Initialization (Finding IDs from activity_app_block.xml) ---
        mAppIcon = findViewById(R.id.imageAppIcon);
        mAppName = findViewById(R.id.textAppName);
        mEditTextPin = findViewById(R.id.editTextPin);
        mButtonSubmit = findViewById(R.id.buttonSubmit);
        mButtonCancel = findViewById(R.id.buttonCancel);

        // Update dialog content
        TextView title = findViewById(R.id.textTitle);
        TextView subtitle = findViewById(R.id.textSubtitle);
        title.setText("App Blocked");
        subtitle.setText("This app is currently restricted. Please enter the Parent PIN to temporarily access this app.");

        loadAppInfo(mBlockedPackageName);


        // --- Set Listeners ---
        mButtonSubmit.setOnClickListener(v -> checkPin());
        mButtonCancel.setOnClickListener(v -> {
            goHome();
            finish();
        });

        // Block back press
        OnBackPressedCallback callback = new OnBackPressedCallback(true /* enabled by default */) {
            @Override
            public void handleOnBackPressed() {
                Toast.makeText(AppBlockActivity.this, "Access denied. Use PIN or Cancel.", Toast.LENGTH_SHORT).show();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, callback);
    }

    private void loadAppInfo(String packageName) {
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            Drawable icon = pm.getApplicationIcon(appInfo);
            String name = pm.getApplicationLabel(appInfo).toString();

            mAppIcon.setImageDrawable(icon);
            mAppName.setText(name);

        } catch (PackageManager.NameNotFoundException e) {
            mAppName.setText(packageName);
            mAppIcon.setImageResource(android.R.drawable.sym_def_app_icon);
        }
    }

    private void checkPin() {
        String enteredPin = mEditTextPin.getText().toString();
        if (enteredPin.length() != 4) {
            mEditTextPin.setError("PIN must be 4 digits");
            return;
        }

        // --- FIX: Use the local constant to get SharedPreferences ---
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedPin = prefs.getString(PREF_SECURITY_PIN, null);
        // --- END FIX ---

        if (savedPin != null && savedPin.equals(enteredPin)) {
            // PIN is correct! Grant access until the phone is likely locked/closed.

            SharedPreferences.Editor editor = prefs.edit();

            // Set bypass time to 24 hours (86,400,000 milliseconds)
            long unlockTime = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(24);

            // Save the bypass time specific to the app's package name
            editor.putLong("BYPASS_" + mBlockedPackageName, unlockTime);
            editor.apply();

            Toast.makeText(this, "Access Granted.", Toast.LENGTH_SHORT).show();

            // Relaunch the blocked app to let the user in
            Intent launchIntent = getPackageManager().getLaunchIntentForPackage(mBlockedPackageName);
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(launchIntent);
            }
            finish(); // Close the block screen
        } else {
            mEditTextPin.setError("Incorrect PIN");
        }
    }

    private void goHome() {
        Intent startMain = new Intent(Intent.ACTION_MAIN);
        startMain.addCategory(Intent.CATEGORY_HOME);
        startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(startMain);
    }
}