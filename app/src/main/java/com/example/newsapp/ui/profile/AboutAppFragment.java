package com.example.newsapp.ui.profile;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.newsapp.R;

public class AboutAppFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_about_app, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Setup back button
        ImageView btnBack = view.findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());
        
        // Setup app version
        setupAppVersion(view);
        
        // Setup about options with expandable content
        setupAboutOptions(view);
    }
    
    private void setupAppVersion(View view) {
        TextView versionText = view.findViewById(R.id.app_version);
        
        try {
            String versionName = requireActivity().getPackageManager()
                    .getPackageInfo(requireActivity().getPackageName(), 0).versionName;
            versionText.setText("Version " + versionName);
        } catch (PackageManager.NameNotFoundException e) {
            versionText.setText("Version 1.0");
        }
    }
    
    private void setupAboutOptions(View view) {
        // Terms of Service expandable section
        setupExpandableSection(
            view.findViewById(R.id.terms_header),
            view.findViewById(R.id.terms_content),
            view.findViewById(R.id.terms_expand_icon)
        );

        // Privacy Policy expandable section
        setupExpandableSection(
            view.findViewById(R.id.privacy_header),
            view.findViewById(R.id.privacy_content),
            view.findViewById(R.id.privacy_expand_icon)
        );

        // Rate App option
        LinearLayout rateOption = view.findViewById(R.id.option_rate_app);
        if (rateOption != null) {
            rateOption.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, 
                            Uri.parse("market://details?id=" + requireActivity().getPackageName())));
                } catch (android.content.ActivityNotFoundException e) {
                    startActivity(new Intent(Intent.ACTION_VIEW, 
                            Uri.parse("https://play.google.com/store/apps/details?id=" + requireActivity().getPackageName())));
                }
            });
        }
        
        // Visit Website option
        LinearLayout websiteOption = view.findViewById(R.id.option_visit_website);
        if (websiteOption != null) {
            websiteOption.setOnClickListener(v -> {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.newsapp.com"));
                startActivity(browserIntent);
            });
        }
    }

    private void setupExpandableSection(View header, View content, ImageView expandIcon) {
        if (header == null || content == null || expandIcon == null) return;

        header.setOnClickListener(v -> {
            boolean isExpanded = content.getVisibility() == View.VISIBLE;

            // Toggle content visibility
            content.setVisibility(isExpanded ? View.GONE : View.VISIBLE);

            // Change the icon based on expanded state
            expandIcon.setImageResource(isExpanded ?
                    R.drawable.ic_expand_more : R.drawable.ic_expand_less);

            // Animate the icon rotation
            expandIcon.animate()
                    .rotation(isExpanded ? 0 : 180)
                    .setDuration(300)
                    .start();
        });
    }
}
