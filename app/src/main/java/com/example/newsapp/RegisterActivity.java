package com.example.newsapp;

import android.content.Intent;
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

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class RegisterActivity extends AppCompatActivity {
    private static final String TAG = "RegisterActivity";
    
    private EditText etName, etEmail, etPhone, etAge, etPassword, etConfirmPassword;
    private CheckBox cbTerms;
    private Button btnNext;
    private ImageButton btnBack, btnTogglePassword, btnToggleConfirmPassword;
    private TextView tvLoginPrompt, tvStepIndicator;
    
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
        btnTogglePassword = findViewById(R.id.btnTogglePassword);
        btnToggleConfirmPassword = findViewById(R.id.btnToggleConfirmPassword);
        
        tvStepIndicator.setText("Step 1 of 2");
        
        // Set click listeners
        btnNext.setOnClickListener(v -> validateAndProceed());
        
        btnBack.setOnClickListener(v -> finish());
        
        tvLoginPrompt.setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });
        
        // Add toggle password visibility listeners
        btnTogglePassword.setOnClickListener(v -> togglePasswordVisibility(etPassword, btnTogglePassword));
        btnToggleConfirmPassword.setOnClickListener(v -> togglePasswordVisibility(etConfirmPassword, btnToggleConfirmPassword));
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
        // Send OTP to email
        sendOtpToEmail(email);
        
        // Store user data in SharedPreferences
        getSharedPreferences("NewsAppPrefs", MODE_PRIVATE)
                .edit()
                .putString("registration_name", name)
                .putString("registration_email", email)
                .putString("registration_phone", phone)
                .putString("registration_age", age)
                .putString("registration_password", password)
                .apply();
        
        // Navigate to step 2
        Intent intent = new Intent(RegisterActivity.this, RegisterStep2Activity.class);
        intent.putExtra("email", email);
        startActivity(intent);
    }
    
    private void sendOtpToEmail(String email) {
        // Generate a random 6-digit OTP
        int otp = (int) (Math.random() * 900000) + 100000;
        
        // Store OTP in SharedPreferences or Firebase for verification
        getSharedPreferences("NewsAppPrefs", MODE_PRIVATE)
                .edit()
                .putString("registration_otp", String.valueOf(otp))
                .putString("registration_email", email)
                .apply();
        
        // In a real app, you would send this OTP via email
        // For now, we'll just log it and show a toast for testing
        Log.d(TAG, "OTP for registration: " + otp);
        Toast.makeText(this, "OTP sent to email: " + otp, Toast.LENGTH_LONG).show();
    }
    
    private void togglePasswordVisibility(EditText editText, ImageButton toggleButton) {
        if (editText.getInputType() == (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)) {
            // Show password
            editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            toggleButton.setImageResource(R.drawable.ic_visibility);
        } else {
            // Hide password
            editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            toggleButton.setImageResource(R.drawable.ic_visibility_off);
        }
        // Move cursor to end of text
        editText.setSelection(editText.getText().length());
    }
}