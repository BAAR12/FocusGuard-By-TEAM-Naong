package com.example.focus;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore mStore;

    // UI elements
    private EditText mEmail, mPassword;
    private Button mLoginButton;
    private TextView mRegisterText, mForgotText;
    private CheckBox mRememberMe;
    private LoadingDialog mLoadingDialog;

    // REMOVED: Google/Facebook variables

    private boolean isPasswordVisible = false;

    // SharedPreferences
    private SharedPreferences mPrefs;
    private static final String PREFS_NAME = "FocusGuardPrefs";
    private static final String PREF_EMAIL = "email";
    private static final String PREF_PASSWORD = "password";
    private static final String PREF_REMEMBER = "remember";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Init Dialog
        mLoadingDialog = new LoadingDialog(this);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        mStore = FirebaseFirestore.getInstance();

        // --- UI Initialization ---
        mEmail = findViewById(R.id.editTextEmail);
        mPassword = findViewById(R.id.editTextPassword);
        mLoginButton = findViewById(R.id.buttonSignIn);
        mRegisterText = findViewById(R.id.textViewRegister);
        mForgotText = findViewById(R.id.textForgotPassword);
        mRememberMe = findViewById(R.id.checkboxRememberMe);
        // REMOVED: Google/Facebook button findViewById

        // --- Configure Social Logins ---
        // REMOVED: configureGoogleSignIn();
        // REMOVED: configureFacebookSignIn();

        // --- OnClick Listeners ---
        mLoginButton.setOnClickListener(v -> loginUser());
        mRegisterText.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
        });
        mForgotText.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, ForgotPasswordActivity.class));
        });
        // REMOVED: Google/Facebook button listeners


        setupPasswordToggle();

        mPrefs = getSharedPreferences(PREFS_NAME, 0);
        loadPreferences();
    }

    // REMOVED: onActivityResult for Facebook

    // REMOVED: All Google/Facebook methods:
    // configureGoogleSignIn, googleSignIn, firebaseAuthWithGoogle
    // configureFacebookSignIn, handleFacebookAccessToken, handleSocialLogin, saveSocialUserData

    private void loadPreferences() {
        boolean remember = mPrefs.getBoolean(PREF_REMEMBER, false);
        if (remember) {
            String email = mPrefs.getString(PREF_EMAIL, "");
            String password = mPrefs.getString(PREF_PASSWORD, "");
            mEmail.setText(email);
            mPassword.setText(password);
            mRememberMe.setChecked(true);
        }
    }

    private void setupPasswordToggle() {
        mPassword.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                final int DRAWABLE_RIGHT = 2;
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    if (event.getRawX() >= (mPassword.getRight() - mPassword.getCompoundDrawables()[DRAWABLE_RIGHT].getBounds().width())) {
                        togglePasswordVisibility();
                        return true;
                    }
                }
                return false;
            }
        });
    }

    private void togglePasswordVisibility() {
        Drawable leftDrawable = mPassword.getCompoundDrawables()[0];
        Drawable eyeDrawable;

        if (isPasswordVisible) {
            mPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
            eyeDrawable = ContextCompat.getDrawable(this, R.drawable.ic_eye_hide_icon);
            isPasswordVisible = false;
        } else {
            mPassword.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
            eyeDrawable = ContextCompat.getDrawable(this, R.drawable.ic_eye_show_icon);
            isPasswordVisible = true;
        }
        mPassword.setCompoundDrawablesWithIntrinsicBounds(leftDrawable, null, eyeDrawable, null);
        mPassword.setSelection(mPassword.getText().length());
    }


    private void loginUser() {
        String email = mEmail.getText().toString().trim();
        String password = mPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            mEmail.setError("Email is required.");
            return;
        }
        if (TextUtils.isEmpty(password)) {
            mPassword.setError("Password is required.");
            return;
        }

        SharedPreferences.Editor editor = mPrefs.edit();
        if (mRememberMe.isChecked()) {
            editor.putString(PREF_EMAIL, email);
            editor.putString(PREF_PASSWORD, password);
            editor.putBoolean(PREF_REMEMBER, true);
        } else {
            editor.clear();
        }
        editor.apply();

        mLoadingDialog.show();

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null && user.isEmailVerified()) {
                            Log.d(TAG, "signInWithEmail:success");
                            // --- THIS IS THE FIX ---
                            // We no longer check the role here. We go back to WelcomeActivity.
                            // WelcomeActivity will be the "bouncer" and check the role/permissions.
                            mLoadingDialog.hide();
                            goToActivity(WelcomeActivity.class);
                            // --- END OF FIX ---
                        } else {
                            mLoadingDialog.hide();
                            Log.w(TAG, "signInWithEmail:failure - Email not verified");
                            showVerificationDialog(user);
                            mAuth.signOut();
                        }
                    } else {
                        mLoadingDialog.hide();
                        Log.w(TAG, "signInWithEmail:failure", task.getException());
                        Toast.makeText(LoginActivity.this, "Authentication failed.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showVerificationDialog(FirebaseUser user) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AlertDialogTheme);
        builder.setTitle("Email Not Verified");
        builder.setMessage("Please check your email to verify your account before logging in. (Check your spam folder!)");

        builder.setPositiveButton("Resend Email", (dialog, which) -> {
            if (user != null) {
                mLoadingDialog.show();
                user.sendEmailVerification().addOnCompleteListener(task -> {
                    mLoadingDialog.hide();
                    if (task.isSuccessful()) {
                        Toast.makeText(LoginActivity.this, "Verification email sent.", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(LoginActivity.this, "Failed to send email.", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.create().show();
    }

    // --- REMOVED: redirectToDashboard() method ---

    private void goToActivity(Class<?> activityClass) {
        // --- UPDATED: Removed "Login Successful" Toast, as WelcomeActivity will handle it ---
        Intent intent = new Intent(LoginActivity.this, activityClass);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}