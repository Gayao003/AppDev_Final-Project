package com.example.newsapp;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.Window;
import android.widget.Button;

public class RegistrationSuccessDialog extends Dialog {
    
    private Button btnGoToLogin;
    private SuccessListener listener;
    
    public interface SuccessListener {
        void onContinue();
    }
    
    public RegistrationSuccessDialog(Context context, SuccessListener listener) {
        super(context);
        this.listener = listener;
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_registration_success);
        
        btnGoToLogin = findViewById(R.id.btnGoToLogin);
        
        btnGoToLogin.setOnClickListener(v -> {
            listener.onContinue();
            dismiss();
        });
        
        setCancelable(false);
    }
}