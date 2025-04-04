// app/src/main/java/com/example/newsapp/VerifyOtpActivity.java
package com.example.newsapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class VerifyOtpActivity extends AppCompatActivity {

    private EditText etOtp1, etOtp2, etOtp3, etOtp4, etOtp5, etOtp6;
    private Button btnVerifyOtp;
    private TextView tvResendCode, tvEmailDisplay;
    private ImageButton btnBack;
    private String email;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verify_otp);

        // Get email from intent
        email = getIntent().getStringExtra("email");
        if (email == null || email.isEmpty()) {
            Toast.makeText(this, "Email not provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize views
        etOtp1 = findViewById(R.id.etOtp1);
        etOtp2 = findViewById(R.id.etOtp2);
        etOtp3 = findViewById(R.id.etOtp3);
        etOtp4 = findViewById(R.id.etOtp4);
        etOtp5 = findViewById(R.id.etOtp5);
        etOtp6 = findViewById(R.id.etOtp6);
        btnVerifyOtp = findViewById(R.id.btnVerifyOtp);
        tvResendCode = findViewById(R.id.tvResendCode);
        tvEmailDisplay = findViewById(R.id.tvEmailDisplay);
        btnBack = findViewById(R.id.btnBack);

        // Display email
        tvEmailDisplay.setText(email);

        // Set up OTP input fields
        setupOtpInputs();

        // Set click listeners
        btnVerifyOtp.setOnClickListener(v -> verifyOtp());
        tvResendCode.setOnClickListener(v -> resendOtp());
        btnBack.setOnClickListener(v -> finish());
    }

    private void setupOtpInputs() {
        // Auto-focus next field when a digit is entered
        etOtp1.addTextChangedListener(new OtpTextWatcher(etOtp1, etOtp2));
        etOtp2.addTextChangedListener(new OtpTextWatcher(etOtp2, etOtp3));
        etOtp3.addTextChangedListener(new OtpTextWatcher(etOtp3, etOtp4));
        etOtp4.addTextChangedListener(new OtpTextWatcher(etOtp4, etOtp5));
        etOtp5.addTextChangedListener(new OtpTextWatcher(etOtp5, etOtp6));
        etOtp6.addTextChangedListener(new OtpTextWatcher(etOtp6, null));

        // Request focus on first field
        etOtp1.requestFocus();
    }

    private void verifyOtp() {
        String otp = etOtp1.getText().toString() +
                etOtp2.getText().toString() +
                etOtp3.getText().toString() +
                etOtp4.getText().toString() +
                etOtp5.getText().toString() +
                etOtp6.getText().toString();

        if (otp.length() < 6) {
            Toast.makeText(this, "Please enter the complete OTP", Toast.LENGTH_SHORT).show();
            return;
        }

        // In a real app, you would verify the OTP with Firebase
        // For this example, we'll just proceed to the next step
        Toast.makeText(this, "OTP verified successfully", Toast.LENGTH_SHORT).show();
        
        // Navigate to reset password screen
        Intent intent = new Intent(VerifyOtpActivity.this, ResetPasswordActivity.class);
        intent.putExtra("email", email);
        startActivity(intent);
    }

    private void resendOtp() {
        // In a real app, you would call Firebase to resend the OTP
        Toast.makeText(this, "OTP resent to " + email, Toast.LENGTH_SHORT).show();
    }

    // Helper class for OTP input fields
    private class OtpTextWatcher implements TextWatcher {
        private final EditText currentField;
        private final EditText nextField;

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