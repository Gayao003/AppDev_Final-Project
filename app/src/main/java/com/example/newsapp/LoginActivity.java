package com.example.newsapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

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

public class LoginActivity extends AppCompatActivity {
    private static final String TAG = "LoginActivity";
    
    private EditText etEmail, etPassword;
    private Button btnLogin, btnGoogleSignIn;
    private TextView tvRegisterPrompt, tvForgotPassword, tvSignUp;
    private ImageButton btnBack, btnTogglePassword;
    
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private GoogleSignInClient mGoogleSignInClient;
    
    private ActivityResultLauncher<Intent> signInLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_login);
            
            // Initialize Firebase Auth
            mAuth = FirebaseAuth.getInstance();
            
            // Initialize Firestore
            db = FirebaseFirestore.getInstance();
            
            // Initialize views
            initializeViews();
            
            // Configure Google Sign In
            configureGoogleSignIn();
            
            // Set click listeners
            setClickListeners();
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate: ", e);
            Toast.makeText(this, "Error initializing app: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private void initializeViews() {
        etEmail = findViewById(R.id.etLoginEmail);
        etPassword = findViewById(R.id.etLoginPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn);
        tvRegisterPrompt = findViewById(R.id.tvRegisterPrompt);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        btnBack = findViewById(R.id.btnBack);
        btnTogglePassword = findViewById(R.id.btnTogglePassword);
        tvSignUp = findViewById(R.id.tvSignUp);
    }
    
    @Override
    protected void onStart() {
        super.onStart();
        // Check if user is signed in
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            // User is already signed in, go to MainActivity
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            finish();
        }
    }
    
    private void loginUser() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        
        // Validate input
        if (email.isEmpty()) {
            etEmail.setError("Email is required");
            etEmail.requestFocus();
            return;
        }
        
        if (password.isEmpty()) {
            etPassword.setError("Password is required");
            etPassword.requestFocus();
            return;
        }
        
        // Sign in with email and password
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        // Sign in success
                        Log.d(TAG, "signInWithEmail:success");
                        FirebaseUser user = mAuth.getCurrentUser();
                        startActivity(new Intent(LoginActivity.this, MainActivity.class));
                        finish();
                    } else {
                        // If sign in fails, display a message to the user
                        Log.w(TAG, "signInWithEmail:failure", task.getException());
                        Toast.makeText(LoginActivity.this, "Authentication failed: " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }
    
    private void signInWithGoogle() {
        // Show loading indicator
        // progressBar.setVisibility(View.VISIBLE);
        
        // Sign out from Google first to force the account picker to show
        mGoogleSignInClient.signOut().addOnCompleteListener(this, task -> {
            // Hide loading indicator
            // progressBar.setVisibility(View.GONE);
            
            // Now start the sign-in flow
            Intent signInIntent = mGoogleSignInClient.getSignInIntent();
            signInLauncher.launch(signInIntent);
        });
    }
    
    private void firebaseAuthWithGoogle(String idToken) {
        // Show a loading indicator
        // You could add a ProgressBar in your layout and show it here
        
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    // Hide loading indicator
                    
                    if (task.isSuccessful()) {
                        // Sign in success
                        Log.d(TAG, "signInWithCredential:success");
                        FirebaseUser user = mAuth.getCurrentUser();
                        
                        // Check if this is a new user
                        boolean isNewUser = task.getResult().getAdditionalUserInfo().isNewUser();
                        if (isNewUser) {
                            // Save user data to Firestore for new users
                            saveUserToFirestore(user);
                        } else {
                            // Update last login timestamp for existing users
                            updateUserLastLogin(user.getUid());
                        }
                        
                        startActivity(new Intent(LoginActivity.this, MainActivity.class));
                        finish();
                    } else {
                        // If sign in fails, display a message to the user
                        Log.w(TAG, "signInWithCredential:failure", task.getException());
                        Toast.makeText(LoginActivity.this, "Authentication failed: " + 
                                (task.getException() != null ? task.getException().getMessage() : "Unknown error"),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }
    
    private void saveUserToFirestore(FirebaseUser user) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("email", user.getEmail());
        userData.put("displayName", user.getDisplayName());
        userData.put("photoUrl", user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : null);
        userData.put("createdAt", System.currentTimeMillis());
        userData.put("lastLogin", System.currentTimeMillis());
        userData.put("provider", "google");
        
        db.collection("users").document(user.getUid())
                .set(userData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "User data saved to Firestore");
                    Toast.makeText(LoginActivity.this, "Account data saved successfully", 
                            Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error saving user data", e);
                    Toast.makeText(LoginActivity.this, 
                            "Failed to save account data: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void updateUserLastLogin(String userId) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("lastLogin", System.currentTimeMillis());
        
        db.collection("users").document(userId)
                .update(updates)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "User last login updated"))
                .addOnFailureListener(e -> Log.w(TAG, "Error updating last login", e));
    }

    private void saveUserPreferences(String userId) {
        // Create default user preferences
        Map<String, Object> preferences = new HashMap<>();
        preferences.put("notificationsEnabled", true);
        preferences.put("darkModeEnabled", false);
        preferences.put("preferredCategories", new String[]{"general", "technology"});
        
        db.collection("users").document(userId)
                .collection("preferences").document("settings")
                .set(preferences)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "User preferences saved"))
                .addOnFailureListener(e -> Log.w(TAG, "Error saving user preferences", e));
    }

    private void configureGoogleSignIn() {
        try {
            // Log the web client ID to debug
            String webClientId = getString(R.string.default_web_client_id);
            Log.d(TAG, "Web Client ID: " + (webClientId != null ? webClientId : "null"));
            
            // Configure Google Sign In
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(webClientId)
                    .requestEmail()
                    .build();
            
            mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
            
            // Set up ActivityResultLauncher for Google Sign-In
            signInLauncher = registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK) {
                            Intent data = result.getData();
                            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
                            try {
                                // Google Sign In was successful, authenticate with Firebase
                                GoogleSignInAccount account = task.getResult(ApiException.class);
                                Log.d(TAG, "firebaseAuthWithGoogle:" + account.getId());
                                firebaseAuthWithGoogle(account.getIdToken());
                            } catch (ApiException e) {
                                // Google Sign In failed
                                Log.w(TAG, "Google sign in failed", e);
                                Toast.makeText(LoginActivity.this, "Google Sign-In failed: " + e.getMessage(),
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error configuring Google Sign-In: ", e);
            Toast.makeText(this, "Error configuring Google Sign-In: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void setClickListeners() {
        // Set click listeners
        btnLogin.setOnClickListener(v -> loginUser());
        
        btnGoogleSignIn.setOnClickListener(v -> signInWithGoogle());
        
        tvRegisterPrompt.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(intent);
        });

        tvForgotPassword.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, ForgotPasswordActivity.class);
            // Pass the email if it's already entered
            if (!etEmail.getText().toString().trim().isEmpty()) {
                intent.putExtra("email", etEmail.getText().toString().trim());
            }
            startActivity(intent);
        });

        btnBack.setOnClickListener(v -> {
            // Handle back button - usually just finish()
            finish();
        });

        btnTogglePassword.setOnClickListener(v -> {
            // Toggle password visibility
            if (etPassword.getInputType() == (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)) {
                // Show password
                etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                btnTogglePassword.setImageResource(R.drawable.ic_visibility);
            } else {
                // Hide password
                etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                btnTogglePassword.setImageResource(R.drawable.ic_visibility_off);
            }
            // Move cursor to end of text
            etPassword.setSelection(etPassword.getText().length());
        });

        tvSignUp.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(intent);
        });
    }
}