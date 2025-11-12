package com.example.focus;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.journeyapps.barcodescanner.BarcodeEncoder;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class LinkAccountActivity extends AppCompatActivity {

    private static final String TAG = "LinkAccountActivity";

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore mStore;
    private FirebaseUser mCurrentUser;
    private String mCurrentUserId;
    private String mUserRole;

    // UI Elements
    private Toolbar mToolbar;
    private TextView mTextLinkCode, mTextNoAccountsLinked;
    private ImageView mImageQRCode;
    private Button mButtonCopyCode, mButtonLinkAccount;
    private EditText mEditTextLinkCode;
    private LinearLayout mLinkedAccountsContainer;
    private LayoutInflater mInflater;
    private CardView mCardScanQR;

    // Real-time listener for the current user's document
    private ListenerRegistration mUserDocListener;

    // QR Code Scanner Launcher
    private final ActivityResultLauncher<ScanOptions> qrCodeScannerLauncher = registerForActivityResult(new ScanContract(),
            result -> {
                if(result.getContents() == null) {
                    Toast.makeText(this, "Scan cancelled", Toast.LENGTH_LONG).show();
                } else {
                    String scannedCode = result.getContents().toUpperCase();
                    mEditTextLinkCode.setText(scannedCode);
                    Toast.makeText(this, "Code Scanned! Linking...", Toast.LENGTH_SHORT).show();
                    linkAccount();
                }
            });


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_link_account);

        // --- Initialize Firebase ---
        mAuth = FirebaseAuth.getInstance();
        mStore = FirebaseFirestore.getInstance();
        mCurrentUser = mAuth.getCurrentUser();

        if (mCurrentUser == null) {
            goToLogin();
            return;
        }
        mCurrentUserId = mCurrentUser.getUid();

        // Inflater for adding views
        mInflater = LayoutInflater.from(this);

        // --- Toolbar Setup ---
        mToolbar = findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        mToolbar.setNavigationOnClickListener(v -> onBackPressed());

        // --- UI Initialization ---
        mTextLinkCode = findViewById(R.id.textLinkCode);
        mImageQRCode = findViewById(R.id.imageQRCode);
        mButtonCopyCode = findViewById(R.id.buttonCopyCode);
        mButtonLinkAccount = findViewById(R.id.buttonLinkAccount);
        mEditTextLinkCode = findViewById(R.id.editTextLinkCode);
        mLinkedAccountsContainer = findViewById(R.id.linkedAccountsContainer);
        mTextNoAccountsLinked = findViewById(R.id.textNoAccountsLinked);
        mCardScanQR = findViewById(R.id.cardScanQR);

        // --- Set Listeners ---
        mButtonCopyCode.setOnClickListener(v -> copyLinkCode());
        mButtonLinkAccount.setOnClickListener(v -> linkAccount());
        mCardScanQR.setOnClickListener(v -> launchScanner());

        // --- Load Data ---
        setupRealTimeListener();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Detach the real-time listener to prevent memory leaks
        if (mUserDocListener != null) {
            mUserDocListener.remove();
        }
    }

    // --- REAL-TIME FIX ---
    private void setupRealTimeListener() {
        // Listen to the current user's document for real-time changes
        mUserDocListener = mStore.collection("users").document(mCurrentUserId)
                .addSnapshotListener((documentSnapshot, error) -> {
                    if (error != null) {
                        Log.w(TAG, "User document listener failed.", error);
                        return;
                    }
                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        processUserData(documentSnapshot);
                    }
                });
    }

    private void processUserData(DocumentSnapshot documentSnapshot) {
        mUserRole = documentSnapshot.getString("role");

        TextView toolbarSubtitle = findViewById(R.id.toolbarSubtitle);
        if ("Student".equals(mUserRole)) {
            toolbarSubtitle.setText("Connect parent's account");
        } else {
            toolbarSubtitle.setText("Connect student's account");
        }

        // Check for existing link code
        String linkCode = documentSnapshot.getString("linkCode");
        if (linkCode == null || linkCode.isEmpty()) {
            generateAndSaveLinkCode();
        } else {
            displayLinkCode(linkCode);
        }

        // Load and display linked accounts (this will also be real-time thanks to the fetch in this method)
        loadLinkedAccounts();
    }
    // --- END REAL-TIME FIX ---

    private void launchScanner() {
        ScanOptions options = new ScanOptions();
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
        options.setPrompt("Scan a FocusGuard QR Code");
        options.setCameraId(0);
        options.setBeepEnabled(true);
        options.setBarcodeImageEnabled(false);
        // FIX: Force to portrait to avoid crash/layout issues
        options.setOrientationLocked(true);
        qrCodeScannerLauncher.launch(options);
    }

    private void generateAndSaveLinkCode() {
        String baseCode = UUID.randomUUID().toString().substring(0, 11).toUpperCase();
        final String finalCode = baseCode.substring(0, 3) + "-" + baseCode.substring(4, 7) + "-" + baseCode.substring(8, 11);

        mStore.collection("users").document(mCurrentUserId)
                .update("linkCode", finalCode)
                .addOnSuccessListener(aVoid -> displayLinkCode(finalCode))
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to create link code.", Toast.LENGTH_SHORT).show());
    }

    private void displayLinkCode(String linkCode) {
        mTextLinkCode.setText(linkCode);

        MultiFormatWriter writer = new MultiFormatWriter();
        try {
            BitMatrix bitMatrix = writer.encode(linkCode, BarcodeFormat.QR_CODE, 500, 500);
            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
            Bitmap bitmap = barcodeEncoder.createBitmap(bitMatrix);
            mImageQRCode.setImageBitmap(bitmap);
        } catch (WriterException e) {
            Log.w(TAG, "QR Code generation failed", e);
        }
    }

    private void copyLinkCode() {
        String code = mTextLinkCode.getText().toString();
        if (code.equals("Loading...")) return;

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("LinkCode", code);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "Code copied to clipboard!", Toast.LENGTH_SHORT).show();
    }

    private void linkAccount() {
        String enteredCode = mEditTextLinkCode.getText().toString().trim().toUpperCase();
        if (TextUtils.isEmpty(enteredCode)) {
            mEditTextLinkCode.setError("Please enter a code.");
            return;
        }

        mStore.collection("users")
                .whereEqualTo("linkCode", enteredCode)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        Toast.makeText(this, "Invalid link code. Please try again.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    DocumentSnapshot otherUserDoc = querySnapshot.getDocuments().get(0);
                    String otherUserId = otherUserDoc.getId();
                    String otherUserRole = otherUserDoc.getString("role");

                    if (mCurrentUserId.equals(otherUserId)) {
                        Toast.makeText(this, "You cannot link your own account.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (mUserRole.equals(otherUserRole)) {
                        Toast.makeText(this, "Cannot link two " + mUserRole + " accounts.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String studentId, parentId;
                    if ("Student".equals(mUserRole)) {
                        studentId = mCurrentUserId;
                        parentId = otherUserId;
                    } else {
                        studentId = otherUserId;
                        parentId = mCurrentUserId;
                    }

                    WriteBatch batch = mStore.batch();

                    DocumentReference studentRef = mStore.collection("users").document(studentId);
                    batch.update(studentRef, "linkedParentId", parentId);

                    DocumentReference parentRef = mStore.collection("users").document(parentId);
                    batch.update(parentRef, "linkedChildIds", FieldValue.arrayUnion(studentId));

                    batch.commit().addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Accounts linked successfully!", Toast.LENGTH_SHORT).show();
                        mEditTextLinkCode.setText("");
                        // The real-time listener (mUserDocListener) will now refresh loadLinkedAccounts()
                    }).addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to link accounts.", Toast.LENGTH_SHORT).show();
                    });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error finding code.", Toast.LENGTH_SHORT).show();
                });
    }

    private void loadLinkedAccounts() {
        // Clear old views (except the "Linked Accounts" title)
        if (mLinkedAccountsContainer.getChildCount() > 1) {
            mLinkedAccountsContainer.removeViews(1, mLinkedAccountsContainer.getChildCount() - 1);
        }

        mTextNoAccountsLinked.setVisibility(View.VISIBLE);

        // Find linked accounts
        if ("Student".equals(mUserRole)) {
            // Student: Find your linkedParentId and fetch that user
            mStore.collection("users").document(mCurrentUserId).get()
                    .addOnSuccessListener(studentDoc -> {
                        String parentId = studentDoc.getString("linkedParentId");
                        if (parentId != null && !parentId.isEmpty()) {
                            mStore.collection("users").document(parentId).get()
                                    .addOnSuccessListener(this::addLinkedAccountView);
                        }
                    });
        } else {
            // Parent: Fetch the linkedChildIds array from the parent's document
            mStore.collection("users").document(mCurrentUserId).get()
                    .addOnSuccessListener(parentDoc -> {
                        List<String> childIds = (List<String>) parentDoc.get("linkedChildIds");
                        if (childIds != null && !childIds.isEmpty()) {
                            // Fetch each child's details
                            for (String childId : childIds) {
                                mStore.collection("users").document(childId).get()
                                        .addOnSuccessListener(this::addLinkedAccountView);
                            }
                        }
                    });
        }
    }

    private void addLinkedAccountView(DocumentSnapshot userDoc) {
        if (!userDoc.exists()) return; // Failsafe

        mTextNoAccountsLinked.setVisibility(View.GONE);

        String firstName = userDoc.getString("firstName");
        String lastName = userDoc.getString("lastName");
        String name = (firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "");
        String role = userDoc.getString("role");
        String userId = userDoc.getId();

        // Check if this user is already displayed to prevent duplication
        for (int i = 1; i < mLinkedAccountsContainer.getChildCount(); i++) {
            if (mLinkedAccountsContainer.getChildAt(i).getTag() != null && mLinkedAccountsContainer.getChildAt(i).getTag().equals(userId)) {
                return;
            }
        }

        View accountView = mInflater.inflate(R.layout.item_linked_account, mLinkedAccountsContainer, false);
        accountView.setTag(userId); // Use userId as tag for identification

        TextView textName = accountView.findViewById(R.id.textLinkedName);
        TextView textRole = accountView.findViewById(R.id.textLinkedRole);
        TextView textUnlink = accountView.findViewById(R.id.textUnlink);

        textName.setText(name);
        textRole.setText((role != null ? role : "Unknown") + " Account");

        textUnlink.setOnClickListener(v -> showUnlinkConfirmation(userId, name));

        mLinkedAccountsContainer.addView(accountView);
    }

    private void showUnlinkConfirmation(String otherUserId, String name) {
        new AlertDialog.Builder(this, R.style.AlertDialogTheme)
                .setTitle("Unlink Account")
                .setMessage("Are you sure you want to unlink from " + name + "? This action cannot be undone.")
                .setPositiveButton("Unlink", (dialog, which) -> unlinkAccount(otherUserId))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void unlinkAccount(String otherUserId) {
        String studentId, parentId;
        // Determine which ID belongs to the student and which belongs to the parent
        if ("Student".equals(mUserRole)) {
            studentId = mCurrentUserId; // Current user is student
            parentId = otherUserId;     // Other user is parent
        } else {
            studentId = otherUserId;    // Other user is student
            parentId = mCurrentUserId;  // Current user is parent
        }

        WriteBatch batch = mStore.batch();

        // 1. Update Student: remove linkedParentId (if it exists)
        DocumentReference studentRef = mStore.collection("users").document(studentId);
        batch.update(studentRef, "linkedParentId", FieldValue.delete());

        // 2. Update Parent: remove studentId from linkedChildIds array
        DocumentReference parentRef = mStore.collection("users").document(parentId);
        batch.update(parentRef, "linkedChildIds", FieldValue.arrayRemove(studentId));

        batch.commit().addOnSuccessListener(aVoid -> {
            Toast.makeText(this, "Account unlinked successfully.", Toast.LENGTH_SHORT).show();
            // The real-time listener will handle the UI refresh (loadLinkedAccounts)
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed to unlink.", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Unlink failed", e);
        });
    }

    private void goToLogin() {
        Intent intent = new Intent(LinkAccountActivity.this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}