package com.example.focus;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ParentalControlsActivity extends AppCompatActivity {

    private static final String TAG = "ParentalControls";

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore mStore;
    private FirebaseUser mCurrentUser;
    private String mParentId;
    private String mSelectedChildId;
    private String mParentPin;

    // UI
    private Toolbar mToolbar;
    private EditText mEditPinCurrent, mEditPinNew, mEditPinConfirm;
    private SwitchCompat mSwitchUninstall, mSwitchSettingsLock;
    private RecyclerView mRecyclerLockedApps;
    private Button mButtonAddMoreApps, mButtonSaveSettings;

    private LinearLayout mPinProtectionCard, mAppProtectionCard, mLockedAppsCard;
    private TextView mLabelPinCurrent;

    private boolean mMustCreatePin = false; // Flag from WelcomeActivity

    // App List
    private LockedAppAdapter mAdapter;
    private List<LockedAppAdapter.AppInfo> mAppList = new ArrayList<>();
    private Map<String, Boolean> mLockedAppMap = new HashMap<>();

    private List<String> mSocialApps = Arrays.asList(
            "com.instagram.android", "com.zhiliaoapp.musically", "com.google.android.youtube",
            "com.facebook.katana", "com.twitter.android", "com.facebook.orca",
            "com.whatsapp", "com.snapchat", "com.android.chrome", "com.google.android.gm"
    );


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parental_controls);

        mSelectedChildId = getIntent().getStringExtra("SELECTED_CHILD_ID");
        mMustCreatePin = getIntent().getBooleanExtra("FORCE_PIN_CREATION", false);

        if (mSelectedChildId == null || mSelectedChildId.isEmpty()) {
            if (!mMustCreatePin) { // If we're just creating a PIN, childId isn't needed yet
                Toast.makeText(this, "No child selected.", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        }

        mAuth = FirebaseAuth.getInstance();
        mStore = FirebaseFirestore.getInstance();
        mCurrentUser = mAuth.getCurrentUser();
        if (mCurrentUser == null) {
            goToLogin();
            return;
        }
        mParentId = mCurrentUser.getUid();

        mToolbar = findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        mToolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (mMustCreatePin) {
                    Toast.makeText(ParentalControlsActivity.this, "You must create a PIN to continue.", Toast.LENGTH_SHORT).show();
                    // Don't allow back press
                } else {
                    setEnabled(false); // Disable this callback
                    finish(); // Allow normal back press
                }
            }
        };
        getOnBackPressedDispatcher().addCallback(this, callback);


        // --- UI Initialization ---
        mPinProtectionCard = findViewById(R.id.pinProtectionCard);
        mAppProtectionCard = findViewById(R.id.appProtectionCard);
        mLockedAppsCard = findViewById(R.id.lockedAppsCard);
        mLabelPinCurrent = findViewById(R.id.labelPinCurrent);

        mEditPinCurrent = findViewById(R.id.editPinCurrent);
        mEditPinNew = findViewById(R.id.editPinNew);
        mEditPinConfirm = findViewById(R.id.editPinConfirm);
        mSwitchUninstall = findViewById(R.id.switchUninstall);
        mSwitchSettingsLock = findViewById(R.id.switchSettingsLock);
        mRecyclerLockedApps = findViewById(R.id.recyclerLockedApps);
        mButtonAddMoreApps = findViewById(R.id.buttonAddMoreApps);
        mButtonSaveSettings = findViewById(R.id.buttonSaveSettings);

        if (mMustCreatePin) {
            // We are forcing PIN creation, hide all other cards
            mAppProtectionCard.setVisibility(View.GONE);
            mLockedAppsCard.setVisibility(View.GONE);
            mToolbar.setNavigationIcon(null); // Hide back arrow
            mButtonSaveSettings.setText("Save PIN and Continue");
            mEditPinCurrent.setVisibility(View.GONE); // Hide "Current PIN"
            mLabelPinCurrent.setVisibility(View.GONE); // Hide "Current PIN" label
        } else {
            // This is a normal visit, load child data
            fetchParentPinAndChildSettings();
        }

        mRecyclerLockedApps.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new LockedAppAdapter(this, mAppList, mLockedAppMap);
        mRecyclerLockedApps.setAdapter(mAdapter);

        mButtonSaveSettings.setOnClickListener(v -> saveAllSettings());
        mButtonAddMoreApps.setOnClickListener(v -> showAppListDialog());
    }

    private void fetchParentPinAndChildSettings() {
        // 1. Get the Parent's master PIN
        mStore.collection("users").document(mParentId).get()
                .addOnSuccessListener(parentDoc -> {
                    if (parentDoc.exists()) {
                        mParentPin = parentDoc.getString("securityPin");
                        if (mParentPin == null || mParentPin.isEmpty()) {
                            // This should never happen because WelcomeActivity checks it
                            Toast.makeText(this, "Error: Parent PIN not found.", Toast.LENGTH_SHORT).show();
                            finish();
                            return;
                        }
                        // 2. Now that we have the PIN, load the child's settings
                        loadChildSettings();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load parent data.", Toast.LENGTH_SHORT).show();
                });
    }

    private void loadChildSettings() {
        mStore.collection("users").document(mSelectedChildId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Boolean uninstallLock = documentSnapshot.getBoolean("uninstallLock");
                        Boolean settingsLock = documentSnapshot.getBoolean("settingsLock");
                        mSwitchUninstall.setChecked(uninstallLock != null && uninstallLock);
                        mSwitchSettingsLock.setChecked(settingsLock != null && settingsLock);

                        Map<String, Boolean> loadedMap = (Map<String, Boolean>) documentSnapshot.get("lockedAppMap");
                        if (loadedMap != null) {
                            for (Map.Entry<String, Boolean> entry : loadedMap.entrySet()) {
                                mLockedAppMap.put(entry.getKey(), entry.getValue() == Boolean.TRUE);
                            }
                        }

                        populateAppList();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load child settings.", Toast.LENGTH_SHORT).show();
                });
    }

    private void populateAppList() {
        mAppList.clear();
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> allApps = pm.getInstalledApplications(PackageManager.GET_ACTIVITIES | PackageManager.GET_META_DATA);

        Set<String> processedPackages = new HashSet<>();
        List<LockedAppAdapter.AppInfo> tempAppList = new ArrayList<>();

        // 1. Show default social apps that are installed
        for (String socialPackage : mSocialApps) {
            try {
                ApplicationInfo appInfo = pm.getApplicationInfo(socialPackage, 0);
                String appName = pm.getApplicationLabel(appInfo).toString();
                boolean isLocked = mLockedAppMap.containsKey(socialPackage) && mLockedAppMap.get(socialPackage) == Boolean.TRUE;

                tempAppList.add(new LockedAppAdapter.AppInfo(appName, appInfo.packageName, pm.getApplicationIcon(appInfo), isLocked));
                processedPackages.add(socialPackage);
            } catch (PackageManager.NameNotFoundException ignored) {
                // App not installed on parent's phone, skip
            }
        }

        // 2. Add any other apps that are in the map but were not in the default social list
        for (Map.Entry<String, Boolean> entry : mLockedAppMap.entrySet()) {
            String packageName = entry.getKey();
            if (entry.getValue() == Boolean.TRUE && !processedPackages.contains(packageName)) {
                try {
                    ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
                    String appName = pm.getApplicationLabel(appInfo).toString();
                    tempAppList.add(new LockedAppAdapter.AppInfo(appName, packageName, pm.getApplicationIcon(appInfo), true));
                    processedPackages.add(packageName);
                } catch (PackageManager.NameNotFoundException e) {
                    mAppList.add(new LockedAppAdapter.AppInfo(packageName, packageName, getDrawable(android.R.drawable.sym_def_app_icon), true));
                }
            }
        }

        mAppList.addAll(tempAppList);
        mAdapter.notifyDataSetChanged();
    }

    private void showAppListDialog() {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> installedApps = pm.getInstalledApplications(PackageManager.GET_ACTIVITIES | PackageManager.GET_META_DATA);

        List<String> appNames = new ArrayList<>();
        List<String> packageNames = new ArrayList<>();
        List<Boolean> initialCheckedStates = new ArrayList<>();

        for (ApplicationInfo appInfo : installedApps) {
            if (((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0 || (appInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0)
                    && !appInfo.packageName.equals(getPackageName())) {

                try {
                    if (pm.getLaunchIntentForPackage(appInfo.packageName) != null) {
                        String appName = pm.getApplicationLabel(appInfo).toString();

                        appNames.add(appName);
                        packageNames.add(appInfo.packageName);
                        initialCheckedStates.add(mLockedAppMap.containsKey(appInfo.packageName) && mLockedAppMap.get(appInfo.packageName) == Boolean.TRUE);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Error processing app for dialog: " + appInfo.packageName, e);
                }
            }
        }

        final String[] items = appNames.toArray(new String[0]);
        final boolean[] checkedItems = new boolean[initialCheckedStates.size()];
        for(int i = 0; i < initialCheckedStates.size(); i++) {
            checkedItems[i] = initialCheckedStates.get(i);
        }

        new AlertDialog.Builder(this, R.style.AlertDialogTheme)
                .setTitle("Select Apps to Lock (" + items.length + " Found)")
                .setMultiChoiceItems(items, checkedItems, (dialog, which, isChecked) -> {
                    checkedItems[which] = isChecked;
                })
                .setPositiveButton("Done", (dialog, which) -> {
                    for (int i = 0; i < items.length; i++) {
                        String pkg = packageNames.get(i);
                        mLockedAppMap.put(pkg, checkedItems[i]);
                    }
                    Toast.makeText(this, "List updated. Click 'Save Settings' to apply.", Toast.LENGTH_LONG).show();
                    populateAppList();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // --- *** THIS IS THE FIX *** ---
    private void saveAllSettings() {
        final String currentPin = mEditPinCurrent.getText().toString();
        final String newPin = mEditPinNew.getText().toString();
        final String confirmPin = mEditPinConfirm.getText().toString();

        // Check if the user is trying to change their PIN
        boolean isChangingPin = !newPin.isEmpty() || !confirmPin.isEmpty() || (!mMustCreatePin && !currentPin.isEmpty());

        if (mMustCreatePin) {
            // --- CASE C: Must create PIN ---
            if (TextUtils.isEmpty(newPin) || newPin.length() != 4) {
                mEditPinNew.setError("PIN must be 4 digits");
                return;
            }
            if (!newPin.equals(confirmPin)) {
                mEditPinConfirm.setError("PINs do not match");
                return;
            }
            // Save the new PIN to the parent, which will then redirect
            saveParentPin(newPin, true); // true = redirect to Welcome

        } else if (isChangingPin) {
            // --- CASE B: User is updating their PIN ---
            if (TextUtils.isEmpty(currentPin)) {
                mEditPinCurrent.setError("Current PIN is required to change it");
                return;
            }
            if (TextUtils.isEmpty(newPin) || newPin.length() != 4) {
                mEditPinNew.setError("New PIN must be 4 digits");
                return;
            }
            if (!newPin.equals(confirmPin)) {
                mEditPinConfirm.setError("PINs do not match");
                return;
            }

            // Verify the parent's CURRENT pin.
            if (mParentPin != null && mParentPin.equals(currentPin)) {
                // Current PIN is correct. Save all settings.
                saveParentPin(newPin, false); // Save to parent
                saveChildSettings(newPin);    // Save to child
            } else {
                mEditPinCurrent.setError("Incorrect Current PIN");
            }

        } else {
            // --- CASE A: User is only changing toggles ---
            // The PIN fields are all empty.
            Log.d(TAG, "Saving toggle settings only.");
            saveChildSettings(null); // Pass 'null' to indicate the PIN is not changing
        }
    }

    // Saves the PIN to the PARENT's document
    private void saveParentPin(String newPin, boolean redirectToWelcome) {
        Map<String, Object> pinData = new HashMap<>();
        pinData.put("securityPin", newPin);

        mStore.collection("users").document(mParentId)
                .set(pinData, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "PIN Saved!", Toast.LENGTH_SHORT).show();
                    mParentPin = newPin; // Update our local copy
                    if (redirectToWelcome) {
                        // PIN is created. Send them back to Welcome to be re-checked.
                        goToActivity(WelcomeActivity.class);
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to save PIN.", Toast.LENGTH_SHORT).show();
                });
    }

    // Saves the settings to the CHILD's document
    private void saveChildSettings(String pinToSave) {
        Map<String, Object> childSettings = new HashMap<>();
        childSettings.put("uninstallLock", mSwitchUninstall.isChecked());
        childSettings.put("settingsLock", mSwitchSettingsLock.isChecked());

        Map<String, Boolean> appsToLock = new HashMap<>();
        for (Map.Entry<String, Boolean> entry : mLockedAppMap.entrySet()) {
            if (entry.getValue() == Boolean.TRUE) {
                appsToLock.put(entry.getKey(), true);
            }
        }
        childSettings.put("lockedAppMap", appsToLock);

        // --- THIS IS THE KEY FIX ---
        // If we are passing a new PIN, use it.
        // If not (pinToSave is null), use the master PIN we fetched in onCreate (mParentPin).
        if (pinToSave != null && !pinToSave.isEmpty()) {
            childSettings.put("securityPin", pinToSave);
        } else {
            childSettings.put("securityPin", mParentPin);
        }
        // --- END OF FIX ---

        mStore.collection("users").document(mSelectedChildId)
                .set(childSettings, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Child's settings saved!", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to save child's settings.", Toast.LENGTH_SHORT).show();
                });
    }
    // --- *** END OF FIX *** ---

    private void goToLogin() {
        Intent intent = new Intent(ParentalControlsActivity.this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void goToActivity(Class<?> activityClass) {
        Intent intent = new Intent(this, activityClass);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}