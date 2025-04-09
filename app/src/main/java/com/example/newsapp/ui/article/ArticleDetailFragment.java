package com.example.newsapp.ui.article;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.example.newsapp.utils.OfflineArticleManager;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class ArticleDetailFragment extends Fragment {
    private static final String ARG_URL = "url";
    private String articleUrl;
    private BookmarkSyncRepository bookmarkSyncRepository;
    private OfflineArticleManager offlineArticleManager;
    private boolean isBookmarked = false;
    private boolean isOfflineMode = false;
    private FloatingActionButton bookmarkFab;
    private ProgressBar progressBar;
    private WebView webView;

    public static ArticleDetailFragment newInstance(String url) {
        ArticleDetailFragment fragment = new ArticleDetailFragment();
        Bundle args = new Bundle();
        args.putString(ARG_URL, url);
        fragment.setArguments(args);
        return fragment;
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true); // Enable options menu
        
        if (getArguments() != null) {
            articleUrl = getArguments().getString(ARG_URL);
        }
        
        // Initialize repositories
        bookmarkSyncRepository = new BookmarkSyncRepository(requireContext());
        offlineArticleManager = new OfflineArticleManager(requireContext());
        
        // Check network status
        isOfflineMode = !isNetworkAvailable();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_article_detail, container, false);
        webView = view.findViewById(R.id.web_view);
        progressBar = view.findViewById(R.id.progress_bar);
        bookmarkFab = view.findViewById(R.id.bookmark_fab);
        
        // Configure WebView
        setupWebView();
        
        // Load content based on network status
        loadArticleContent();
        
        // Set up bookmark FAB
        setupBookmarkFab();
        
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
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
            }
            
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // If we're offline, check if this URL is available offline
                if (isOfflineMode && !offlineArticleManager.isArticleAvailableOffline(url)) {
                    showToastOnMainThread("This link is not available offline");
                    return true;
                }
                
                // Load URL in WebView
                view.loadUrl(url);
                return true;
            }
            
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                
                // Check if we have an offline version of this article
                if (offlineArticleManager.isArticleAvailableOffline(failingUrl)) {
                    String localUrl = offlineArticleManager.getLocalFileUrl(failingUrl);
                    view.loadUrl(localUrl);
                    showToastOnMainThread("Loading article from offline storage");
                } else {
                    showToastOnMainThread("Error loading article: " + description);
                }
            }
        });
    }
    
    private void loadArticleContent() {
        if (articleUrl == null) return;
        
        progressBar.setVisibility(View.VISIBLE);
        
        if (isOfflineMode) {
            if (offlineArticleManager.isArticleAvailableOffline(articleUrl)) {
                // Load the locally stored version
                String localUrl = offlineArticleManager.getLocalFileUrl(articleUrl);
                webView.loadUrl(localUrl);
                showToastOnMainThread("Loading article from offline storage");
            } else {
                // No offline version available
                showOfflineNotAvailableMessage();
                progressBar.setVisibility(View.GONE);
            }
        } else {
            // Online mode, load directly from URL
            webView.loadUrl(articleUrl);
        }
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
    }
    
    private void setupBookmarkFab() {
        bookmarkFab.setOnClickListener(v -> {
            if (isBookmarked) {
                unbookmarkArticle();
            } else {
                bookmarkArticle();
            }
        });
    }
    
    private void checkBookmarkStatus() {
        if (articleUrl == null) return;
        
        bookmarkSyncRepository.isArticleBookmarked(articleUrl, bookmarked -> {
            isBookmarked = bookmarked;
            updateBookmarkFabIcon();
        });
    }
    
    private void updateBookmarkFabIcon() {
        if (isBookmarked) {
            bookmarkFab.setImageResource(R.drawable.ic_bookmark_filled);
        } else {
            bookmarkFab.setImageResource(R.drawable.ic_bookmark_border);
        }
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
                updateBookmarkFabIcon();
                
                // Now download article content for offline reading
                if (!isOfflineMode) {
                    showToastOnMainThread("Saving article for offline reading...");
                    offlineArticleManager.downloadArticleForOffline(article, success -> {
                        if (success) {
                            showToastOnMainThread("Article saved for offline reading");
                        } else {
                            showToastOnMainThread("Failed to save article for offline reading");
                        }
                    });
                } else {
                    showToastOnMainThread("Article bookmarked (offline mode)");
                }
            }

            @Override
            public void onError(String message) {
                showToastOnMainThread("Failed to bookmark article: " + message);
            }
        });
    }
    
    private void unbookmarkArticle() {
        bookmarkSyncRepository.removeBookmark(articleUrl, new BookmarkSyncRepository.SyncCallback() {
            @Override
            public void onSuccess(boolean syncedToCloud) {
                isBookmarked = false;
                updateBookmarkFabIcon();
                
                // Also remove offline version
                offlineArticleManager.deleteOfflineArticle(articleUrl);
                
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
        android.net.ConnectivityManager connectivityManager = (android.net.ConnectivityManager) 
            requireContext().getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            android.net.NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            return activeNetworkInfo != null && activeNetworkInfo.isConnected();
        }
        return false;
    }
    
    /**
     * Helper method to show Toast messages safely on the main thread
     */
    private void showToastOnMainThread(String message) {
        if (getContext() == null) return;
        
        new Handler(Looper.getMainLooper()).post(() -> {
            if (isAdded() && getContext() != null) {
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }
}