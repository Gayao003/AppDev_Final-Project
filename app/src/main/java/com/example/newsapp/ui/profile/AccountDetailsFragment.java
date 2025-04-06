package com.example.newsapp.ui.profile;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.example.newsapp.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import de.hdodenhof.circleimageview.CircleImageView;

public class AccountDetailsFragment extends Fragment {

    private CircleImageView profileImage;
    private TextView profileName, profileEmail;
    private TextInputEditText firstNameInput, lastNameInput, phoneInput, dobInput;
    private AutoCompleteTextView genderDropdown;
    private MaterialButton updateButton;
    private ImageView btnBack, btnChangePhoto;

    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private FirebaseFirestore db;
    private Uri selectedImageUri;
    private final Calendar calendar = Calendar.getInstance();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    private ActivityResultLauncher<String> pickImageLauncher;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_account_details, container, false);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        db = FirebaseFirestore.getInstance();
        
        // Register image picker
        registerImagePicker();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize views
        initViews(view);
        setupListeners();
        
        // Load user data
        loadUserData();
        
        // Setup gender dropdown
        setupGenderDropdown();
    }

    private void initViews(View view) {
        profileImage = view.findViewById(R.id.profile_image);
        profileName = view.findViewById(R.id.profile_name);
        profileEmail = view.findViewById(R.id.profile_email);
        firstNameInput = view.findViewById(R.id.edit_first_name);
        lastNameInput = view.findViewById(R.id.edit_last_name);
        phoneInput = view.findViewById(R.id.edit_phone);
        genderDropdown = view.findViewById(R.id.dropdown_gender);
        dobInput = view.findViewById(R.id.edit_dob);
        updateButton = view.findViewById(R.id.btn_update_profile);
        btnBack = view.findViewById(R.id.btn_back);
        btnChangePhoto = view.findViewById(R.id.btn_change_photo);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> {
            // Navigate back to profile fragment
            requireActivity().getSupportFragmentManager().popBackStack();
        });
        
        btnChangePhoto.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
        
        dobInput.setOnClickListener(v -> showDatePickerDialog());
        
        updateButton.setOnClickListener(v -> updateUserProfile());
    }

    private void registerImagePicker() {
        pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    selectedImageUri = uri;
                    Glide.with(this)
                        .load(uri)
                        .circleCrop()
                        .into(profileImage);
                }
            }
        );
    }

    private void setupGenderDropdown() {
        String[] genders = new String[]{"Male", "Female", "Other", "Prefer not to say"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(), R.layout.dropdown_item, genders);
        genderDropdown.setAdapter(adapter);
    }

    private void loadUserData() {
        if (currentUser != null) {
            // Set header information
            profileName.setText(currentUser.getDisplayName() != null ? 
                    currentUser.getDisplayName() : "User");
            profileEmail.setText(currentUser.getEmail());
            
            // Load profile image if available
            if (currentUser.getPhotoUrl() != null) {
                Glide.with(this)
                    .load(currentUser.getPhotoUrl())
                    .circleCrop()
                    .placeholder(R.drawable.default_profile_image)
                    .into(profileImage);
            }
            
            // Get additional user data from Firestore
            db.collection("users").document(currentUser.getUid())
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        // Split full name into first and last name
                        String fullName = currentUser.getDisplayName();
                        if (fullName != null && fullName.contains(" ")) {
                            String[] names = fullName.split(" ", 2);
                            firstNameInput.setText(names[0]);
                            lastNameInput.setText(names[1]);
                        } else {
                            firstNameInput.setText(fullName);
                        }
                        
                        // Set other fields from Firestore data
                        if (document.contains("phone")) {
                            phoneInput.setText(document.getString("phone"));
                        }
                        if (document.contains("gender")) {
                            genderDropdown.setText(document.getString("gender"), false);
                        }
                        if (document.contains("dob")) {
                            dobInput.setText(document.getString("dob"));
                        }
                    }
                });
        }
    }

    private void showDatePickerDialog() {
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                requireContext(),
                (view, year, month, dayOfMonth) -> {
                    calendar.set(Calendar.YEAR, year);
                    calendar.set(Calendar.MONTH, month);
                    calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    dobInput.setText(dateFormat.format(calendar.getTime()));
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.show();
    }

    private void updateUserProfile() {
        if (currentUser == null) return;
        
        // Show loading state
        updateButton.setEnabled(false);
        updateButton.setText("Updating...");
        
        // Get form values
        String firstName = firstNameInput.getText().toString().trim();
        String lastName = lastNameInput.getText().toString().trim();
        String phone = phoneInput.getText().toString().trim();
        String gender = genderDropdown.getText().toString();
        String dob = dobInput.getText().toString().trim();
        
        // Validate form
        if (firstName.isEmpty() || lastName.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter your name", Toast.LENGTH_SHORT).show();
            updateButton.setEnabled(true);
            updateButton.setText("Update Profile");
            return;
        }
        
        // Update display name in Firebase Auth
        String fullName = firstName + " " + lastName;
        UserProfileChangeRequest.Builder profileUpdates = new UserProfileChangeRequest.Builder()
                .setDisplayName(fullName);
        
        // Update profile image if selected
        if (selectedImageUri != null) {
            // In a real app, you would upload the image to Firebase Storage and get a URL
            // For this example, we'll just keep the local URI
            profileUpdates.setPhotoUri(selectedImageUri);
        }
        
        // Update Firebase Auth profile
        currentUser.updateProfile(profileUpdates.build())
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        // Update additional data in Firestore
                        updateFirestoreData(firstName, lastName, phone, gender, dob);
                    } else {
                        Toast.makeText(requireContext(),
                                "Failed to update profile: " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
                        updateButton.setEnabled(true);
                        updateButton.setText("Update Profile");
                    }
                });
    }

    private void updateFirestoreData(String firstName, String lastName, String phone, String gender, String dob) {
        DocumentReference userRef = db.collection("users").document(currentUser.getUid());
        
        Map<String, Object> updates = new HashMap<>();
        updates.put("firstName", firstName);
        updates.put("lastName", lastName);
        updates.put("fullName", firstName + " " + lastName);
        updates.put("phone", phone);
        
        if (!gender.isEmpty()) {
            updates.put("gender", gender);
        }
        
        if (!dob.isEmpty()) {
            updates.put("dob", dob);
        }
        
        userRef.update(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(requireContext(),
                            "Profile updated successfully", Toast.LENGTH_SHORT).show();
                    // Go back to profile fragment
                    requireActivity().getSupportFragmentManager().popBackStack();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(requireContext(),
                            "Failed to update profile data: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    updateButton.setEnabled(true);
                    updateButton.setText("Update Profile");
                });
    }
}
