package com.example.newsapp.ui.search;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.newsapp.R;
import com.example.newsapp.data.models.Article;
import com.example.newsapp.data.repository.NewsRepository;
import com.example.newsapp.ui.home.NewsAdapter;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SearchFragment extends Fragment {
    private static final String TAG = "SearchFragment";
    
    private EditText searchEditText;
    private Button searchButton;
    private RecyclerView resultsRecyclerView;
    private ProgressBar loadingIndicator;
    private TextView emptyResultsText;
    private ChipGroup recentSearchesChipGroup;
    
    private NewsAdapter resultsAdapter;
    private NewsRepository newsRepository;
    
    // Popular searches suggestions
    private final List<String> popularSearches = Arrays.asList(
            "Technology", "Climate Change", "Sports", "Politics", "Health"
    );
    
    // Recent searches (would ideally be stored in SharedPreferences)
    private final List<String> recentSearches = new ArrayList<>();
    private static final int MAX_RECENT_SEARCHES = 5;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_search, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Initialize repository
        newsRepository = new NewsRepository(requireContext());
        
        // Initialize views
        searchEditText = view.findViewById(R.id.search_edit_text);
        searchButton = view.findViewById(R.id.search_button);
        resultsRecyclerView = view.findViewById(R.id.search_results_recycler_view);
        loadingIndicator = view.findViewById(R.id.search_loading_indicator);
        emptyResultsText = view.findViewById(R.id.empty_results_text);
        recentSearchesChipGroup = view.findViewById(R.id.recent_searches_chip_group);
        
        // Setup RecyclerView
        resultsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        resultsAdapter = new NewsAdapter(new ArrayList<>(), false);
        resultsRecyclerView.setAdapter(resultsAdapter);
        
        // Setup search button
        searchButton.setOnClickListener(v -> performSearch());
        
        // Setup search on keyboard action
        searchEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch();
                return true;
            }
            return false;
        });
        
        // Add text change listener to enable/disable search button
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchButton.setEnabled(s.length() > 0);
            }
            
            @Override
            public void afterTextChanged(Editable s) {}
        });
        
        // Initialize with popular searches
        setupPopularSearches(view);
        
        // Show initial empty state
        showEmptyState(true);
    }
    
    private void setupPopularSearches(View view) {
        ChipGroup popularSearchesChipGroup = view.findViewById(R.id.popular_searches_chip_group);
        popularSearchesChipGroup.removeAllViews();
        
        for (String query : popularSearches) {
            Chip chip = new Chip(requireContext());
            chip.setText(query);
            chip.setChipBackgroundColorResource(R.color.chip_background);
            chip.setTextColor(getResources().getColor(R.color.chip_text));
            chip.setClickable(true);
            chip.setCheckable(false);
            
            chip.setOnClickListener(v -> {
                searchEditText.setText(query);
                performSearch();
            });
            
            popularSearchesChipGroup.addView(chip);
        }
        
        // Also load recent searches if any
        updateRecentSearchesChips();
    }
    
    private void updateRecentSearchesChips() {
        TextView recentSearchesTitle = requireView().findViewById(R.id.recent_searches_title);
        recentSearchesChipGroup.removeAllViews();
        
        if (recentSearches.isEmpty()) {
            recentSearchesTitle.setVisibility(View.GONE);
            recentSearchesChipGroup.setVisibility(View.GONE);
            return;
        }
        
        recentSearchesTitle.setVisibility(View.VISIBLE);
        recentSearchesChipGroup.setVisibility(View.VISIBLE);
        
        for (String query : recentSearches) {
            Chip chip = new Chip(requireContext());
            chip.setText(query);
            chip.setChipBackgroundColorResource(R.color.chip_background);
            chip.setTextColor(getResources().getColor(R.color.chip_text));
            chip.setCloseIconVisible(true);
            chip.setClickable(true);
            chip.setCheckable(false);
            
            chip.setOnClickListener(v -> {
                searchEditText.setText(query);
                performSearch();
            });
            
            chip.setOnCloseIconClickListener(v -> {
                recentSearches.remove(query);
                updateRecentSearchesChips();
            });
            
            recentSearchesChipGroup.addView(chip);
        }
    }
    
    private void performSearch() {
        String query = searchEditText.getText().toString().trim();
        if (query.isEmpty()) {
            return;
        }
        
        // Add to recent searches
        addToRecentSearches(query);
        
        // Show loading state
        setLoadingState(true);
        
        // Clear previous results
        resultsAdapter.updateArticles(new ArrayList<>());
        
        // Perform search
        newsRepository.searchArticles(query, new NewsRepository.NewsCallback() {
            @Override
            public void onSuccess(List<Article> articles) {
                if (isAdded()) {
                    setLoadingState(false);
                    
                    if (articles.isEmpty()) {
                        showEmptyState(true);
                    } else {
                        showEmptyState(false);
                        resultsAdapter.updateArticles(articles);
                    }
                }
            }
            
            @Override
            public void onError(String message) {
                if (isAdded()) {
                    setLoadingState(false);
                    showEmptyState(true);
                    Snackbar.make(requireView(), "Search error: " + message, Snackbar.LENGTH_LONG).show();
                }
            }
        });
    }
    
    private void addToRecentSearches(String query) {
        // Remove if already exists (to reorder)
        recentSearches.remove(query);
        
        // Add to the beginning
        recentSearches.add(0, query);
        
        // Trim if needed
        if (recentSearches.size() > MAX_RECENT_SEARCHES) {
            recentSearches.remove(recentSearches.size() - 1);
        }
        
        // Update UI
        updateRecentSearchesChips();
    }
    
    private void setLoadingState(boolean isLoading) {
        loadingIndicator.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        resultsRecyclerView.setVisibility(isLoading ? View.GONE : View.VISIBLE);
    }
    
    private void showEmptyState(boolean isEmpty) {
        if (isEmpty && resultsAdapter.getItemCount() == 0) {
            emptyResultsText.setVisibility(View.VISIBLE);
            resultsRecyclerView.setVisibility(View.GONE);
        } else {
            emptyResultsText.setVisibility(View.GONE);
            resultsRecyclerView.setVisibility(View.VISIBLE);
        }
    }
}