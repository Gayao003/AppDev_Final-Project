package com.example.newsapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    
    private static final String TAG = "MainActivity";
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    private GoogleSignInClient mGoogleSignInClient;
    private ActivityResultLauncher<Intent> linkAccountLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        
        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        
        // Check if user is logged in
        if (currentUser == null) {
            // User not logged in, redirect to login
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
            return;
        }
        
        // User is logged in, display welcome message
        TextView tvWelcome = findViewById(R.id.tvWelcome);
        tvWelcome.setText("Welcome, " + currentUser.getDisplayName());
        
        // Add logout button functionality
        Button btnLogout = findViewById(R.id.btnLogout);
        btnLogout.setOnClickListener(v -> {
            mAuth.signOut();
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
        });
        
        // Initialize Firestore
        db = FirebaseFirestore.getInstance();
        
        // Configure Google Sign In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
        
        // Set up ActivityResultLauncher for Google Sign-In account linking
        linkAccountLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        Intent data = result.getData();
                        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
                        try {
                            // Google Sign In was successful, link with Firebase
                            GoogleSignInAccount account = task.getResult(ApiException.class);
                            Log.d(TAG, "Google account selected for linking: " + account.getId());
                            linkGoogleAccount(account.getIdToken());
                        } catch (ApiException e) {
                            // Google Sign In failed
                            Log.w(TAG, "Google sign in failed", e);
                            Toast.makeText(MainActivity.this, "Google Sign-In failed: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
        
        // Add account linking button
        addAccountLinkingButton();
        
        // Verify Firebase connection
        if (mAuth != null) {
            Log.d(TAG, "Firebase Auth initialized successfully");
        } else {
            Log.e(TAG, "Firebase Auth initialization failed");
        }
        
        // Verify Firestore connection
        if (db != null) {
            Log.d(TAG, "Firestore initialized successfully");
            
            // Test Firestore by fetching a document
            db.collection("articles").limit(1).get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        Log.d(TAG, "Successfully retrieved data from Firestore");
                    } else {
                        Log.d(TAG, "No documents found in articles collection");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error getting documents: ", e);
                });
        } else {
            Log.e(TAG, "Firestore initialization failed");
        }
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }
    
    private void addAccountLinkingButton() {
        // Check if Google provider is already linked
        boolean isGoogleLinked = false;
        if (currentUser != null && currentUser.getProviderData() != null) {
            for (com.google.firebase.auth.UserInfo profile : currentUser.getProviderData()) {
                if (GoogleAuthProvider.PROVIDER_ID.equals(profile.getProviderId())) {
                    isGoogleLinked = true;
                    break;
                }
            }
        }
        
        // Only show the button if Google is not already linked
        if (!isGoogleLinked) {
            // Find the container where you want to add the button
            ViewGroup container = findViewById(R.id.main);
            
            // Create a new button
            Button btnLinkAccount = new Button(this);
            btnLinkAccount.setText("Link Google Account");
            btnLinkAccount.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            
            // Add the button to the container
            container.addView(btnLinkAccount);
            
            // Set click listener
            btnLinkAccount.setOnClickListener(v -> startGoogleSignInForLinking());
        }
    }
    
    private void startGoogleSignInForLinking() {
        // Sign out from Google first to force the account picker to show
        mGoogleSignInClient.signOut().addOnCompleteListener(this, task -> {
            // Start the Google Sign In flow
            Intent signInIntent = mGoogleSignInClient.getSignInIntent();
            linkAccountLauncher.launch(signInIntent);
        });
    }
    
    private void linkGoogleAccount(String idToken) {
        // Get Google Auth credential
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        
        // Link the credential to the current user account
        currentUser.linkWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "linkWithCredential:success");
                        // Update the current user reference
                        currentUser = mAuth.getCurrentUser();
                        
                        // Update user data in Firestore to reflect the linked account
                        updateUserDataAfterLinking();
                        
                        Toast.makeText(MainActivity.this, 
                                "Google account linked successfully", 
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Log.w(TAG, "linkWithCredential:failure", task.getException());
                        
                        // Handle specific error cases
                        if (task.getException().getMessage().contains("already linked")) {
                            Toast.makeText(MainActivity.this, 
                                    "This Google account is already linked to your profile", 
                                    Toast.LENGTH_SHORT).show();
                        } else if (task.getException().getMessage().contains("already exists")) {
                            Toast.makeText(MainActivity.this, 
                                    "This Google account is already used by another user", 
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(MainActivity.this, 
                                    "Failed to link Google account: " + task.getException().getMessage(), 
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }
    
    private void updateUserDataAfterLinking() {
        // Update the user document to indicate multiple auth providers
        Map<String, Object> updates = new HashMap<>();
        updates.put("linkedProviders", "google");
        
        db.collection("users").document(currentUser.getUid())
                .update(updates)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "User data updated after linking"))
                .addOnFailureListener(e -> Log.w(TAG, "Error updating user data after linking", e));
    }
}