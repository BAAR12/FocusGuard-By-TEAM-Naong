package com.example.focus;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.concurrent.TimeUnit;

public class PinCheckActivity extends AppCompatActivity {

    private static final String TAG = "PinCheckActivity";

    private EditText mEditTextPin;
    private Button mButtonPinSubmit, mButtonPinCancel;

    private DevicePolicyManager mDevicePolicyManager;
    private ComponentName mDeviceAdminComponent;

    private String mActionType; // What to do on success

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(R.style.Theme_Guard_Dialog);
        setContentView(R.layout.activity_pin_check);
        setFinishOnTouchOutside(false); // Don't close when tapping outside

        // Get the action from the Intent
        mActionType = getIntent().getStringExtra("ACTION_TYPE");
        if (mActionType == null) {
            mActionType = "UNKNOWN"; // Default
        }

        // --- UPDATED IDs to match your new XML ---
        mEditTextPin = findViewById(R.id.editTextPin);
        mButtonPinSubmit = findViewById(R.id.buttonPinSubmit);
        mButtonPinCancel = findViewById(R.id.buttonPinCancel);
        // --- END OF UPDATE ---

        mDevicePolicyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        mDeviceAdminComponent = new ComponentName(this, DeviceAdminReceiver.class);

        // --- Set listeners for the new buttons ---
        mButtonPinSubmit.setOnClickListener(v -> checkPin());
        mButtonPinCancel.setOnClickListener(v -> {
            Toast.makeText(this, "Cancelled", Toast.LENGTH_SHORT).show();
            // Go to home screen on cancel to break the loop
            goHome();
            finish();
        });

        // --- NEW: OnBackPressedCallback for this Activity ---
        // This replaces the old onBackPressed() method
        OnBackPressedCallback callback = new OnBackPressedCallback(true /* enabled by default */) {
            @Override
            public void handleOnBackPressed() {
                // This is the logic from your old method
                Toast.makeText(PinCheckActivity.this, "Please enter PIN or cancel.", Toast.LENGTH_SHORT).show();
                // We do *not* call super.onBackPressed() or setEnabled(false)
                // because we want to block the back press every time.
            }
        };
        getOnBackPressedDispatcher().addCallback(this, callback);
        // --- END OF NEW CODE ---
    }

    private void checkPin() {
        String enteredPin = mEditTextPin.getText().toString();
        if (enteredPin.length() != 4) {
            mEditTextPin.setError("PIN must be 4 digits");
            return;
        }

        SharedPreferences prefs = getSharedPreferences(StudentDashboardActivity.PREFS_NAME, MODE_PRIVATE);
        String savedPin = prefs.getString(StudentDashboardActivity.PREF_SECURITY_PIN, null);

        if (savedPin != null && savedPin.equals(enteredPin)) {
            // PIN is correct!

            if ("ACTION_DISABLE_ADMIN".equals(mActionType)) {
                // The service blocked the Admin screen. We just go home.
                // The user must now go back to settings, get a "free pass",
                // and then manually disable the admin.
                Toast.makeText(this, "PIN correct.", Toast.LENGTH_SHORT).show();
                goHome();

            } else if ("UNLOCK_SETTINGS".equals(mActionType)) {
                // This is a request to unlock the main Settings page.
                Toast.makeText(this, "PIN correct. Settings unlocked for 5 minutes.", Toast.LENGTH_SHORT).show();
                grantFreePass(prefs);
            }

            // Close this PIN screen
            finish();
        } else {
            mEditTextPin.setError("Incorrect PIN");
        }
    }

    private void grantFreePass(SharedPreferences prefs) {
        // Grant a 5-minute "free pass"
        SharedPreferences.Editor editor = prefs.edit();
        long unlockTime = System.currentTimeMillis() + (5 * 60 * 1000); // 5 minutes from now
        editor.putLong(StudentDashboardActivity.PREF_SETTINGS_UNLOCKED_UNTIL, unlockTime);
        editor.apply();
    }

    private void goHome() {
        Intent startMain = new Intent(Intent.ACTION_MAIN);
        startMain.addCategory(Intent.CATEGORY_HOME);
        startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(startMain);
    }
}