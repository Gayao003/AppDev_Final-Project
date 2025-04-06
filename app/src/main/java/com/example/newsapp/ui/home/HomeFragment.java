package com.example.newsapp.ui.home;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.newsapp.R;
import com.example.newsapp.data.models.Article;
import com.example.newsapp.data.repository.NewsRepository;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class HomeFragment extends Fragment {
    private static final String TAG = "HomeFragment";
    
    private RecyclerView categoriesRecyclerView;
    private RecyclerView featuredRecyclerView;
    private RecyclerView newsRecyclerView;
    
    private ProgressBar featuredLoadingIndicator;
    private ProgressBar newsLoadingIndicator;
    private TextView featuredEmptyText;
    private TextView newsEmptyText;
    
    private CategoryAdapter categoryAdapter;
    private NewsAdapter featuredAdapter;
    private NewsAdapter newsAdapter;
    private List<String> categories = Arrays.asList("Technology", "Business", "Sports", "Entertainment", "Health");
    private String currentCategory = "technology";
    
    private NewsRepository newsRepository;
    private View rootView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_home, container, false);
        
        // Initialize repository
        newsRepository = new NewsRepository(requireContext());
        
        setupViews(rootView);
        setupCategoriesRecyclerView();
        setupFeaturedRecyclerView();
        setupNewsRecyclerView();
        
        loadNewsForCategory(currentCategory);
        
        return rootView;
    }
    
    private void setupViews(View view) {
        categoriesRecyclerView = view.findViewById(R.id.categories_recycler_view);
        featuredRecyclerView = view.findViewById(R.id.featured_recycler_view);
        newsRecyclerView = view.findViewById(R.id.news_recycler_view);
        
        featuredLoadingIndicator = view.findViewById(R.id.featured_loading_indicator);
        newsLoadingIndicator = view.findViewById(R.id.news_loading_indicator);
        featuredEmptyText = view.findViewById(R.id.featured_empty_text);
        newsEmptyText = view.findViewById(R.id.news_empty_text);
    }
    
    private void loadNewsForCategory(String category) {
        // Show loading state
        setFeaturedLoadingState(true);
        setNewsLoadingState(true);
        
        Log.d(TAG, "Loading news for category: " + category);
        
        // Load featured news
        newsRepository.getNewsByCategory(category, true, 5, new NewsRepository.NewsCallback() {
            @Override
            public void onSuccess(List<Article> articles) {
                if (isAdded()) {
                    setFeaturedLoadingState(false);
                    if (articles.isEmpty()) {
                        featuredEmptyText.setVisibility(View.VISIBLE);
                    } else {
                        featuredEmptyText.setVisibility(View.GONE);
                        featuredAdapter.updateArticles(articles);
                    }
                }
            }

            @Override
            public void onError(String message) {
                if (isAdded()) {
                    setFeaturedLoadingState(false);
                    
                    // Check if we have items already (from cache)
                    if (featuredAdapter.getItemCount() == 0) {
                        featuredEmptyText.setVisibility(View.VISIBLE);
                    }
                    
                    showOfflineMessage(message);
                }
            }
        });
        
        // Load regular news
        newsRepository.getNewsByCategory(category, false, 15, new NewsRepository.NewsCallback() {
            @Override
            public void onSuccess(List<Article> articles) {
                if (isAdded()) {
                    setNewsLoadingState(false);
                    if (articles.isEmpty()) {
                        newsEmptyText.setVisibility(View.VISIBLE);
                    } else {
                        newsEmptyText.setVisibility(View.GONE);
                        newsAdapter.updateArticles(articles);
                    }
                }
            }

            @Override
            public void onError(String message) {
                if (isAdded()) {
                    setNewsLoadingState(false);
                    
                    // Check if we have items already (from cache)
                    if (newsAdapter.getItemCount() == 0) {
                        newsEmptyText.setVisibility(View.VISIBLE);
                    }
                    
                    showOfflineMessage(message);
                }
            }
        });
    }
    
    private void setFeaturedLoadingState(boolean isLoading) {
        featuredLoadingIndicator.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        featuredRecyclerView.setVisibility(isLoading ? View.GONE : View.VISIBLE);
    }
    
    private void setNewsLoadingState(boolean isLoading) {
        newsLoadingIndicator.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        newsRecyclerView.setVisibility(isLoading ? View.GONE : View.VISIBLE);
    }
    
    private void showOfflineMessage(String message) {
        Snackbar.make(rootView, message, Snackbar.LENGTH_LONG)
                .setAction("Retry", v -> loadNewsForCategory(currentCategory))
                .show();
    }

    private void setupCategoriesRecyclerView() {
        categoriesRecyclerView.setLayoutManager(
            new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        categoryAdapter = new CategoryAdapter(categories, category -> {
            currentCategory = category.toLowerCase();
            loadNewsForCategory(currentCategory);
        });
        categoriesRecyclerView.setAdapter(categoryAdapter);
    }

    private void setupFeaturedRecyclerView() {
        featuredRecyclerView.setLayoutManager(
            new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        featuredAdapter = new NewsAdapter(new ArrayList<>(), true);
        featuredRecyclerView.setAdapter(featuredAdapter);
    }

    private void setupNewsRecyclerView() {
        newsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        newsAdapter = new NewsAdapter(new ArrayList<>(), false);
        newsRecyclerView.setAdapter(newsAdapter);
    }
}