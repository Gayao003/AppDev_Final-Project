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
import androidx.appcompat.app.AlertDialog;
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
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.example.newsapp.ui.common.NavbarActivity;

import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {
    private static final String TAG = "LoginActivity";
    
    private EditText etEmail, etPassword;
    private Button btnLogin, btnGoogleSignIn;
    private TextView tvRegisterPrompt, tvForgotPassword, tvSignUp;
    private ImageButton btnBack;
    
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
        tvSignUp = findViewById(R.id.tvSignUp);
    }
    
    @Override
    protected void onStart() {
        super.onStart();
        // Check if user is signed in
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            startActivity(new Intent(LoginActivity.this, NavbarActivity.class));
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

        // Directly proceed with sign-in attempt
        proceedWithSignIn(email, password);
    }
    
    private void showNoAccountDialog(String email) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Account Not Found");
        builder.setMessage("No account exists with email: " + email + 
            "\nWould you like to create a new account?");
        builder.setPositiveButton("Register", (dialog, which) -> {
            // Navigate to registration
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            intent.putExtra("email", email); // Pre-fill email in registration
            startActivity(intent);
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
    
    private void proceedWithSignIn(String email, String password) {
        mAuth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this, task -> {
                if (task.isSuccessful()) {
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user.isEmailVerified()) {
                        updateEmailVerificationStatus(user);
                        startActivity(new Intent(LoginActivity.this, NavbarActivity.class));
                        finish();
                    } else {
                        showVerificationDialog(user);
                        mAuth.signOut();
                    }
                } else {
                    // If email/password sign-in fails, check if this email uses Google Sign-In
                    mAuth.fetchSignInMethodsForEmail(email)
                        .addOnCompleteListener(methodsTask -> {
                            if (methodsTask.isSuccessful() && methodsTask.getResult() != null) {
                                boolean hasGoogleProvider = false;
                                for (String provider : methodsTask.getResult().getSignInMethods()) {
                                    if (provider.equals(GoogleAuthProvider.PROVIDER_ID)) {
                                        hasGoogleProvider = true;
                                        break;
                                    }
                                }
                                
                                if (hasGoogleProvider) {
                                    showGoogleSignInPrompt(email);
                                } else {
                                    String errorMessage = "Invalid email or password";
                                    if (task.getException() instanceof FirebaseAuthInvalidCredentialsException) {
                                        errorMessage = "Invalid password. Please try again.";
                                    }
                                    Toast.makeText(LoginActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                Toast.makeText(LoginActivity.this, 
                                    "Authentication failed. Please try again.", 
                                    Toast.LENGTH_SHORT).show();
                            }
                        });
                }
            });
    }
    
    private void showGoogleSignInPrompt(String email) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Google Sign-In Required");
        builder.setMessage("This account is linked to Google. Please sign in with Google instead.");
        builder.setPositiveButton("Sign in with Google", (dialog, which) -> {
            signInWithGoogle();
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
    
    private void performEmailPasswordSignIn(String email, String password) {
        mAuth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this, task -> {
                if (task.isSuccessful()) {
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user.isEmailVerified()) {
                        updateEmailVerificationStatus(user);
                        startActivity(new Intent(LoginActivity.this, NavbarActivity.class));
                        finish();
                    } else {
                        showVerificationDialog(user);
                        mAuth.signOut();
                    }
                } else {
                    String errorMessage = "Authentication failed";
                    if (task.getException() != null) {
                        if (task.getException() instanceof FirebaseAuthInvalidCredentialsException) {
                            errorMessage = "Invalid password. Please try again.";
                        } else {
                            errorMessage = task.getException().getMessage();
                        }
                    }
                    Toast.makeText(LoginActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                }
            });
    }
    
    private void showVerificationDialog(FirebaseUser user) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Email Verification Required");
        builder.setMessage("Please verify your email address first. Check your inbox for the verification link.");
        builder.setPositiveButton("Resend Email", (dialog, which) -> {
            sendVerificationEmail(user);
        });
        builder.setNegativeButton("OK", null);
        builder.show();
    }
    
    private void sendVerificationEmail(FirebaseUser user) {
        user.sendEmailVerification()
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Toast.makeText(LoginActivity.this,
                        "Verification email sent to " + user.getEmail(),
                        Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(LoginActivity.this,
                        "Failed to send verification email: " + task.getException().getMessage(),
                        Toast.LENGTH_LONG).show();
                }
            });
    }
    
    private void updateEmailVerificationStatus(FirebaseUser user) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("emailVerified", true);
        
        db.collection("users").document(user.getUid())
            .update(updates)
            .addOnSuccessListener(aVoid -> Log.d(TAG, "Email verification status updated"))
            .addOnFailureListener(e -> Log.w(TAG, "Error updating email verification status", e));
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
                if (task.isSuccessful()) {
                    FirebaseUser user = mAuth.getCurrentUser();
                    // Proceed with user login
                    Log.d(TAG, "signInWithCredential:success");
                    // Check if this is a new user
                    boolean isNewUser = task.getResult().getAdditionalUserInfo().isNewUser();
                    if (isNewUser) {
                        // Save user data to Firestore for new users
                        saveUserToFirestore(user);
                    } else {
                        // Update last login timestamp for existing users
                        updateUserLastLogin(user.getUid());
                    }
                    
                    startActivity(new Intent(LoginActivity.this, NavbarActivity.class));
                    finish();
                } else {
                    // Handle sign-in failure
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
        userData.put("emailVerified", true);
        
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
            // Optionally pass the email if it's already entered
            if (!etEmail.getText().toString().trim().isEmpty()) {
                intent.putExtra("email", etEmail.getText().toString().trim());
            }
            startActivity(intent);
        });

        btnBack.setOnClickListener(v -> {
            // Handle back button - usually just finish()
            finish();
        });

        tvSignUp.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(intent);
        });
    }
}