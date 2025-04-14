package com.example.newsapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.auth.UserProfileChangeRequest;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {
    private static final String TAG = "RegisterActivity";
    
    private EditText etName, etEmail, etPhone, etAge, etPassword, etConfirmPassword;
    private CheckBox cbTerms;
    private Button btnNext;
    private ImageButton btnBack;
    private TextView tvLoginPrompt, tvStepIndicator;
    private TextView tvTermsLink, tvPrivacyLink;
    
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        
        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();
        
        // Initialize Firestore
        db = FirebaseFirestore.getInstance();
        
        // Initialize views
        etName = findViewById(R.id.etRegisterName);
        etEmail = findViewById(R.id.etRegisterEmail);
        etPhone = findViewById(R.id.etRegisterPhone);
        etAge = findViewById(R.id.etRegisterAge);
        cbTerms = findViewById(R.id.cbTerms);
        btnNext = findViewById(R.id.btnNext);
        btnBack = findViewById(R.id.btnBack);
        tvLoginPrompt = findViewById(R.id.tvLoginPrompt);
        tvStepIndicator = findViewById(R.id.tvStepIndicator);
        etPassword = findViewById(R.id.etRegisterPassword);
        etConfirmPassword = findViewById(R.id.etRegisterConfirmPassword);
        tvTermsLink = findViewById(R.id.tvTermsLink);
        tvPrivacyLink = findViewById(R.id.tvPrivacyLink);
        
        tvStepIndicator.setText("Register");
        
        // Check if email was passed from login screen
        String prefillEmail = getIntent().getStringExtra("email");
        if (prefillEmail != null && !prefillEmail.isEmpty()) {
            etEmail.setText(prefillEmail);
        }
        
        // Set click listeners
        btnNext.setOnClickListener(v -> validateAndProceed());
        
        btnBack.setOnClickListener(v -> finish());
        
        tvLoginPrompt.setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });
        
        // Add Terms and Privacy Policy link listeners
        tvTermsLink.setOnClickListener(v -> showTermsDialog());
        tvPrivacyLink.setOnClickListener(v -> showPrivacyDialog());
    }
    
    private void validateAndProceed() {
        String name = etName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String ageStr = etAge.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();
        
        // Validate input
        if (name.isEmpty()) {
            etName.setError("Name is required");
            etName.requestFocus();
            return;
        }
        
        if (email.isEmpty()) {
            etEmail.setError("Email is required");
            etEmail.requestFocus();
            return;
        }
        
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Please enter a valid email");
            etEmail.requestFocus();
            return;
        }
        
        if (!ageStr.isEmpty()) {
            try {
                int age = Integer.parseInt(ageStr);
                if (age < 13) {
                    etAge.setError("You must be at least 13 years old");
                    etAge.requestFocus();
                    return;
                }
            } catch (NumberFormatException e) {
                etAge.setError("Please enter a valid age");
                etAge.requestFocus();
                return;
            }
        }
        
        if (!cbTerms.isChecked()) {
            Toast.makeText(this, "You must accept the Terms of Service", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Add password validation
        if (password.isEmpty()) {
            etPassword.setError("Password is required");
            etPassword.requestFocus();
            return;
        }
        
        if (password.length() < 6) {
            etPassword.setError("Password must be at least 6 characters");
            etPassword.requestFocus();
            return;
        }
        
        if (!password.equals(confirmPassword)) {
            etConfirmPassword.setError("Passwords do not match");
            etConfirmPassword.requestFocus();
            return;
        }
        
        // Check if email already exists
        mAuth.fetchSignInMethodsForEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        if (task.getResult().getSignInMethods() != null && 
                                !task.getResult().getSignInMethods().isEmpty()) {
                            // Email exists
                            etEmail.setError("Email already in use");
                            etEmail.requestFocus();
                        } else {
                            // Email is available, proceed to step 2
                            proceedToStep2(name, email, phone, ageStr, password);
                        }
                    } else {
                        Log.w(TAG, "Error checking email existence", task.getException());
                        Toast.makeText(RegisterActivity.this, 
                                "Error checking email: " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }
    
    private void proceedToStep2(String name, String email, String phone, String age, String password) {
        // Store user data in SharedPreferences
        getSharedPreferences("NewsAppPrefs", MODE_PRIVATE)
                .edit()
                .putString("registration_name", name)
                .putString("registration_email", email)
                .putString("registration_phone", phone)
                .putString("registration_age", age)
                .putString("registration_password", password)
                .apply();
        
        // Send verification email and create account
        sendOtpToEmail(email);
    }
    
    private void sendOtpToEmail(String email) {
        // We'll create the user account first, then send verification email
        String password = getSharedPreferences("NewsAppPrefs", MODE_PRIVATE)
                .getString("registration_password", "");
        
        // Show loading indicator
        btnNext.setEnabled(false);
        btnNext.setText("Creating Account...");
        
        // Create user with email and password
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    btnNext.setEnabled(true);
                    btnNext.setText("Register");
                    
                    if (task.isSuccessful()) {
                        // Sign in success, update UI with the signed-in user's information
                        Log.d(TAG, "createUserWithEmail:success");
                        FirebaseUser user = mAuth.getCurrentUser();
                        
                        // Send verification email
                        sendEmailVerification(user);
                        
                        // Save additional user data to Firestore
                        saveUserToFirestore(user);
                    } else {
                        // If sign in fails, display a message to the user
                        Log.w(TAG, "createUserWithEmail:failure", task.getException());
                        Toast.makeText(RegisterActivity.this, "Registration failed: " + 
                                task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }
    
    private void sendEmailVerification(FirebaseUser user) {
        user.sendEmailVerification()
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    // Show verification instructions dialog
                    androidx.appcompat.app.AlertDialog.Builder builder = 
                        new androidx.appcompat.app.AlertDialog.Builder(this);
                    builder.setTitle("Verify Your Email");
                    builder.setMessage("A verification email has been sent to " + user.getEmail() + 
                        ". Please check your inbox and click the verification link to complete registration.");
                    builder.setPositiveButton("OK", (dialog, which) -> {
                        // Sign out the user and redirect to login
                        mAuth.signOut();
                        Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    });
                    builder.setCancelable(false);
                    builder.show();
                } else {
                    Log.e(TAG, "sendEmailVerification failed", task.getException());
                    Toast.makeText(RegisterActivity.this,
                        "Failed to send verification email: " + task.getException().getMessage(),
                        Toast.LENGTH_LONG).show();
                }
            });
    }
    
    private void saveUserToFirestore(FirebaseUser user) {
        // Get user data from SharedPreferences
        SharedPreferences prefs = getSharedPreferences("NewsAppPrefs", MODE_PRIVATE);
        String name = prefs.getString("registration_name", "");
        String email = prefs.getString("registration_email", "");
        String phone = prefs.getString("registration_phone", "");
        String age = prefs.getString("registration_age", "");
        
        // Create user data map
        Map<String, Object> userData = new HashMap<>();
        userData.put("name", name);
        userData.put("email", email);
        userData.put("phone", phone);
        userData.put("age", age);
        userData.put("createdAt", System.currentTimeMillis());
        userData.put("lastLogin", System.currentTimeMillis());
        userData.put("provider", "email");
        userData.put("emailVerified", user.isEmailVerified());
        
        // Save to Firestore
        db.collection("users").document(user.getUid())
            .set(userData)
            .addOnSuccessListener(aVoid -> {
                Log.d(TAG, "User data saved to Firestore");
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error saving user data", e);
                Toast.makeText(RegisterActivity.this, 
                    "Failed to save user data: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
            });
    }
    
    private void showTermsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Terms of Service");
        builder.setMessage("These Terms of Service govern your use of the application. By using our app, you agree to these terms.\n\n" +
                "1. The app provides news content from various sources.\n" +
                "2. We are not responsible for the accuracy of third-party content.\n" +
                "3. We reserve the right to modify or discontinue the service at any time.\n" +
                "4. Users are responsible for maintaining the confidentiality of their account information.");
        builder.setPositiveButton("Close", null);
        builder.show();
    }
    
    private void showPrivacyDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Privacy Policy");
        builder.setMessage("We collect minimal personal information needed to provide our services.\n\n" +
                "1. Your email address is used for authentication.\n" +
                "2. We do not sell your data to third parties.\n" +
                "3. We use cookies and similar technologies to enhance your experience.\n" +
                "4. We may collect usage statistics to improve our service.");
        builder.setPositiveButton("Close", null);
        builder.show();
    }
}