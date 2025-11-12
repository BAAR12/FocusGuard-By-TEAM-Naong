package com.example.focus;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private static final String TAG = "RegisterActivity";

    // UI
    private EditText mEmail, mPassword, mFirstName, mMiddleName, mLastName;
    private Button mRegisterButton, mButtonParent, mButtonStudent;
    private TextView mLoginText;
    // REMOVED: Google/Facebook buttons

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore mStore;

    // State
    private String mSelectedRole = "Parent"; // Default role
    private boolean isPasswordVisible = false;
    private LoadingDialog mLoadingDialog;

    // REMOVED: Google/Facebook variables

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mLoadingDialog = new LoadingDialog(this);
        mAuth = FirebaseAuth.getInstance();
        mStore = FirebaseFirestore.getInstance();

        // --- UI ---
        mEmail = findViewById(R.id.editTextEmail);
        mPassword = findViewById(R.id.editTextPassword);
        mFirstName = findViewById(R.id.editTextFirstName);
        mMiddleName = findViewById(R.id.editTextMiddleName);
        mLastName = findViewById(R.id.editTextLastName);

        mRegisterButton = findViewById(R.id.buttonSignUp);
        mButtonParent = findViewById(R.id.buttonParent);
        mButtonStudent = findViewById(R.id.buttonStudent);
        mLoginText = findViewById(R.id.textViewLogin);
        // REMOVED: Google/Facebook findViewById

        // REMOVED: Social Login configurations

        // --- Listeners ---
        mButtonParent.setOnClickListener(v -> {
            mSelectedRole = "Parent";
            if (mButtonParent != null) mButtonParent.setBackgroundResource(R.drawable.button_blue_background);
            if (mButtonStudent != null) mButtonStudent.setBackgroundResource(android.R.color.transparent);
        });

        mButtonStudent.setOnClickListener(v -> {
            mSelectedRole = "Student";
            if (mButtonStudent != null) mButtonStudent.setBackgroundResource(R.drawable.button_blue_background);
            if (mButtonParent != null) mButtonParent.setBackgroundResource(android.R.color.transparent);
        });

        mRegisterButton.setOnClickListener(v -> registerUser());

        mLoginText.setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });

        // REMOVED: Google/Facebook click listeners

        setupPasswordToggle();
    }

    // REMOVED: onActivityResult for Facebook
    // REMOVED: All Google/Facebook methods:
    // configureGoogleSignIn, googleSignIn, firebaseAuthWithGoogle
    // configureFacebookSignIn, handleFacebookAccessToken, handleSocialLogin, saveSocialUserData

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


    // --- Email/Password Register ---
    private void registerUser() {
        String email = mEmail.getText().toString().trim();
        String password = mPassword.getText().toString().trim();
        String firstName = mFirstName.getText().toString().trim();
        String middleName = mMiddleName.getText().toString().trim();
        String lastName = mLastName.getText().toString().trim();

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password) || TextUtils.isEmpty(firstName) || TextUtils.isEmpty(lastName)) {
            Toast.makeText(this, "All fields (except middle name) are required.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.length() < 6) {
            mPassword.setError("Password must be at least 6 characters");
            return;
        }

        mLoadingDialog.show();

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            user.sendEmailVerification().addOnCompleteListener(task1 -> {
                                if (task1.isSuccessful()) {
                                    Log.d(TAG, "Verification email sent.");
                                } else {
                                    Log.w(TAG, "Failed to send verification email.", task1.getException());
                                }
                            });
                        }
                        saveUserData(user, firstName, middleName, lastName, mSelectedRole);
                    } else {
                        mLoadingDialog.hide();
                        Toast.makeText(RegisterActivity.this, "Authentication failed. " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // --- User Data Saving ---
    private void saveUserData(FirebaseUser user, String firstName, String middleName, String lastName, String role) {
        if (user == null) {
            mLoadingDialog.hide();
            return;
        }
        String userId = user.getUid();
        Map<String, Object> userData = new HashMap<>();
        userData.put("firstName", firstName);
        userData.put("middleName", middleName);
        userData.put("lastName", lastName);
        userData.put("email", user.getEmail());
        userData.put("role", role);
        userData.put("totalPomodoros", 0);
        userData.put("totalHours", 0.0);
        userData.put("focusDurationMillis", 25 * 60 * 1000);

        mStore.collection("users").document(userId)
                .set(userData)
                .addOnSuccessListener(aVoid -> {
                    mLoadingDialog.hide();
                    Toast.makeText(RegisterActivity.this, "Registration Successful. Please check your email to verify.", Toast.LENGTH_LONG).show();
                    Intent intent = new Intent(RegisterActivity.this, VerifyEmailActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    mLoadingDialog.hide();
                    Toast.makeText(RegisterActivity.this, "Error saving user details.", Toast.LENGTH_SHORT).show();
                });
    }
}