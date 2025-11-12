package com.example.focus;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

public class HomeActivity extends AppCompatActivity {

    TextView welcomeText;
    Button logoutButton;
    FirebaseAuth mAuth;
    FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        welcomeText = findViewById(R.id.welcomeText);
        logoutButton = findViewById(R.id.logoutButton);
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        FirebaseUser user = mAuth.getCurrentUser();

        if (user != null) {
            // User is signed in, get their info
            String userId = user.getUid();
            DocumentReference docRef = db.collection("users").document(userId);

            docRef.get().addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    String firstName = documentSnapshot.getString("firstName");
                    String role = documentSnapshot.getString("role");
                    welcomeText.setText("Welcome, " + firstName + "! (" + role + ")");
                } else {
                    welcomeText.setText("Welcome!");
                }
            }).addOnFailureListener(e -> {
                welcomeText.setText("Welcome!");
                Toast.makeText(HomeActivity.this, "Failed to load user data.", Toast.LENGTH_SHORT).show();
            });

        } else {
            // No user is signed in, send back to login
            goToLogin();
        }

        logoutButton.setOnClickListener(v -> {
            mAuth.signOut();
            goToLogin();
        });
    }

    private void goToLogin() {
        Intent intent = new Intent(HomeActivity.this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}

