package com.example.newsapp.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;

import com.example.newsapp.R;
import com.example.newsapp.LoginActivity;
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
import android.app.Activity;

import java.util.HashMap;
import java.util.Map;

public class ProfileFragment extends Fragment {
    private static final String TAG = "ProfileFragment";
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private FirebaseFirestore db;
    private GoogleSignInClient mGoogleSignInClient;
    private ActivityResultLauncher<Intent> linkAccountLauncher;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        db = FirebaseFirestore.getInstance();

        // Configure Google Sign In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(requireActivity(), gso);

        // Set up ActivityResultLauncher for Google Sign-In account linking
        linkAccountLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent data = result.getData();
                        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
                        try {
                            // Google Sign In was successful, link with Firebase
                            GoogleSignInAccount account = task.getResult(ApiException.class);
                            Log.d(TAG, "Google account selected for linking: " + account.getId());
                            linkGoogleAccount(account.getIdToken());
                        } catch (ApiException e) {
                            // Google Sign In failed
                            Log.w(TAG, "Google sign in failed", e);
                            Toast.makeText(getActivity(), "Google Sign-In failed: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
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
            for (com.google.firebase.auth.UserInfo profile : currentUser.getProviderData()) {
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
        // Sign out from Google first to force the account picker to show
        mGoogleSignInClient.signOut().addOnCompleteListener(getActivity(), task -> {
            // Start the Google Sign In flow
            Intent signInIntent = mGoogleSignInClient.getSignInIntent();
            linkAccountLauncher.launch(signInIntent);
        });
    }

    private void linkGoogleAccount(String idToken) {
        // Get Google Auth credential
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        
        // Link the credential to the current user account
        currentUser.linkWithCredential(credential)
                .addOnCompleteListener(getActivity(), task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "linkWithCredential:success");
                        // Update the current user reference
                        currentUser = mAuth.getCurrentUser();
                        
                        // Update user data in Firestore to reflect the linked account
                        updateUserDataAfterLinking();
                        
                        Toast.makeText(getActivity(), 
                                "Google account linked successfully", 
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Log.w(TAG, "linkWithCredential:failure", task.getException());
                        
                        // Handle specific error cases
                        if (task.getException().getMessage().contains("already linked")) {
                            Toast.makeText(getActivity(), 
                                    "This Google account is already linked to your profile", 
                                    Toast.LENGTH_SHORT).show();
                        } else if (task.getException().getMessage().contains("already exists")) {
                            Toast.makeText(getActivity(), 
                                    "This Google account is already used by another user", 
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(getActivity(), 
                                    "Failed to link Google account: " + task.getException().getMessage(), 
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void updateUserDataAfterLinking() {
        // Update the user document to indicate multiple auth providers
        Map<String, Object> updates = new HashMap<>();
        updates.put("linkedProviders", "google");
        
        db.collection("users").document(currentUser.getUid())
                .update(updates)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "User data updated after linking"))
                .addOnFailureListener(e -> Log.w(TAG, "Error updating user data after linking", e));
    }
}