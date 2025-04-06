package com.example.newsapp.ui.profile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.bumptech.glide.Glide;
import com.example.newsapp.LoginActivity;
import com.example.newsapp.R;
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
import com.google.firebase.auth.UserInfo;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class ProfileFragment extends Fragment {
    private static final String TAG = "ProfileFragment";
    
    // Firebase and Auth
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private FirebaseFirestore db;
    private GoogleSignInClient mGoogleSignInClient;
    
    // Activity launchers
    private ActivityResultLauncher<Intent> linkAccountLauncher;
    
    // Shared Preferences
    private SharedPreferences prefs;
    
    // UI References
    private TextView linkStatusText;
    private ImageView linkStatusIcon;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize Firebase components
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        db = FirebaseFirestore.getInstance();
        
        // Initialize shared preferences
        prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        
        // Log auth providers for debugging
        logAuthProviders();
        
        // Configure Google Sign In
        setupGoogleSignIn();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        setupProfileHeader(view);
        setupOptions(view);

        validateAccountLinkStatus();

    }
/**
 * Validates and synchronizes the account link status between Firebase Auth and Firestore
 */
private void validateAccountLinkStatus() {
    // First check Firebase Auth providers
    final boolean isLinkedByProviders = checkIfGoogleProviderLinked();
    
    // Initial UI update based on provider data
    updateGoogleLinkStatus(isLinkedByProviders);
    
    // Then verify with Firestore data for consistency
    if (currentUser != null) {
        db.collection("users").document(currentUser.getUid())
            .get()
            .addOnSuccessListener(document -> {
                if (document.exists()) {
                    // Check if the Firestore data matches what we determined from providers
                    String linkedProviders = document.getString("linkedProviders");
                    String provider = document.getString("provider");
                    
                    boolean isLinkedInFirestore = 
                        (linkedProviders != null && linkedProviders.contains("google")) ||
                        (provider != null && provider.contains("google"));
                    
                    // If there's a mismatch, update Firestore to match the actual provider status
                    if (isLinkedByProviders != isLinkedInFirestore) {
                        updateFirestoreWithCorrectLinkStatus(document, isLinkedByProviders);
                    }
                    
                    // Final UI update based on the provider status
                    updateGoogleLinkStatus(isLinkedByProviders);
                }
            });
    }
}

/**
 * Checks if Google provider is linked in Firebase Auth
 */
private boolean checkIfGoogleProviderLinked() {
    if (currentUser != null && currentUser.getProviderData() != null) {
        for (UserInfo profile : currentUser.getProviderData()) {
            Log.d(TAG, "Provider: " + profile.getProviderId());
            if (profile.getProviderId().equals(GoogleAuthProvider.PROVIDER_ID)) {
                return true;
            }
        }
    }
    return false;
}

/**
 * Updates Firestore data to reflect the correct link status
 */
private void updateFirestoreWithCorrectLinkStatus(com.google.firebase.firestore.DocumentSnapshot document, boolean isLinked) {
    Map<String, Object> updates = new HashMap<>();
    if (isLinked) {
        updates.put("linkedProviders", "google");
        updates.put("provider", document.getString("provider") != null ? 
            document.getString("provider") + ",google" : "google");
    } else {
        updates.put("linkedProviders", "");
        updates.put("provider", "email");
    }
    
    // Update Firestore with correct data
    db.collection("users").document(currentUser.getUid())
        .update(updates)
        .addOnSuccessListener(aVoid -> 
            Log.d(TAG, "Account link status synchronized"));
}
    
    /**
     * Logs the current auth providers for debugging purposes
     */
    private void logAuthProviders() {
        if (currentUser != null) {
            Log.d(TAG, "Current user: " + currentUser.getEmail());
            Log.d(TAG, "Auth providers:");
            for (UserInfo profile : currentUser.getProviderData()) {
                Log.d(TAG, "Provider: " + profile.getProviderId());
            }
        }
    }
    
    /**
     * Configures Google Sign-In and sets up the activity result launcher
     */
    private void setupGoogleSignIn() {
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
    
    /**
     * Sets up the profile header with user information
     */
    private void setupProfileHeader(View view) {
        ImageView profileImage = view.findViewById(R.id.profile_image);
        TextView profileName = view.findViewById(R.id.profile_name);
        TextView profileEmail = view.findViewById(R.id.profile_email);
        TextView membershipStatus = view.findViewById(R.id.membership_status);
        ImageView editProfileButton = view.findViewById(R.id.edit_profile_button);
        
        Log.d(TAG, "Setting up profile header");
        
        if (currentUser != null) {
            // Set user name and email
            String displayName = currentUser.getDisplayName();
            String email = currentUser.getEmail();
            
            Log.d(TAG, "Firebase User data - Name: " + displayName + ", Email: " + email);
            
            // Set initial values from Firebase Auth
            if (email != null && !email.isEmpty()) {
                profileEmail.setText(email);
            } else {
                profileEmail.setText("No email available");
            }
            
            if (displayName != null && !displayName.isEmpty()) {
                profileName.setText(displayName);
            } else {
                // If no display name, we'll update it from Firestore
                profileName.setText("User");
            }
            
            // Load profile image if available
            if (currentUser.getPhotoUrl() != null) {
                Glide.with(this)
                    .load(currentUser.getPhotoUrl())
                    .circleCrop()
                    .placeholder(R.drawable.default_profile_image)
                    .into(profileImage);
            }
            
            // Load additional user data from Firestore
            loadUserDataFromFirestore(profileName, profileEmail, membershipStatus);
        } else {
            // Set default values if user is null (should not normally happen)
            Log.w(TAG, "Current user is null in profile header setup");
            profileName.setText("Guest User");
            profileEmail.setText("Not signed in");
            membershipStatus.setText("Guest");
        }
        
        // Set edit profile button click listener - now using fragment navigation
        editProfileButton.setOnClickListener(v -> {
            navigateToFragment(new AccountDetailsFragment());
        });
    }

    private void loadUserDataFromFirestore(TextView profileName, TextView profileEmail, TextView membershipStatus) {
        if (currentUser == null) return;
        
        Log.d(TAG, "Loading user data from Firestore for UID: " + currentUser.getUid());
        
        db.collection("users").document(currentUser.getUid())
            .get()
            .addOnSuccessListener(document -> {
                if (document.exists()) {
                    Log.d(TAG, "Firestore document exists for user");
                    
                    // Get name from Firestore
                    String name = document.getString("name");
                    if (name != null && !name.isEmpty()) {
                        Log.d(TAG, "Found name in Firestore: " + name);
                        profileName.setText(name);
                    } else {
                        Log.d(TAG, "No name field in Firestore, checking fullName");
                        // Fallback to fullName if name is not available
                        String fullName = document.getString("fullName");
                        if (fullName != null && !fullName.isEmpty()) {
                            Log.d(TAG, "Found fullName in Firestore: " + fullName);
                            profileName.setText(fullName);
                        }
                    }
                    
                    // Get email from Firestore as backup
                    String emailFromFirestore = document.getString("email");
                    if (emailFromFirestore != null && !emailFromFirestore.isEmpty() && 
                        (profileEmail.getText().toString().isEmpty() || profileEmail.getText().toString().equals("No email available"))) {
                        Log.d(TAG, "Using email from Firestore: " + emailFromFirestore);
                        profileEmail.setText(emailFromFirestore);
                    }
                    
                    // Check membership status
                    String membership = document.getString("membership");
                    if (membership != null && !membership.isEmpty()) {
                        membershipStatus.setText(membership);
                        membershipStatus.setVisibility(View.VISIBLE);
                    } else {
                        // Set default membership if not available
                        membershipStatus.setText("Free User");
                        membershipStatus.setVisibility(View.VISIBLE);
                    }
                } else {
                    Log.w(TAG, "No user document found in Firestore");
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error loading user data from Firestore", e);
            });
    }

// Update setupOptions to use fragment navigation
private void setupOptions(View view) {
    // Find views
    LinearLayout myAccountOption = view.findViewById(R.id.option_my_account);
    LinearLayout accountLinkingOption = view.findViewById(R.id.option_account_linking);
    LinearLayout pushNotificationsOption = view.findViewById(R.id.option_push_notifications);
    LinearLayout logoutOption = view.findViewById(R.id.option_logout);
    LinearLayout helpSupportOption = view.findViewById(R.id.option_help_support);
    LinearLayout aboutAppOption = view.findViewById(R.id.option_about_app);
    
    linkStatusText = view.findViewById(R.id.link_status_text);
    linkStatusIcon = view.findViewById(R.id.link_status_icon);
    
    // Set up notifications switch if available
    SwitchCompat notificationsSwitch = view.findViewById(R.id.switch_push_notifications);
    if (notificationsSwitch != null) {
        notificationsSwitch.setChecked(prefs.getBoolean("push_notifications_enabled", true));
    }
    
    // Check if Google account is linked
    boolean isGoogleLinked = isGoogleAccountLinked();
    updateGoogleLinkStatus(isGoogleLinked);
    
    // Set click listeners for options
    
    // My Account option
    if (myAccountOption != null) {
        myAccountOption.setOnClickListener(v -> {
            navigateToFragment(new AccountDetailsFragment());
        });
    }
    
    // Account linking option
    if (accountLinkingOption != null) {
        accountLinkingOption.setOnClickListener(v -> {
            if (!isGoogleLinked) {
                startGoogleSignInForLinking();
            } else {
                Toast.makeText(getActivity(), "Google account already linked", 
                        Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    // Push notifications option
    if (pushNotificationsOption != null && notificationsSwitch != null) {
        notificationsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Save the notification preference
            prefs.edit().putBoolean("push_notifications_enabled", isChecked).apply();
            
            Toast.makeText(getActivity(), 
                    "Push notifications " + (isChecked ? "enabled" : "disabled"), 
                    Toast.LENGTH_SHORT).show();
        });
    }
    
    // Logout option
    if (logoutOption != null) {
        logoutOption.setOnClickListener(v -> showLogoutConfirmationDialog());
    }
    
    // Help & Support option
    if (helpSupportOption != null) {
        helpSupportOption.setOnClickListener(v -> {
            navigateToFragment(new HelpSupportFragment());
        });
    }
    
    // About App option
    if (aboutAppOption != null) {
        aboutAppOption.setOnClickListener(v -> {
            navigateToFragment(new AboutAppFragment());
        });
    }
}

// Add this helper method to navigate to fragments
private void navigateToFragment(Fragment fragment) {
    requireActivity().getSupportFragmentManager()
            .beginTransaction()
            .replace(R.id.fragment_container, fragment) // Make sure this ID matches your container
            .addToBackStack(null)
            .commit();
}
    
    /**
     * Checks if a Google account is linked to the current user
     */
private boolean isGoogleAccountLinked() {
    if (currentUser != null && currentUser.getProviderData() != null) {
        for (UserInfo profile : currentUser.getProviderData()) {
            // Check specifically for Google Auth provider
            if (profile.getProviderId().equals(GoogleAuthProvider.PROVIDER_ID)) {
                return true;
            }
        }
        
        // If we didn't find a Google provider, check Firestore data as backup
        db.collection("users").document(currentUser.getUid())
            .get()
            .addOnSuccessListener(document -> {
                if (document.exists() && document.contains("linkedProviders")) {
                    String linkedProviders = document.getString("linkedProviders");
                    if (linkedProviders != null && linkedProviders.contains("google")) {
                        // Update UI to show linked status
                        updateGoogleLinkStatus(true);
                    } else {
                        updateGoogleLinkStatus(false);
                    }
                } else {
                    updateGoogleLinkStatus(false);
                }
            });
    }
    
    // Return immediate result based on provider data only
    // The Firestore check will update UI asynchronously if needed
    return false;
}
    
    /**
     * Updates the UI to reflect the Google account linking status
     */
    private void updateGoogleLinkStatus(boolean isLinked) {
        if (linkStatusText == null || linkStatusIcon == null) return;
        
        if (isLinked) {
            linkStatusText.setText("Google account linked");
            linkStatusIcon.setImageResource(R.drawable.ic_check_circle);
        } else {
            linkStatusText.setText("Link your Google account");
            linkStatusIcon.setImageResource(R.drawable.ic_warning);
        }
    }
    
    /**
     * Shows a confirmation dialog before logging out
     */
    private void showLogoutConfirmationDialog() {
        new AlertDialog.Builder(getActivity())
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    // Log out the user
                    mAuth.signOut();
                    Toast.makeText(getActivity(), "Logged out successfully", 
                            Toast.LENGTH_SHORT).show();
                    
                    // Navigate to login screen
                    Intent intent = new Intent(getActivity(), LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                })
                .setNegativeButton("No", null)
                .show();
    }
    
    /**
     * Initiates the Google Sign-In flow for account linking
     */
    private void startGoogleSignInForLinking() {
        // Sign out from Google first to force the account picker to show
        mGoogleSignInClient.signOut().addOnCompleteListener(getActivity(), task -> {
            // Start the Google Sign In flow
            Intent signInIntent = mGoogleSignInClient.getSignInIntent();
            linkAccountLauncher.launch(signInIntent);
        });
    }
    
    /**
     * Links a Google account using the provided ID token
     */
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
                        
                        // Update UI to reflect the linked account
                        updateGoogleLinkStatus(true);
                        
                        Toast.makeText(getActivity(), 
                                "Google account linked successfully", 
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Log.w(TAG, "linkWithCredential:failure", task.getException());
                        handleLinkingError(task.getException());
                    }
                });
    }
    
    /**
     * Handles errors that occur during the account linking process
     */
private void handleLinkingError(Exception exception) {
    if (exception != null && exception.getMessage() != null) {
        String errorMessage = exception.getMessage();
        Log.d(TAG, "Link error message: " + errorMessage);
        
        if (errorMessage.contains("already linked")) {
            Toast.makeText(getActivity(), 
                "This Google account is already linked to your profile", 
                Toast.LENGTH_SHORT).show();
            
            // Update UI since account is actually linked
            updateGoogleLinkStatus(true);
        } else if (errorMessage.contains("already exists") || 
                  errorMessage.contains("email already in use")) {
            // This could be "credential already in use" or similar messages
            Toast.makeText(getActivity(), 
                "This Google account is already used by another user", 
                Toast.LENGTH_SHORT).show();
            
            // Make sure UI shows not linked
            updateGoogleLinkStatus(false);
        } else {
            Toast.makeText(getActivity(), 
                "Failed to link Google account: " + errorMessage, 
                Toast.LENGTH_SHORT).show();
            
            // Make sure UI shows not linked
            updateGoogleLinkStatus(false);
        }
    } else {
        Toast.makeText(getActivity(), 
            "Failed to link Google account", 
            Toast.LENGTH_SHORT).show();
        
        // Make sure UI shows not linked
        updateGoogleLinkStatus(false);
    }
}

    private void updateUserDataAfterLinking() {
        // Update the user document to indicate multiple auth providers
        Map<String, Object> updates = new HashMap<>();
        updates.put("linkedProviders", "google");
        updates.put("provider", "email,google"); // Update to indicate multiple providers
        
        db.collection("users").document(currentUser.getUid())
                .update(updates)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "User data updated after linking"))
                .addOnFailureListener(e -> Log.w(TAG, "Error updating user data after linking", e));
    }
}
