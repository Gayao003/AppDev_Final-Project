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
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class ArticleDetailFragment extends Fragment {
    private static final String ARG_URL = "url";
    private String articleUrl;
    private BookmarkSyncRepository bookmarkSyncRepository;
    private boolean isBookmarked = false;
    private FloatingActionButton bookmarkFab;
    private ProgressBar progressBar;

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
        
        // Initialize repository
        bookmarkSyncRepository = new BookmarkSyncRepository(requireContext());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_article_detail, container, false);
        WebView webView = view.findViewById(R.id.web_view);
        progressBar = view.findViewById(R.id.progress_bar);
        bookmarkFab = view.findViewById(R.id.bookmark_fab);
        
        // Configure WebView for offline caching
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        
        // Enable caching
        webView.getSettings().setCacheMode(android.webkit.WebSettings.LOAD_CACHE_ELSE_NETWORK);
        
        // Additional settings for better offline experience
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setDatabaseEnabled(true);
        
        // Set a WebViewClient to handle page loading
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
            }
            
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // Stay within the WebView for all navigation
                view.loadUrl(url);
                return true;
            }
            
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                // Handle error - try to load from cache if available
                if (errorCode == WebViewClient.ERROR_HOST_LOOKUP || 
                    errorCode == WebViewClient.ERROR_TIMEOUT || 
                    errorCode == WebViewClient.ERROR_CONNECT) {
                    
                    // Force cache-only mode when offline
                    webView.getSettings().setCacheMode(android.webkit.WebSettings.LOAD_CACHE_ONLY);
                    view.loadUrl(failingUrl);
                    
                    // Show a message that we're in offline mode
                    showToastOnMainThread("No internet connection. Loading from cache.");
                }
            }
        });
        
        // Load URL
        if (articleUrl != null) {
            progressBar.setVisibility(View.VISIBLE);
            webView.loadUrl(articleUrl);
        }
        
        // Set up bookmark FAB
        setupBookmarkFab();
        
        return view;
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
        
        bookmarkSyncRepository.addBookmark(article, new BookmarkSyncRepository.SyncCallback() {
            @Override
            public void onSuccess(boolean syncedToCloud) {
                isBookmarked = true;
                updateBookmarkFabIcon();
                
                String message = syncedToCloud 
                    ? "Article bookmarked and synced to cloud" 
                    : "Article bookmarked (offline mode)";
                    
                showToastOnMainThread(message);
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