package com.example.focus;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class NoInternetActivity extends AppCompatActivity {

    private Button mButtonRetry;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_Guard_Dark); // Use the dark theme
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_no_internet);

        mButtonRetry = findViewById(R.id.buttonRetry);

        mButtonRetry.setOnClickListener(v -> {
            if (isNetworkAvailable()) {
                // Internet is back, go to WelcomeActivity to restart the check
                Intent intent = new Intent(NoInternetActivity.this, WelcomeActivity.class);
                startActivity(intent);
                finish(); // Close this activity
            } else {
                Toast.makeText(this, "Still no connection...", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        Network activeNetwork = cm.getActiveNetwork();
        if (activeNetwork == null) return false;

        NetworkCapabilities caps = cm.getNetworkCapabilities(activeNetwork);
        if (caps == null) return false;

        // Check for Wi-Fi, Cellular, or Ethernet
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
    }

    @Override
    public void onBackPressed() {
        // Exit the app completely if user presses back from this screen
        super.onBackPressed();
        finishAffinity();
    }
}