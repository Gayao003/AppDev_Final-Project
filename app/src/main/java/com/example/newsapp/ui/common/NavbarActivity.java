package com.example.newsapp.ui.common;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.example.newsapp.LoginActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.example.newsapp.R;
import com.example.newsapp.ui.profile.ProfileFragment;
import com.example.newsapp.ui.home.HomeFragment;
import com.example.newsapp.ui.categories.CategoriesFragment;
import com.example.newsapp.ui.search.SearchFragment;
import com.example.newsapp.ui.bookmarks.BookmarksFragment;

public class NavbarActivity extends AppCompatActivity {
    private BottomNavigationView bottomNavigationView;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_navbar);

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        // Check if user is logged in
        if (currentUser == null) {
            // User not logged in, redirect to login
            startActivity(new Intent(NavbarActivity.this, LoginActivity.class));
            finish();
            return;
        }

        // Load the ProfileFragment by default
        loadFragment(new ProfileFragment());

        // Setup bottom navigation
        setupBottomNavigation();
    }

    private void loadFragment(Fragment fragment) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.fragment_container, fragment);
        fragmentTransaction.commit();
    }

    private void setupBottomNavigation() {
        bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setOnNavigationItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            int id = item.getItemId();

            if (id == R.id.navigation_profile) {
                selectedFragment = new ProfileFragment();
            } else if (id == R.id.navigation_home) {
                selectedFragment = new HomeFragment();
            } else if (id == R.id.navigation_categories) {
                selectedFragment = new CategoriesFragment();
            } else if (id == R.id.navigation_search) {
                selectedFragment = new SearchFragment();
            } else if (id == R.id.navigation_bookmarks) {
                selectedFragment = new BookmarksFragment();
            }

            if (selectedFragment != null) {
                loadFragment(selectedFragment);
            }
            return true;
        });

    }
}