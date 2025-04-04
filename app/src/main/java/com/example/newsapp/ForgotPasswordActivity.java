// app/src/main/java/com/example/newsapp/ForgotPasswordActivity.java
package com.example.newsapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class ForgotPasswordActivity extends AppCompatActivity {

    private EditText etEmail;
    private Button btnResetPassword;
    private ImageButton btnBack;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // Initialize views
        etEmail = findViewById(R.id.etForgotPasswordEmail);
        btnResetPassword = findViewById(R.id.btnResetPassword);
        btnBack = findViewById(R.id.btnBack);

        // Get email from intent if available
        String email = getIntent().getStringExtra("email");
        if (email != null && !email.isEmpty()) {
            etEmail.setText(email);
        }

        // Set click listeners
        btnResetPassword.setOnClickListener(v -> sendResetEmail());
        btnBack.setOnClickListener(v -> finish());
    }

    private void sendResetEmail() {
        String email = etEmail.getText().toString().trim();

        if (email.isEmpty()) {
            etEmail.setError("Email is required");
            etEmail.requestFocus();
            return;
        }

        // Show loading indicator or disable button
        btnResetPassword.setEnabled(false);
        btnResetPassword.setText("Sending...");

        // In a real app, you would verify the email exists in Firebase
        // For this example, we'll just proceed to the OTP verification screen
        
        // Navigate to OTP verification screen
        Intent intent = new Intent(ForgotPasswordActivity.this, VerifyOtpActivity.class);
        intent.putExtra("email", email);
        startActivity(intent);
        
        // Re-enable button
        btnResetPassword.setEnabled(true);
        btnResetPassword.setText("Send Reset Link");
    }
}