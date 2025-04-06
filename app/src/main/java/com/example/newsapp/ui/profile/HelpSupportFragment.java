package com.example.newsapp.ui.profile;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.newsapp.R;

public class HelpSupportFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_help_support, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Setup back button
        ImageView btnBack = view.findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());
        
        // Setup help options with expandable content
        setupHelpOptions(view);
    }
    
    private void setupHelpOptions(View view) {
        // FAQs expandable section
        setupExpandableSection(
            view.findViewById(R.id.faq_header),
            view.findViewById(R.id.faq_content),
            view.findViewById(R.id.faq_expand_icon)
        );
        
        // Contact Us option
        LinearLayout contactOption = view.findViewById(R.id.option_contact_us);
        if (contactOption != null) {
            contactOption.setOnClickListener(v -> {
                // Open email intent
                Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
                emailIntent.setData(Uri.parse("mailto:support@newsapp.com"));
                emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Support Request");
                
                try {
                    startActivity(Intent.createChooser(emailIntent, "Send email using..."));
                } catch (android.content.ActivityNotFoundException ex) {
                    Toast.makeText(requireContext(), "No email clients installed", Toast.LENGTH_SHORT).show();
                }
            });
        }
        
        // Report Issue expandable section
        setupExpandableSection(
            view.findViewById(R.id.report_issue_header),
            view.findViewById(R.id.report_issue_content),
            view.findViewById(R.id.report_issue_expand_icon)
        );
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
