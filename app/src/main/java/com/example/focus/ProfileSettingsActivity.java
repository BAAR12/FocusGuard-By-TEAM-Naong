package com.example.focus;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class ProfileSettingsActivity extends AppCompatActivity {

    private static final String TAG = "ProfileSettings";

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore mStore;
    private FirebaseUser mCurrentUser;
    private DocumentReference mUserDocRef;

    // UI Elements - Profile
    private EditText mEditFirstName, mEditLastName;
    private TextView mTextEmail;
    private Button mButtonUpdateProfile;

    // UI Elements - Password
    private EditText mEditCurrentPassword, mEditNewPassword;
    private Button mButtonChangePassword;

    // Loading Dialog
    private LoadingDialog mLoadingDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_settings);

        mAuth = FirebaseAuth.getInstance();
        mStore = FirebaseFirestore.getInstance();
        mCurrentUser = mAuth.getCurrentUser();

        if (mCurrentUser == null) {
            finish(); // Should never happen if flow is correct
            return;
        }

        mUserDocRef = mStore.collection("users").document(mCurrentUser.getUid());
        mLoadingDialog = new LoadingDialog(this);

        // --- Toolbar Setup ---
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        // --- Initialize UI ---
        mEditFirstName = findViewById(R.id.editFirstName);
        mEditLastName = findViewById(R.id.editLastName);
        mTextEmail = findViewById(R.id.textEmail);
        mButtonUpdateProfile = findViewById(R.id.buttonUpdateProfile);

        mEditCurrentPassword = findViewById(R.id.editCurrentPassword);
        mEditNewPassword = findViewById(R.id.editNewPassword);
        mButtonChangePassword = findViewById(R.id.buttonChangePassword);

        // --- Load existing data ---
        loadProfileData();

        // --- Set Listeners ---
        mButtonUpdateProfile.setOnClickListener(v -> updateProfile());
        mButtonChangePassword.setOnClickListener(v -> changePassword());
    }

    private void loadProfileData() {
        mLoadingDialog.show();
        mTextEmail.setText(mCurrentUser.getEmail());

        mUserDocRef.get().addOnSuccessListener(documentSnapshot -> {
            mLoadingDialog.hide();
            if (documentSnapshot.exists()) {
                mEditFirstName.setText(documentSnapshot.getString("firstName"));
                mEditLastName.setText(documentSnapshot.getString("lastName"));
            }
        }).addOnFailureListener(e -> {
            mLoadingDialog.hide();
            Toast.makeText(this, "Failed to load profile data.", Toast.LENGTH_SHORT).show();
            Log.w(TAG, "Load profile failed", e);
        });
    }

    private void updateProfile() {
        String firstName = mEditFirstName.getText().toString().trim();
        String lastName = mEditLastName.getText().toString().trim();

        if (TextUtils.isEmpty(firstName) || TextUtils.isEmpty(lastName)) {
            Toast.makeText(this, "First Name and Last Name cannot be empty.", Toast.LENGTH_SHORT).show();
            return;
        }

        mLoadingDialog.show();
        Map<String, Object> updates = new HashMap<>();
        updates.put("firstName", firstName);
        updates.put("lastName", lastName);

        mUserDocRef.set(updates, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    mLoadingDialog.hide();
                    Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    mLoadingDialog.hide();
                    Toast.makeText(this, "Failed to update profile.", Toast.LENGTH_SHORT).show();
                });
    }

    private void changePassword() {
        String currentPassword = mEditCurrentPassword.getText().toString();
        String newPassword = mEditNewPassword.getText().toString();

        if (TextUtils.isEmpty(currentPassword) || TextUtils.isEmpty(newPassword)) {
            Toast.makeText(this, "All password fields are required.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (newPassword.length() < 6) {
            Toast.makeText(this, "New password must be at least 6 characters.", Toast.LENGTH_SHORT).show();
            return;
        }

        mLoadingDialog.show();

        // Step 1: Re-authenticate user with current password
        AuthCredential credential = EmailAuthProvider.getCredential(mCurrentUser.getEmail(), currentPassword);

        mCurrentUser.reauthenticate(credential)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        // Step 2: If re-authentication succeeds, update the password
                        mCurrentUser.updatePassword(newPassword)
                                .addOnCompleteListener(task2 -> {
                                    mLoadingDialog.hide();
                                    if (task2.isSuccessful()) {
                                        Toast.makeText(ProfileSettingsActivity.this, "Password changed successfully!", Toast.LENGTH_LONG).show();
                                        // Clear fields
                                        mEditCurrentPassword.setText("");
                                        mEditNewPassword.setText("");
                                    } else {
                                        Toast.makeText(ProfileSettingsActivity.this, "Failed to change password. Try logging in again.", Toast.LENGTH_LONG).show();
                                    }
                                });
                    } else {
                        mLoadingDialog.hide();
                        mEditCurrentPassword.setError("Incorrect current password.");
                    }
                });
    }
}