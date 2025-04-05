// app/src/main/java/com/example/newsapp/ForgotPasswordActivity.java
package com.example.newsapp;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;

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

        // Use Firebase's built-in password reset functionality
        mAuth.sendPasswordResetEmail(email)
            .addOnCompleteListener(task -> {
                btnResetPassword.setEnabled(true);
                btnResetPassword.setText("Send Reset Link");
                
                if (task.isSuccessful()) {
                    // Show success dialog
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Check Your Email");
                    builder.setMessage("We've sent a password reset link to " + email + 
                        ". Please check your inbox and follow the instructions to reset your password.");
                    builder.setPositiveButton("OK", (dialog, which) -> {
                        // Return to login screen
                        finish();
                    });
                    builder.setCancelable(false);
                    builder.show();
                } else {
                    // Show error
                    Toast.makeText(ForgotPasswordActivity.this, 
                        "Failed to send reset email: " + task.getException().getMessage(),
                        Toast.LENGTH_LONG).show();
                }
            });
    }
}