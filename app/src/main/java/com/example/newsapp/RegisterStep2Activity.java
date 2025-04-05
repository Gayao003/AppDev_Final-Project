package com.example.newsapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

import com.example.newsapp.models.User;

public class RegisterStep2Activity extends AppCompatActivity {
    private static final String TAG = "RegisterStep2Activity";
    
    private EditText etOtp1, etOtp2, etOtp3, etOtp4, etOtp5, etOtp6;
    private Button btnVerify, btnResendOtp;
    private ImageButton btnBack;
    private TextView tvStepIndicator, tvOtpTimer, tvEmailSent;
    
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    
    private String name, email, phone, age;
    private CountDownTimer countDownTimer;
    private static final long OTP_TIMEOUT = 60000; // 60 seconds
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register_step2);
        
        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();
        
        // Initialize Firestore
        db = FirebaseFirestore.getInstance();
        
        // Get data from intent
        Intent intent = getIntent();
        name = intent.getStringExtra("name");
        email = intent.getStringExtra("email");
        phone = intent.getStringExtra("phone");
        age = intent.getStringExtra("age");
        
        // Initialize views
        etOtp1 = findViewById(R.id.etOtp1);
        etOtp2 = findViewById(R.id.etOtp2);
        etOtp3 = findViewById(R.id.etOtp3);
        etOtp4 = findViewById(R.id.etOtp4);
        etOtp5 = findViewById(R.id.etOtp5);
        etOtp6 = findViewById(R.id.etOtp6);
        btnVerify = findViewById(R.id.btnVerify);
        btnResendOtp = findViewById(R.id.btnResendOtp);
        btnBack = findViewById(R.id.btnBack);
        tvStepIndicator = findViewById(R.id.tvStepIndicator);
        tvOtpTimer = findViewById(R.id.tvOtpTimer);
        tvEmailSent = findViewById(R.id.tvEmailSent);
        
        tvStepIndicator.setText("Step 2 of 2");
        tvEmailSent.setText("Enter the verification code sent to " + email);
        
        setupOtpInputs();
        startOtpTimer();
        
        // Set click listeners
        btnVerify.setOnClickListener(v -> verifyOtp());
        
        btnResendOtp.setOnClickListener(v -> resendOtp());
        
        btnBack.setOnClickListener(v -> finish());
    }
    
    private void setupOtpInputs() {
        // Auto-focus to next field when a digit is entered
        etOtp1.addTextChangedListener(new OtpTextWatcher(etOtp1, etOtp2));
        etOtp2.addTextChangedListener(new OtpTextWatcher(etOtp2, etOtp3));
        etOtp3.addTextChangedListener(new OtpTextWatcher(etOtp3, etOtp4));
        etOtp4.addTextChangedListener(new OtpTextWatcher(etOtp4, etOtp5));
        etOtp5.addTextChangedListener(new OtpTextWatcher(etOtp5, etOtp6));
        etOtp6.addTextChangedListener(new OtpTextWatcher(etOtp6, null));
    }
    
    private void startOtpTimer() {
        btnResendOtp.setEnabled(false);
        
        countDownTimer = new CountDownTimer(OTP_TIMEOUT, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                tvOtpTimer.setText("Resend code in " + (millisUntilFinished / 1000) + " seconds");
            }
            
            @Override
            public void onFinish() {
                tvOtpTimer.setText("Didn't receive the code?");
                btnResendOtp.setEnabled(true);
            }
        }.start();
    }
    
    private void verifyOtp() {
        String enteredOtp = etOtp1.getText().toString() +
                etOtp2.getText().toString() +
                etOtp3.getText().toString() +
                etOtp4.getText().toString() +
                etOtp5.getText().toString() +
                etOtp6.getText().toString();
        
        if (enteredOtp.length() < 6) {
            Toast.makeText(this, "Please enter the complete OTP", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Get stored OTP and user data from SharedPreferences
        SharedPreferences prefs = getSharedPreferences("NewsAppPrefs", MODE_PRIVATE);
        String storedOtp = prefs.getString("registration_otp", "");
        String storedEmail = prefs.getString("registration_email", "");
        String storedPassword = prefs.getString("registration_password", "");
        String storedName = prefs.getString("registration_name", "");
        String storedPhone = prefs.getString("registration_phone", "");
        String storedAge = prefs.getString("registration_age", "");
        
        if (!email.equals(storedEmail)) {
            Toast.makeText(this, "Email mismatch. Please restart registration.", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (enteredOtp.equals(storedOtp)) {
            // OTP verified, proceed with registration
            registerUser(storedName, storedEmail, storedPhone, storedAge, storedPassword);
        } else {
            Toast.makeText(this, "Invalid OTP. Please try again.", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void registerUser(String name, String email, String phone, String age, String password) {
        // Show loading indicator
        // ...
        
        // Create user with email and password
        mAuth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this, task -> {
                // Hide loading indicator
                
                if (task.isSuccessful()) {
                    // User created successfully
                    String userId = mAuth.getCurrentUser().getUid();
                    
                    // Create user document in Firestore
                    User user = new User(userId, name, email, phone, age);
                    
                    db.collection("users").document(userId)
                        .set(user)
                        .addOnSuccessListener(aVoid -> {
                            // Show success dialog
                            showRegistrationSuccessDialog();
                        })
                        .addOnFailureListener(e -> {
                            Log.w(TAG, "Error adding user to Firestore", e);
                            Toast.makeText(RegisterStep2Activity.this, 
                                    "Error creating user profile: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        });
                } else {
                    // If sign up fails, display a message to the user
                    Log.w(TAG, "createUserWithEmail:failure", task.getException());
                    Toast.makeText(RegisterStep2Activity.this, 
                            "Authentication failed: " + task.getException().getMessage(),
                            Toast.LENGTH_SHORT).show();
                }
            });
    }
    
    private void showRegistrationSuccessDialog() {
        // Show success dialog
        RegistrationSuccessDialog dialog = new RegistrationSuccessDialog(this, () -> {
            // Redirect to login screen
            Intent intent = new Intent(RegisterStep2Activity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
        dialog.show();
    }
    
    private void resendOtp() {
        // Generate a new OTP and send it
        int otp = (int) (Math.random() * 900000) + 100000;
        
        // Store new OTP
        getSharedPreferences("NewsAppPrefs", MODE_PRIVATE)
                .edit()
                .putString("registration_otp", String.valueOf(otp))
                .apply();
        
        // In a real app, you would send this OTP via email
        Log.d(TAG, "New OTP for registration: " + otp);
        Toast.makeText(this, "New OTP sent: " + otp, Toast.LENGTH_LONG).show();
        
        // Reset timer
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        startOtpTimer();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }
    
    // Helper class for OTP input fields
    private class OtpTextWatcher implements TextWatcher {
        private EditText currentField;
        private EditText nextField;
        
        public OtpTextWatcher(EditText currentField, EditText nextField) {
            this.currentField = currentField;
            this.nextField = nextField;
        }
        
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }
        
        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
        
        @Override
        public void afterTextChanged(Editable s) {
            if (s.length() == 1 && nextField != null) {
                nextField.requestFocus();
            }
        }
    }
}