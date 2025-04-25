package com.example.newsapp.ui.article;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.newsapp.R;
import com.example.newsapp.data.models.Article;
import com.example.newsapp.data.repository.BookmarkSyncRepository;
import com.example.newsapp.data.repository.NewsRepository;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;

public class ArticleDetailFragment extends Fragment {
    private static final String TAG = "ArticleDetailFragment";
    private static final String ARG_URL = "url";
    private static final String ARG_IS_OFFLINE = "is_offline";
    
    private String articleUrl;
    private boolean isOfflineAvailable = false;
    private BookmarkSyncRepository bookmarkSyncRepository;
    private NewsRepository newsRepository;
    private boolean isBookmarked = false;
    private boolean isOfflineMode = false;
    private ProgressBar progressBar;
    private WebView webView;

    public static ArticleDetailFragment newInstance(String url, boolean isOfflineAvailable) {
        ArticleDetailFragment fragment = new ArticleDetailFragment();
        Bundle args = new Bundle();
        args.putString(ARG_URL, url);
        args.putBoolean(ARG_IS_OFFLINE, isOfflineAvailable);
        fragment.setArguments(args);
        return fragment;
    }
    
    public static ArticleDetailFragment newInstance(String url) {
        return newInstance(url, false);
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true); // Enable options menu
        
        if (getArguments() != null) {
            articleUrl = getArguments().getString(ARG_URL);
            isOfflineAvailable = getArguments().getBoolean(ARG_IS_OFFLINE, false);
        }
        
        // Initialize repositories
        bookmarkSyncRepository = new BookmarkSyncRepository(requireContext());
        newsRepository = new NewsRepository(requireContext());
        
        // Check network status
        isOfflineMode = !isNetworkAvailable();
        
        Log.d(TAG, "Article URL: " + articleUrl);
        Log.d(TAG, "Is offline available: " + isOfflineAvailable);
        Log.d(TAG, "Is offline mode: " + isOfflineMode);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_article_detail, container, false);
        webView = view.findViewById(R.id.web_view);
        progressBar = view.findViewById(R.id.progress_bar);
        
        // Configure WebView
        setupWebView();
        
        // Load content based on network status
        loadArticleContent();
        
        return view;
    }
    
    private void setupWebView() {
        // Basic WebView configuration
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setLoadsImagesAutomatically(true);
        
        // Cache settings
        webView.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
        // AppCache is deprecated in API level 33, use Service Workers instead
        
        // Additional settings
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setDatabaseEnabled(true);
        
        // Set WebViewClient for handling page loading
        webView.setWebViewClient(newsRepository.getOfflineEnabledWebViewClient());
    }
    
    private void loadArticleContent() {
        if (articleUrl == null) return;
        
        progressBar.setVisibility(View.VISIBLE);
        
        // First verify offline availability
        newsRepository.isArticleAvailableOffline(articleUrl, isAvailable -> {
            isOfflineAvailable = isAvailable;
            
            if (isOfflineMode) {
                if (isOfflineAvailable) {
                    // Load the locally stored version
                    String localUrl = newsRepository.getOfflineArticleUrl(articleUrl);
                    webView.loadUrl(localUrl);
                    showToastOnMainThread("Loading article from offline storage");
                } else {
                    // No offline version available
                    showOfflineNotAvailableMessage();
                    progressBar.setVisibility(View.GONE);
                }
            } else {
                // Check if we should load from offline storage anyway
                if (isOfflineAvailable) {
                    // Ask user if they want to use offline version
                    String localUrl = newsRepository.getOfflineArticleUrl(articleUrl);
                    // For simplicity, automatically use offline version if available
                    webView.loadUrl(localUrl);
                    showToastOnMainThread("Loading saved offline version");
                } else {
                    // Online mode, load directly from URL
                    webView.loadUrl(articleUrl);
                }
            }
        });
    }
    
    private void showOfflineNotAvailableMessage() {
        webView.loadData(
            "<html><body style='margin:30px; font-family:sans-serif;'>" +
            "<h2>Article Not Available Offline</h2>" +
            "<p>This article hasn't been saved for offline reading.</p>" +
            "<p>Please connect to the internet to read this article or bookmark it while online to read it offline later.</p>" +
            "</body></html>",
            "text/html",
            "UTF-8"
        );
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // Check if article is bookmarked
        checkBookmarkStatus();
        
        // Verify offline status
        if (articleUrl != null) {
            newsRepository.isArticleAvailableOffline(articleUrl, isAvailable -> {
                isOfflineAvailable = isAvailable;
            });
        }
    }
    
    private void checkBookmarkStatus() {
        if (articleUrl == null) return;
        
        bookmarkSyncRepository.isArticleBookmarked(articleUrl, bookmarked -> {
            isBookmarked = bookmarked;
        });
    }
    
    private void bookmarkArticle() {
        // Create a simple Article object with at minimum the URL
        Article article = new Article();
        article.setUrl(articleUrl);
        article.setTimestamp(System.currentTimeMillis());
        
        // Start by saving to database
        bookmarkSyncRepository.addBookmark(article, new BookmarkSyncRepository.SyncCallback() {
            @Override
            public void onSuccess(boolean syncedToCloud) {
                isBookmarked = true;
                // Refresh the menu to show bookmark status change
                requireActivity().invalidateOptionsMenu();
                
                // Ask user if they want to download for offline reading
                if (!isOfflineMode && !isOfflineAvailable) {
                    showToastOnMainThread("Saving article for offline reading...");
                    downloadForOffline(article);
                } else {
                    showToastOnMainThread("Article bookmarked" + (isOfflineMode ? " (offline mode)" : ""));
                }
            }

            @Override
            public void onError(String message) {
                showToastOnMainThread("Failed to bookmark article: " + message);
            }
        });
    }
    
    private void downloadForOffline(Article article) {
        newsRepository.downloadArticleForOffline(article, success -> {
            if (success) {
                isOfflineAvailable = true;
                showToastOnMainThread("Article saved for offline reading");
            } else {
                showToastOnMainThread("Failed to save article for offline reading");
            }
        });
    }
    
    private void unbookmarkArticle() {
        bookmarkSyncRepository.removeBookmark(articleUrl, new BookmarkSyncRepository.SyncCallback() {
            @Override
            public void onSuccess(boolean syncedToCloud) {
                isBookmarked = false;
                // Refresh the menu to show bookmark status change
                requireActivity().invalidateOptionsMenu();
                
                String message = syncedToCloud 
                    ? "Bookmark removed and synced" 
                    : "Bookmark removed (offline mode)";
                
                showToastOnMainThread(message);
            }

            @Override
            public void onError(String message) {
                showToastOnMainThread("Failed to remove bookmark: " + message);
            }
        });
    }
    
    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) 
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }
    
    private void showToastOnMainThread(String message) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (isAdded()) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.article_detail_menu, menu);
        updateDownloadMenuItem(menu);
        updateBookmarkMenuItem(menu);
        super.onCreateOptionsMenu(menu, inflater);
    }
    
    private void updateDownloadMenuItem(Menu menu) {
        MenuItem downloadItem = menu.findItem(R.id.action_download);
        if (downloadItem != null) {
            downloadItem.setVisible(!isOfflineMode && !isOfflineAvailable);
        }
        
        MenuItem deleteItem = menu.findItem(R.id.action_delete_offline);
        if (deleteItem != null) {
            deleteItem.setVisible(isOfflineAvailable);
        }
    }
    
    private void updateBookmarkMenuItem(Menu menu) {
        MenuItem bookmarkItem = menu.findItem(R.id.action_bookmark);
        if (bookmarkItem != null) {
            // Update icon based on bookmark status
            bookmarkItem.setIcon(isBookmarked ? 
                R.drawable.ic_bookmark_filled : 
                R.drawable.ic_bookmark_border);
            bookmarkItem.setTitle(isBookmarked ? "Remove Bookmark" : "Bookmark Article");
        }
    }
    
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        
        if (id == R.id.action_download) {
            // Download for offline reading
            Article article = new Article();
            article.setUrl(articleUrl);
            downloadForOffline(article);
            return true;
        } else if (id == R.id.action_delete_offline) {
            // Delete offline version
            newsRepository.deleteOfflineArticle(articleUrl, new NewsRepository.NewsCallback() {
                @Override
                public void onSuccess(List<Article> articles) {
                    isOfflineAvailable = false;
                    showToastOnMainThread("Article removed from offline storage");
                    // Update menu items
                    requireActivity().invalidateOptionsMenu();
                }
                
                @Override
                public void onError(String message) {
                    showToastOnMainThread("Failed to remove offline article: " + message);
                }
            });
            return true;
        } else if (id == R.id.action_bookmark) {
            // Toggle bookmark status
            if (isBookmarked) {
                unbookmarkArticle();
            } else {
                bookmarkArticle();
            }
            // Update menu after toggle
            requireActivity().invalidateOptionsMenu();
            return true;
        } else if (id == R.id.action_share) {
            // Share article URL
            shareArticleUrl();
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }
    
    private void shareArticleUrl() {
        if (articleUrl == null) return;
        
        android.content.Intent shareIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, articleUrl);
        startActivity(android.content.Intent.createChooser(shareIntent, "Share article via"));
    }
}