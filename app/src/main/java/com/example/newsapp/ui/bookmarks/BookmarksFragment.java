package com.example.newsapp.ui.bookmarks;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.newsapp.R;
import com.example.newsapp.data.models.Article;
import com.example.newsapp.data.repository.BookmarkSyncRepository;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

public class BookmarksFragment extends Fragment implements BookmarksAdapter.BookmarkActionListener {
    private static final String TAG = "BookmarksFragment";
    
    private RecyclerView bookmarksRecyclerView;
    private ProgressBar loadingIndicator;
    private LinearLayout emptyState;
    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView syncStatusText;
    
    private BookmarksAdapter adapter;
    private BookmarkSyncRepository bookmarkSyncRepository;
    private List<Article> bookmarkedArticles = new ArrayList<>();
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Initialize the repository
        bookmarkSyncRepository = new BookmarkSyncRepository(requireContext());
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
        
        // Setup RecyclerView
        setupRecyclerView();
        
        // Setup SwipeRefreshLayout
        setupSwipeRefresh();
        
        // Load bookmarked articles
        loadBookmarkedArticles();
        
        return rootView;
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // Refresh bookmarks when coming back to the fragment
        loadBookmarkedArticles();
    }
    
    private void setupRecyclerView() {
        adapter = new BookmarksAdapter(bookmarkedArticles, this);
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
                } else if (!fromCloud) {
                    // Only show empty state if we've loaded local data and it's empty
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
                
                showError("Failed to load bookmarks: " + message);
            }
        });
    }
    
    private void syncBookmarks() {
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
    
    private void updateSyncStatus(boolean fromCloud) {
        if (fromCloud) {
            syncStatusText.setText("Bookmarks synced from cloud");
            syncStatusText.setVisibility(View.VISIBLE);
            
            // Hide sync status after a delay
            syncStatusText.postDelayed(() -> {
                if (isAdded()) {
                    syncStatusText.setVisibility(View.GONE);
                }
            }, 3000);
        }
    }
    
    private void showLoading(boolean isLoading) {
        if (isLoading) {
            loadingIndicator.setVisibility(View.VISIBLE);
            bookmarksRecyclerView.setVisibility(View.GONE);
            emptyState.setVisibility(View.GONE);
        } else {
            loadingIndicator.setVisibility(View.GONE);
            bookmarksRecyclerView.setVisibility(View.VISIBLE);
        }
    }
    
    private void showEmptyState(boolean isEmpty) {
        if (isEmpty) {
            emptyState.setVisibility(View.VISIBLE);
            bookmarksRecyclerView.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            bookmarksRecyclerView.setVisibility(View.VISIBLE);
        }
    }
    
    private void showError(String message) {
        if (getView() != null) {
            Snackbar.make(getView(), message, Snackbar.LENGTH_SHORT).show();
        }
    }
    
    private void showSuccess(String message) {
        if (getView() != null) {
            Snackbar.make(getView(), message, Snackbar.LENGTH_SHORT).show();
        }
    }
}