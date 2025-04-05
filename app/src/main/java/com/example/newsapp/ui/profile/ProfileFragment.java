package com.example.newsapp.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.example.newsapp.R;
import com.example.newsapp.LoginActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.auth.UserInfo;

public class ProfileFragment extends Fragment {
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private FirebaseFirestore db;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        db = FirebaseFirestore.getInstance();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Initialize your profile view components here
        TextView profileName = view.findViewById(R.id.profile_name);
        TextView profileEmail = view.findViewById(R.id.profile_email);
        Button btnLogout = view.findViewById(R.id.btnLogout);
        Button btnLinkAccount = view.findViewById(R.id.btnLinkAccount);
        
        // Set user information
        if (currentUser != null) {
            profileName.setText(currentUser.getDisplayName());
            profileEmail.setText(currentUser.getEmail());
        }

        // Check if the user is linked to a Google account
        if (currentUser.getProviderData() != null) {
            boolean isLinked = false;
            for (UserInfo profile : currentUser.getProviderData()) {
                if (profile.getProviderId().equals(GoogleAuthProvider.PROVIDER_ID)) {
                    isLinked = true;
                    break;
                }
            }
            if (isLinked) {
                btnLinkAccount.setVisibility(View.GONE); // Hide the button if already linked
                Toast.makeText(getActivity(), "Google account is already linked.", Toast.LENGTH_SHORT).show();
            }
        }

        // Logout functionality
        btnLogout.setOnClickListener(v -> {
            mAuth.signOut();
            Toast.makeText(getActivity(), "Logged out successfully", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(getActivity(), LoginActivity.class)); // Redirect to LoginActivity
            getActivity().finish(); // Close the current activity
        });

        // Add account linking functionality
        btnLinkAccount.setOnClickListener(v -> startGoogleSignInForLinking());
    }

    private void startGoogleSignInForLinking() {
        // Implement Google Sign-In logic for linking accounts
        // This would involve using GoogleSignInClient to initiate the sign-in flow
    }

    private void linkGoogleAccount(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        currentUser.linkWithCredential(credential)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(getActivity(), "Google account linked successfully", Toast.LENGTH_SHORT).show();
                    } else {
                        String errorMessage = "Failed to link Google account";
                        if (task.getException() instanceof FirebaseAuthInvalidCredentialsException) {
                            errorMessage = "Invalid credentials. Please try again.";
                        }
                        Toast.makeText(getActivity(), errorMessage, Toast.LENGTH_SHORT).show();
                    }
                });
    }
}