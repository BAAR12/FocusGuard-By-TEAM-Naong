package com.example.focus;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns; // <-- ADDED IMPORT
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class ForgotPasswordActivity extends AppCompatActivity {

    private EditText mEmail;
    private Button mSendLinkButton;
    private TextView mBackToLogin;

    private FirebaseAuth mAuth;

    // --- ADDED: Loading Dialog ---
    private LoadingDialog mLoadingDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_Guard_Dark);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        // --- Init UI ---
        mEmail = findViewById(R.id.editTextEmail);
        mSendLinkButton = findViewById(R.id.buttonSendLink);
        mBackToLogin = findViewById(R.id.textBackToLogin);

        mAuth = FirebaseAuth.getInstance();

        // --- ADDED: Init Loading Dialog ---
        mLoadingDialog = new LoadingDialog(this);

        // --- Set Listeners ---
        mSendLinkButton.setOnClickListener(v -> sendPasswordResetEmail());

        mBackToLogin.setOnClickListener(v -> {
            // Go back to LoginActivity
            finish(); // Just close this activity, don't create a new one
        });
    }

    private void sendPasswordResetEmail() {
        String email = mEmail.getText().toString().trim();

        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            mEmail.setError("Please enter a valid email address.");
            return;
        }

        // --- Show loading dialog ---
        mLoadingDialog.show();

        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    // --- Hide loading dialog ---
                    mLoadingDialog.hide();

                    if (task.isSuccessful()) {
                        Toast.makeText(ForgotPasswordActivity.this, "Password reset link sent. Please check your email.", Toast.LENGTH_LONG).show();
                        // Go back to login screen
                        finish();
                    } else {
                        Toast.makeText(ForgotPasswordActivity.this, "Failed to send reset link. " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }
}