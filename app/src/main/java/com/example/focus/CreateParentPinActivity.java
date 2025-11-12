package com.example.focus;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class CreateParentPinActivity extends AppCompatActivity {

    private static final String TAG = "CreateParentPin";

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore mStore;
    private String mParentId;

    // UI
    private EditText mEditPinNew, mEditPinConfirm;
    private Button mButtonSavePin;
    private TextView mTextLogout;
    private LoadingDialog mLoadingDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_parent_pin);

        mAuth = FirebaseAuth.getInstance();
        mStore = FirebaseFirestore.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            goToLogin();
            return;
        }
        mParentId = currentUser.getUid();

        mLoadingDialog = new LoadingDialog(this);
        mEditPinNew = findViewById(R.id.editPinNew);
        mEditPinConfirm = findViewById(R.id.editPinConfirm);
        mButtonSavePin = findViewById(R.id.buttonSavePin);
        mTextLogout = findViewById(R.id.textLogout);

        mButtonSavePin.setOnClickListener(v -> savePin());
        mTextLogout.setOnClickListener(v -> {
            mAuth.signOut();
            goToLogin();
        });

        // Block the back button. User must create PIN or logout.
        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Toast.makeText(CreateParentPinActivity.this, "Please create a PIN or logout.", Toast.LENGTH_SHORT).show();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, callback);
    }

    private void savePin() {
        String newPin = mEditPinNew.getText().toString();
        String confirmPin = mEditPinConfirm.getText().toString();

        if (TextUtils.isEmpty(newPin) || newPin.length() != 4) {
            mEditPinNew.setError("PIN must be 4 digits");
            return;
        }
        if (!newPin.equals(confirmPin)) {
            mEditPinConfirm.setError("PINs do not match");
            return;
        }

        mLoadingDialog.show();

        // Save this PIN to the PARENT's user document
        Map<String, Object> pinData = new HashMap<>();
        pinData.put("securityPin", newPin); // This is the new master Parent PIN

        mStore.collection("users").document(mParentId)
                .set(pinData, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    mLoadingDialog.hide();
                    Toast.makeText(this, "PIN created successfully!", Toast.LENGTH_SHORT).show();
                    // Now that PIN is created, send them to the dashboard
                    Intent intent = new Intent(CreateParentPinActivity.this, ParentDashboardActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    mLoadingDialog.hide();
                    Toast.makeText(this, "Failed to save PIN: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}