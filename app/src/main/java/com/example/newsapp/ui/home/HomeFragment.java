package com.example.newsapp.ui.home;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.core.widget.NestedScrollView;
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
    private NestedScrollView scrollView;
    
    private ProgressBar featuredLoadingIndicator;
    private ProgressBar newsLoadingIndicator;
    private TextView featuredEmptyText;
    private TextView newsEmptyText;
    private Button loadMoreButton;
    private ProgressBar paginationLoadingIndicator;
    
    private CategoryAdapter categoryAdapter;
    private NewsAdapter featuredAdapter;
    private NewsAdapter newsAdapter;
    private List<String> categories = Arrays.asList("Technology", "Business", "Sports", "Entertainment", "Health");
    private String currentCategory = "technology";
    
    private NewsRepository newsRepository;
    private View rootView;
    
    // Pagination variables
    private int currentPage = 1;
    private boolean hasMorePages = false;
    private boolean isLoadingMore = false;

    // Loading timeout variables
    private static final long LOADING_TIMEOUT = 10000; // 10 seconds
    private Runnable timeoutRunnable;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_home, container, false);
        
        // Initialize repository
        newsRepository = new NewsRepository(requireContext());
        
        setupViews(rootView);
        setupCategoriesRecyclerView();
        setupFeaturedRecyclerView();
        setupNewsRecyclerView();
        setupPaginationControls();
        
        loadNewsForCategory(currentCategory);
        
        return rootView;
    }
    
    private void setupViews(View view) {
        scrollView = view.findViewById(R.id.scroll_view);
        categoriesRecyclerView = view.findViewById(R.id.categories_recycler_view);
        featuredRecyclerView = view.findViewById(R.id.featured_recycler_view);
        newsRecyclerView = view.findViewById(R.id.news_recycler_view);
        
        featuredLoadingIndicator = view.findViewById(R.id.featured_loading_indicator);
        newsLoadingIndicator = view.findViewById(R.id.news_loading_indicator);
        featuredEmptyText = view.findViewById(R.id.featured_empty_text);
        newsEmptyText = view.findViewById(R.id.news_empty_text);
        loadMoreButton = view.findViewById(R.id.load_more_button);
        paginationLoadingIndicator = view.findViewById(R.id.pagination_loading_indicator);
    }
    
    private void setupPaginationControls() {
        loadMoreButton.setOnClickListener(v -> {
            if (hasMorePages && !isLoadingMore) {
                loadNextPage();
            }
        });
    }
    
    private void loadNextPage() {
        if (!isLoadingMore) {
            isLoadingMore = true;
            currentPage++;
            
            // Show loading indicator for pagination
            loadMoreButton.setVisibility(View.GONE);
            paginationLoadingIndicator.setVisibility(View.VISIBLE);
            
            // Set a timeout to handle potential API issues
            setupLoadingTimeout(() -> {
                if (isAdded() && isLoadingMore) {
                    isLoadingMore = false;
                    paginationLoadingIndicator.setVisibility(View.GONE);
                    loadMoreButton.setVisibility(hasMorePages ? View.VISIBLE : View.GONE);
                    showOfflineMessage("Loading timeout. Please try again.");
                }
            });
            
            newsRepository.loadMoreNews(currentCategory, currentPage, 
                new NewsRepository.PaginatedNewsCallback() {
                    @Override
                    public void onSuccess(List<Article> articles) {
                        // This method won't be called directly
                    }

                    @Override
                    public void onError(String message) {
                        if (isAdded()) {
                            cancelLoadingTimeout();
                            isLoadingMore = false;
                            paginationLoadingIndicator.setVisibility(View.GONE);
                            loadMoreButton.setVisibility(hasMorePages ? View.VISIBLE : View.GONE);
                            showOfflineMessage(message);
                        }
                    }

                    @Override
                    public void onSuccessWithHasMore(List<Article> articles, boolean morePages) {
                        if (isAdded()) {
                            cancelLoadingTimeout();
                            isLoadingMore = false;
                            paginationLoadingIndicator.setVisibility(View.GONE);
                            
                            // Update UI with the new articles
                            if (articles.isEmpty()) {
                                showOfflineMessage("No more articles found.");
                                hasMorePages = false;
                            } else {
                                newsAdapter.updateArticles(articles);
                                
                                // Update pagination state
                                hasMorePages = morePages;
                            }
                            
                            loadMoreButton.setVisibility(hasMorePages ? View.VISIBLE : View.GONE);
                            
                            // Scroll to show new content
                            scrollView.post(() -> {
                                // Calculate position to scroll to - a few items back
                                int scrollPosition = Math.max(0, newsAdapter.getItemCount() - 5);
                                if (scrollPosition < newsAdapter.getItemCount()) {
                                    newsRecyclerView.smoothScrollToPosition(scrollPosition);
                                }
                            });
                        }
                    }
                });
        }
    }
    
    private void loadNewsForCategory(String category) {
        // Reset pagination
        currentPage = 1;
        hasMorePages = false;
        isLoadingMore = false;
        
        // Hide pagination controls
        loadMoreButton.setVisibility(View.GONE);
        paginationLoadingIndicator.setVisibility(View.GONE);
        
        // Hide error messages
        featuredEmptyText.setVisibility(View.GONE);
        newsEmptyText.setVisibility(View.GONE);
        
        // Show loading state
        setFeaturedLoadingState(true);
        setNewsLoadingState(true);
        
        // Initialize adapters with empty lists (important to show loading properly)
        featuredAdapter.updateArticles(new ArrayList<>());
        newsAdapter.updateArticles(new ArrayList<>());
        
        // Set timeouts to handle potential API issues
        setupLoadingTimeout(() -> {
            if (isAdded()) {
                // Handle featured news timeout
                setFeaturedLoadingState(false);
                if (featuredAdapter.getItemCount() == 0) {
                    featuredEmptyText.setVisibility(View.VISIBLE);
                }
                
                // Handle regular news timeout
                setNewsLoadingState(false);
                if (newsAdapter.getItemCount() == 0) {
                    newsEmptyText.setVisibility(View.VISIBLE);
                }
                
                showOfflineMessage("Loading timeout. Please check your connection.");
            }
        });
        
        Log.d(TAG, "Loading news for category: " + category);
        
        // Track completion status for both API calls
        final boolean[] featuredCompleted = {false};
        final boolean[] regularCompleted = {false};
        
        // Load featured news
        newsRepository.getNewsByCategory(category, true, 5, new NewsRepository.NewsCallback() {
            @Override
            public void onSuccess(List<Article> articles) {
                if (isAdded()) {
                    featuredCompleted[0] = true;
                    setFeaturedLoadingState(false);
                    Log.d(TAG, "Featured news loaded, count: " + articles.size());
                    
                    if (articles.isEmpty()) {
                        featuredEmptyText.setVisibility(View.VISIBLE);
                    } else {
                        featuredEmptyText.setVisibility(View.GONE);
                        featuredAdapter.updateArticles(articles);
                    }
                    
                    // Check if both calls completed
                    checkBothCompleted(featuredCompleted[0], regularCompleted[0]);
                }
            }

            @Override
            public void onError(String message) {
                if (isAdded()) {
                    featuredCompleted[0] = true;
                    setFeaturedLoadingState(false);
                    Log.e(TAG, "Featured news error: " + message);
                    
                    // Check if we have items already (from cache)
                    if (featuredAdapter.getItemCount() == 0) {
                        featuredEmptyText.setVisibility(View.VISIBLE);
                    }
                    
                    showOfflineMessage(message);
                    
                    // Check if both calls completed
                    checkBothCompleted(featuredCompleted[0], regularCompleted[0]);
                }
            }
        });
        
        // Load regular news with pagination
        newsRepository.getNewsByCategory(category, false, 5, 
            new NewsRepository.PaginatedNewsCallback() {
                @Override
                public void onSuccess(List<Article> articles) {
                    // This is handled in onSuccessWithHasMore
                    Log.d(TAG, "Regular onSuccess called with: " + articles.size());
                    
                    if (isAdded()) {
                        regularCompleted[0] = true;
                        setNewsLoadingState(false);
                        
                        if (articles.isEmpty()) {
                            newsEmptyText.setVisibility(View.VISIBLE);
                        } else {
                            newsEmptyText.setVisibility(View.GONE);
                            newsAdapter.updateArticles(articles);
                        }
                        
                        // Check if both calls completed
                        checkBothCompleted(featuredCompleted[0], regularCompleted[0]);
                    }
                }

                @Override
                public void onError(String message) {
                    if (isAdded()) {
                        cancelLoadingTimeout();
                        regularCompleted[0] = true;
                        setNewsLoadingState(false);
                        Log.e(TAG, "Regular news error: " + message);
                        
                        // Check if we have items already (from cache)
                        if (newsAdapter.getItemCount() == 0) {
                            newsEmptyText.setVisibility(View.VISIBLE);
                        }
                        
                        showOfflineMessage(message);
                        
                        // Check if both calls completed
                        checkBothCompleted(featuredCompleted[0], regularCompleted[0]);
                    }
                }

                @Override
                public void onSuccessWithHasMore(List<Article> articles, boolean morePages) {
                    if (isAdded()) {
                        cancelLoadingTimeout();
                        regularCompleted[0] = true;
                        setNewsLoadingState(false);
                        Log.d(TAG, "Regular news loaded with pagination, count: " + articles.size() + ", hasMore: " + morePages);
                        
                        if (articles.isEmpty()) {
                            newsEmptyText.setVisibility(View.VISIBLE);
                        } else {
                            newsEmptyText.setVisibility(View.GONE);
                            newsAdapter.updateArticles(articles);
                            
                            // Update pagination state
                            hasMorePages = morePages;
                            loadMoreButton.setVisibility(hasMorePages ? View.VISIBLE : View.GONE);
                        }
                        
                        // Check if both calls completed
                        checkBothCompleted(featuredCompleted[0], regularCompleted[0]);
                    }
                }
            });
    }
    
    private void checkBothCompleted(boolean featuredDone, boolean regularDone) {
        if (featuredDone && regularDone) {
            // Both API calls have completed (success or error)
            cancelLoadingTimeout();
            
            // Ensure that both loading indicators are properly hidden
            setFeaturedLoadingState(false);
            setNewsLoadingState(false);
            
            // Log completion status
            Log.d(TAG, "Both featured and regular news calls completed");
        }
    }
    
    private void setupLoadingTimeout(Runnable action) {
        cancelLoadingTimeout(); // Cancel any existing timeout
        timeoutRunnable = action;
        if (getView() != null) {
            getView().postDelayed(timeoutRunnable, LOADING_TIMEOUT);
        }
    }
    
    private void cancelLoadingTimeout() {
        if (timeoutRunnable != null && getView() != null) {
            getView().removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
        }
    }
    
    private void setFeaturedLoadingState(boolean isLoading) {
        if (featuredLoadingIndicator != null && featuredRecyclerView != null) {
            featuredLoadingIndicator.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            featuredRecyclerView.setVisibility(isLoading ? View.GONE : View.VISIBLE);
        }
    }
    
    private void setNewsLoadingState(boolean isLoading) {
        if (newsLoadingIndicator != null && newsRecyclerView != null) {
            newsLoadingIndicator.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            newsRecyclerView.setVisibility(isLoading ? View.GONE : View.VISIBLE);
        }
    }
    
    private void showOfflineMessage(String message) {
        if (rootView != null) {
            Snackbar.make(rootView, message, Snackbar.LENGTH_LONG)
                    .setAction("Retry", v -> loadNewsForCategory(currentCategory))
                    .show();
        }
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
    
    @Override
    public void onDestroy() {
        cancelLoadingTimeout();
        super.onDestroy();
    }
}