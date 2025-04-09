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
import com.example.newsapp.data.repository.BookmarkSyncRepository;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.HashSet;

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
    private BookmarkSyncRepository bookmarkSyncRepository;
    private View rootView;
    
    // Pagination variables
    private int currentPage = 1;
    private boolean hasMorePages = false;
    private boolean isLoadingMore = false;

    // Loading timeout variables
    private static final long LOADING_TIMEOUT = 10000; // 10 seconds
    private Runnable timeoutRunnable;

    private static final int MAX_RETRY_PAGES = 3; // Maximum number of pages to try when looking for new articles
    private int retryCount = 0;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_home, container, false);
        
        // Initialize repositories
        newsRepository = new NewsRepository(requireContext());
        bookmarkSyncRepository = new BookmarkSyncRepository(requireContext());
        
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
        
        // Make Load More button visible by default
        loadMoreButton.setVisibility(View.VISIBLE);
        Log.d(TAG, "Load More button initialized as VISIBLE");
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
            retryCount = 0; // Reset retry count for each new load more request
            
            Log.d(TAG, "Loading page " + currentPage);
            
            // Show loading indicator for pagination
            loadMoreButton.setVisibility(View.GONE);
            paginationLoadingIndicator.setVisibility(View.VISIBLE);
            
            // Set a timeout to handle potential API issues
            setupLoadingTimeout(() -> {
                if (isAdded() && isLoadingMore) {
                    isLoadingMore = false;
                    paginationLoadingIndicator.setVisibility(View.GONE);
                    loadMoreButton.setVisibility(View.VISIBLE); // Always show button after timeout
                    Log.d(TAG, "Loading timeout - showing Load More button again");
                    showOfflineMessage("Loading timeout. Please try again.");
                }
            });
            
            tryLoadPage(currentPage);
        }
    }
    
    private void tryLoadPage(int page) {
        Log.d(TAG, "Trying to load page " + page + " (attempt " + (retryCount + 1) + ")");
        
        newsRepository.loadMoreNews(currentCategory, page, 
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
                        loadMoreButton.setVisibility(View.VISIBLE); // Always show button on error
                        Log.d(TAG, "Error loading page " + page + ": " + message);
                        showOfflineMessage("Error: " + message);
                    }
                }

                @Override
                public void onSuccessWithHasMore(List<Article> articles, boolean morePages) {
                    if (isAdded()) {
                        Log.d(TAG, "Page " + page + " loaded with " + articles.size() + " articles, hasMore=" + morePages);
                        
                        // Update UI with the new articles
                        cancelLoadingTimeout();
                        isLoadingMore = false;
                        paginationLoadingIndicator.setVisibility(View.GONE);
                        
                        if (articles.isEmpty()) {
                            // No articles in this page
                            if (morePages) {
                                // API says there are more pages, so show the load more button
                                loadMoreButton.setVisibility(View.VISIBLE);
                                Log.d(TAG, "No articles in this page but API indicates more pages exist");
                                showOfflineMessage("No new articles found. You can try loading more.");
                            } else {
                                // No more pages according to API
                                loadMoreButton.setVisibility(View.GONE);
                                hasMorePages = false;
                                Log.d(TAG, "No more articles available according to API");
                                showOfflineMessage("No more articles available.");
                            }
                        } else {
                            // We got some articles, add them to the adapter
                            // Get existing articles
                            List<Article> existingArticles = newsAdapter.getArticles();
                            Log.d(TAG, "Current articles: " + existingArticles.size());
                            
                            // Create a new list combining existing and new articles
                            List<Article> combinedArticles = new ArrayList<>(existingArticles);
                            
                            // Add only articles that don't already exist (by URL)
                            Set<String> existingUrls = new HashSet<>();
                            for (Article article : existingArticles) {
                                if (article.getUrl() != null) {
                                    existingUrls.add(article.getUrl());
                                }
                            }
                            
                            int newArticlesAdded = 0;
                            for (Article article : articles) {
                                if (article.getUrl() != null && !existingUrls.contains(article.getUrl())) {
                                    combinedArticles.add(article);
                                    existingUrls.add(article.getUrl());
                                    newArticlesAdded++;
                                }
                            }
                            
                            Log.d(TAG, "Adding " + newArticlesAdded + " new unique articles out of " + articles.size() + " received");
                            
                            // Update adapter with combined list
                            newsAdapter.updateArticles(combinedArticles);
                            
                            // Update pagination state
                            hasMorePages = morePages;
                            Log.d(TAG, "Setting hasMorePages to " + hasMorePages);
                            
                            // Show load more button if there are more pages
                            loadMoreButton.setVisibility(hasMorePages ? View.VISIBLE : View.GONE);
                            
                            if (newArticlesAdded > 0) {
                                // Scroll to show new content
                                scrollView.post(() -> {
                                    // Calculate position to scroll to - a few items back from where new content begins
                                    int scrollPosition = Math.max(0, existingArticles.size() - 1);
                                    if (scrollPosition < newsAdapter.getItemCount()) {
                                        newsRecyclerView.smoothScrollToPosition(scrollPosition);
                                    }
                                });
                            } else if (morePages) {
                                // No new articles added but API says there are more pages
                                // Let's try loading the next page automatically
                                if (retryCount < MAX_RETRY_PAGES) {
                                    retryCount++;
                                    currentPage++;
                                    Log.d(TAG, "No new articles added, automatically trying page " + currentPage);
                                    tryLoadPage(currentPage);
                                } else {
                                    // Too many retries, just show the load more button
                                    showOfflineMessage("Try loading more for additional articles.");
                                }
                            }
                        }
                    }
                }
            });
    }
    
    private void loadNewsForCategory(String category) {
        // Reset pagination
        currentPage = 1;
        hasMorePages = true; // Assume there are more pages by default
        isLoadingMore = false;
        retryCount = 0;
        
        // Reset repository state
        newsRepository.resetArticles();
        
        // Initially show the Load More button even before data loads
        loadMoreButton.setVisibility(View.VISIBLE);
        Log.d(TAG, "Reset pagination and showing Load More button");
        
        // Initially hide the pagination loading indicator
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
        
        // Load featured news (still using 5 for featured)
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
        
        // Load regular news with pagination - specify 10 articles explicitly
        newsRepository.getNewsByCategory(category, false, 10, 
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
                            hasMorePages = false;
                            Log.d(TAG, "No articles returned, disabling pagination");
                        } else {
                            newsEmptyText.setVisibility(View.GONE);
                            
                            // When loading additional pages, append the new articles to the existing ones
                            if (currentPage > 1) {
                                Log.d(TAG, "Appending " + articles.size() + " new articles to existing list");
                                // Get existing articles
                                List<Article> existingArticles = newsAdapter.getArticles();
                                // Create new combined list
                                List<Article> combinedArticles = new ArrayList<>(existingArticles);
                                // Add new articles
                                combinedArticles.addAll(articles);
                                // Update adapter with combined list
                                newsAdapter.updateArticles(combinedArticles);
                                Log.d(TAG, "Total articles after append: " + combinedArticles.size());
                            } else {
                                // First page - just use the articles directly
                                newsAdapter.updateArticles(articles);
                                Log.d(TAG, "First page - setting " + articles.size() + " articles");
                            }
                            
                            // Update pagination state
                            hasMorePages = morePages;
                            Log.d(TAG, "Setting hasMorePages to " + hasMorePages);
                        }
                        
                        // Show the load more button if there are more pages
                        if (hasMorePages) {
                            loadMoreButton.setVisibility(View.VISIBLE);
                            Log.d(TAG, "Showing Load More button");
                        } else {
                            loadMoreButton.setVisibility(View.GONE);
                            Log.d(TAG, "Hiding Load More button");
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
        List<Article> emptyList = new ArrayList<>();
        featuredAdapter = new NewsAdapter(emptyList, true, bookmarkSyncRepository);
        featuredRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        featuredRecyclerView.setAdapter(featuredAdapter);
    }

    private void setupNewsRecyclerView() {
        List<Article> emptyList = new ArrayList<>();
        newsAdapter = new NewsAdapter(emptyList, false, bookmarkSyncRepository);
        newsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        newsRecyclerView.setAdapter(newsAdapter);
    }
    
    @Override
    public void onDestroy() {
        cancelLoadingTimeout();
        super.onDestroy();
    }
}