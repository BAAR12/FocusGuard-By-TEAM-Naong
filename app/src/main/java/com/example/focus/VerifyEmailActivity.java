package com.example.focus;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class VerifyEmailActivity extends AppCompatActivity {

    private static final String TAG = "VerifyEmailActivity";

    // UI
    private Button mCheckVerifiedButton;
    private TextView mResendEmailText;

    // Firebase
    private FirebaseAuth mAuth;
    // --- REMOVED: No longer need Firestore here ---
    // private FirebaseFirestore mStore;

    private LoadingDialog mLoadingDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_Guard_Dark);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verify_email);

        mLoadingDialog = new LoadingDialog(this);

        mAuth = FirebaseAuth.getInstance();
        // --- REMOVED: No longer need Firestore here ---
        // mStore = FirebaseFirestore.getInstance();

        mCheckVerifiedButton = findViewById(R.id.buttonCheckVerification);
        mResendEmailText = findViewById(R.id.textResendEmail);

        mCheckVerifiedButton.setOnClickListener(v -> checkVerificationStatus());
        mResendEmailText.setOnClickListener(v -> resendVerificationEmail());
    }

    private void checkVerificationStatus() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            goToLogin();
            return;
        }

        mLoadingDialog.show();

        user.reload().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                // --- THIS IS THE FIX ---
                // We re-check the user object *after* reloading
                FirebaseUser updatedUser = mAuth.getCurrentUser();
                if (updatedUser != null && updatedUser.isEmailVerified()) {
                    Log.d(TAG, "Email is verified.");
                    // Email is verified! Go to WelcomeActivity to be sorted.
                    mLoadingDialog.hide();
                    goToActivity(WelcomeActivity.class);
                } else {
                    // --- END OF FIX ---
                    mLoadingDialog.hide();
                    Log.w(TAG, "Email is not verified.");
                    Toast.makeText(this, "Email not verified. Please check your inbox.", Toast.LENGTH_SHORT).show();
                }
            } else {
                mLoadingDialog.hide();
                Log.w(TAG, "Failed to reload user.", task.getException());
                Toast.makeText(this, "Failed to check status. Please try again.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void resendVerificationEmail() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            mLoadingDialog.show(); // Show loading for resend
            user.sendEmailVerification().addOnCompleteListener(task -> {
                mLoadingDialog.hide(); // Hide after resend
                if (task.isSuccessful()) {
                    Toast.makeText(this, "Verification email sent.", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Failed to send email. Please wait a moment and try again.", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    // --- REMOVED: checkUserRoleAndRedirect() method ---
    // (This logic now lives *only* in WelcomeActivity)

    private void goToActivity(Class<?> activityClass) {
        Intent intent = new Intent(this, activityClass);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}