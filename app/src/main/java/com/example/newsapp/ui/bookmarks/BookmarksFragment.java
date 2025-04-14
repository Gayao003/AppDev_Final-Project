package com.example.newsapp.ui.bookmarks;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.newsapp.R;
import com.example.newsapp.data.models.Article;
import com.example.newsapp.data.repository.BookmarkSyncRepository;
import com.example.newsapp.data.repository.NewsRepository;
import com.google.android.material.chip.Chip;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

public class BookmarksFragment extends Fragment implements BookmarksAdapter.BookmarkActionListener {
    private static final String TAG = "BookmarksFragment";
    
    private RecyclerView bookmarksRecyclerView;
    private LinearProgressIndicator loadingIndicator;
    private ConstraintLayout emptyState;
    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView syncStatusText;
    private Chip offlineIndicator;
    private boolean isOfflineMode = false;
    
    private BookmarksAdapter adapter;
    private BookmarkSyncRepository bookmarkSyncRepository;
    private NewsRepository newsRepository;
    private List<Article> bookmarkedArticles = new ArrayList<>();
    private NetworkChangeReceiver networkChangeReceiver;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Initialize the repositories
        bookmarkSyncRepository = new BookmarkSyncRepository(requireContext());
        newsRepository = new NewsRepository(requireContext());
        
        // Initialize network change receiver
        networkChangeReceiver = new NetworkChangeReceiver();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_bookmarks, container, false);
        
        // Initialize views
        bookmarksRecyclerView = rootView.findViewById(R.id.bookmarks_recycler_view);
        loadingIndicator = rootView.findViewById(R.id.loading_indicator);
        emptyState = rootView.findViewById(R.id.empty_state);
        swipeRefreshLayout = rootView.findViewById(R.id.swipe_refresh_layout);
        syncStatusText = rootView.findViewById(R.id.sync_status_text);
        offlineIndicator = rootView.findViewById(R.id.offline_indicator);
        
        // Setup RecyclerView
        setupRecyclerView();
        
        // Setup SwipeRefreshLayout
        setupSwipeRefresh();
        
        // Check network status and set offline indicator
        checkNetworkStatus();
        
        // Load bookmarked articles
        loadBookmarkedArticles();
        
        return rootView;
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // Register network change receiver
        requireActivity().registerReceiver(
            networkChangeReceiver, 
            new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        );
        
        // Check network status
        checkNetworkStatus();
        
        // Refresh bookmarks when coming back to the fragment
        loadBookmarkedArticles();
    }
    
    @Override
    public void onPause() {
        super.onPause();
        // Unregister network change receiver
        try {
            requireActivity().unregisterReceiver(networkChangeReceiver);
        } catch (IllegalArgumentException e) {
            // Receiver was not registered
            Log.e(TAG, "NetworkChangeReceiver not registered or already unregistered", e);
        }
    }
    
    private void setupRecyclerView() {
        adapter = new BookmarksAdapter(bookmarkedArticles, this, newsRepository);
        bookmarksRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        bookmarksRecyclerView.setAdapter(adapter);
    }
    
    private void setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener(this::syncBookmarks);
        swipeRefreshLayout.setColorSchemeResources(R.color.primary_color);
    }
    
    private void loadBookmarkedArticles() {
        showLoading(true);
        
        bookmarkSyncRepository.getBookmarkedArticles(new BookmarkSyncRepository.SyncCallback() {
            @Override
            public void onArticlesLoaded(List<Article> articles, boolean fromCloud) {
                if (!isAdded()) return;
                
                showLoading(false);
                
                if (articles != null && !articles.isEmpty()) {
                    bookmarkedArticles = articles;
                    adapter.updateArticles(bookmarkedArticles);
                    showEmptyState(false);
                    
                    // Update sync status
                    updateSyncStatus(fromCloud);
                } else {
                    // Show empty state
                    showEmptyState(true);
                }
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                
                Log.e(TAG, "Error loading bookmarks: " + message);
                showLoading(false);
                
                // Only show empty state if we have no articles
                if (bookmarkedArticles.isEmpty()) {
                    showEmptyState(true);
                }
                
                if (isOfflineMode) {
                    showError("Offline mode: Using locally cached bookmarks");
                } else {
                    showError("Failed to load bookmarks: " + message);
                }
            }
        });
    }
    
    private void syncBookmarks() {
        if (isOfflineMode) {
            swipeRefreshLayout.setRefreshing(false);
            showError("Cannot sync in offline mode. Please check your connection.");
            return;
        }
        
        syncStatusText.setText("Syncing bookmarks...");
        syncStatusText.setVisibility(View.VISIBLE);
        
        bookmarkSyncRepository.syncBookmarksOnLogin(new BookmarkSyncRepository.SyncCallback() {
            @Override
            public void onArticlesLoaded(List<Article> articles, boolean fromCloud) {
                if (!isAdded()) return;
                
                swipeRefreshLayout.setRefreshing(false);
                
                if (articles != null && !articles.isEmpty()) {
                    bookmarkedArticles = articles;
                    adapter.updateArticles(bookmarkedArticles);
                    showEmptyState(false);
                    
                    syncStatusText.setText("Bookmarks synced from cloud");
                    showSuccess("Bookmarks synced successfully");
                } else {
                    syncStatusText.setText("No new bookmarks found");
                }
                
                // Hide sync status after a delay
                syncStatusText.postDelayed(() -> {
                    if (isAdded()) {
                        syncStatusText.setVisibility(View.GONE);
                    }
                }, 3000);
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                
                swipeRefreshLayout.setRefreshing(false);
                syncStatusText.setText("Sync failed");
                showError("Failed to sync bookmarks: " + message);
                
                // Hide sync status after a delay
                syncStatusText.postDelayed(() -> {
                    if (isAdded()) {
                        syncStatusText.setVisibility(View.GONE);
                    }
                }, 3000);
            }
        });
    }
    
    @Override
    public void onRemoveBookmark(Article article) {
        bookmarkSyncRepository.removeBookmark(article.getUrl(), new BookmarkSyncRepository.SyncCallback() {
            @Override
            public void onSuccess(boolean syncedToCloud) {
                if (!isAdded()) return;
                
                // Remove from adapter and show success message
                adapter.removeArticle(article);
                
                String message = syncedToCloud 
                    ? "Article removed from bookmarks and synced"
                    : "Article removed from bookmarks (offline mode)";
                
                showSuccess(message);
                
                // If no more articles, show empty state
                if (adapter.getItemCount() == 0) {
                    showEmptyState(true);
                }
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                
                showError("Failed to remove bookmark: " + message);
            }
        });
    }
    
    @Override
    public void onDownloadArticle(Article article) {
        newsRepository.downloadArticleForOffline(article, success -> {
            if (!isAdded()) return;
            
            // Update download status in adapter
            adapter.updateDownloadStatus(article.getUrl(), true);
            
            if (success) {
                showSuccess("Article downloaded for offline reading");
                // Refresh the bookmarks list to update UI
                adapter.notifyDataSetChanged();
            } else {
                showError("Failed to download article");
            }
        });
    }
    
    @Override
    public void onDeleteOfflineArticle(Article article) {
        showLoading(true);
        
        newsRepository.deleteOfflineArticle(article.getUrl(), new NewsRepository.NewsCallback() {
            @Override
            public void onSuccess(List<Article> articles) {
                if (!isAdded()) return;
                
                showLoading(false);
                showSuccess("Article removed from offline storage");
                
                // Refresh the bookmarks list to update UI
                adapter.notifyDataSetChanged();
            }
            
            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                
                showLoading(false);
                showError("Failed to remove offline article: " + message);
            }
        });
    }
    
    private void updateSyncStatus(boolean fromCloud) {
        syncStatusText.setText(fromCloud ? "Synced with cloud" : "Using local data");
        syncStatusText.setVisibility(View.VISIBLE);
        
        // Hide sync status after a delay
        syncStatusText.postDelayed(() -> {
            if (isAdded()) {
                syncStatusText.setVisibility(View.GONE);
            }
        }, 3000);
    }
    
    private void showLoading(boolean isLoading) {
        loadingIndicator.setVisibility(isLoading ? View.VISIBLE : View.GONE);
    }
    
    private void showEmptyState(boolean isEmpty) {
        emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        bookmarksRecyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        
        // Update the empty state message based on network status
        if (isEmpty) {
            TextView emptyStateTitle = emptyState.findViewById(R.id.empty_state_title);
            TextView emptyStateText = emptyState.findViewById(R.id.empty_state_text);
            
            if (isOfflineMode) {
                emptyStateTitle.setText("No offline bookmarks");
                emptyStateText.setText("Connect to the internet to sync your bookmarks or save articles for offline reading.");
            } else {
                emptyStateTitle.setText("No bookmarks yet");
                emptyStateText.setText("Save your favorite articles to read them later.");
            }
        }
    }
    
    private void showError(String message) {
        if (getView() != null) {
            Snackbar snackbar = Snackbar.make(getView(), message, Snackbar.LENGTH_LONG);
            View snackbarView = snackbar.getView();
            snackbarView.setBackgroundResource(R.color.warning_color);
            snackbar.show();
        }
    }
    
    private void showSuccess(String message) {
        if (getView() != null) {
            Snackbar snackbar = Snackbar.make(getView(), message, Snackbar.LENGTH_SHORT);
            View snackbarView = snackbar.getView();
            snackbarView.setBackgroundResource(R.color.green);
            snackbar.show();
        }
    }
    
    private void checkNetworkStatus() {
        isOfflineMode = !isNetworkAvailable();
        updateOfflineIndicator();
        
        // Update empty state if needed
        if (bookmarkedArticles.isEmpty()) {
            showEmptyState(true);
        }
    }
    
    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) 
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }
    
    private void updateOfflineIndicator() {
        if (offlineIndicator != null) {
            offlineIndicator.setVisibility(isOfflineMode ? View.VISIBLE : View.GONE);
        }
        
        // If we've gone offline, show a message
        if (isOfflineMode && getView() != null) {
            Snackbar snackbar = Snackbar.make(getView(), "You're offline. Some features may be limited.", 
                Snackbar.LENGTH_LONG);
            View snackbarView = snackbar.getView();
            snackbarView.setBackgroundResource(R.color.warning_color);
            snackbar.show();
        }
    }
    
    // Network change receiver to detect connectivity changes
    private class NetworkChangeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            checkNetworkStatus();
            
            // If we're back online and had no bookmarks, try to load them again
            if (!isOfflineMode && bookmarkedArticles.isEmpty()) {
                loadBookmarkedArticles();
            }
        }
    }
}