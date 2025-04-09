package com.example.newsapp.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.example.newsapp.data.models.Article;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class OfflineArticleManager {
    private static final String TAG = "OfflineArticleManager";
    private static final String OFFLINE_DIR = "offline_articles";
    
    private final Context context;
    private final Executor executor;
    
    public OfflineArticleManager(Context context) {
        this.context = context.getApplicationContext();
        this.executor = Executors.newSingleThreadExecutor();
        createOfflineDirectory();
    }
    
    private void createOfflineDirectory() {
        File offlineDir = new File(context.getFilesDir(), OFFLINE_DIR);
        if (!offlineDir.exists()) {
            if (!offlineDir.mkdirs()) {
                Log.e(TAG, "Failed to create offline directory");
            }
        }
    }
    
    public interface DownloadCallback {
        void onDownloadComplete(boolean success);
    }
    
    public void downloadArticleForOffline(Article article, DownloadCallback callback) {
        executor.execute(() -> {
            boolean success = false;
            try {
                String articleContent = downloadArticleContent(article.getUrl());
                if (articleContent != null) {
                    String fileName = getFileNameFromUrl(article.getUrl());
                    saveArticleToFile(fileName, articleContent);
                    success = true;
                    Log.d(TAG, "Article downloaded successfully: " + article.getUrl());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error downloading article: " + e.getMessage(), e);
            }
            
            final boolean finalSuccess = success;
            new Handler(Looper.getMainLooper()).post(() -> callback.onDownloadComplete(finalSuccess));
        });
    }
    
    private String downloadArticleContent(String articleUrl) {
        HttpURLConnection connection = null;
        BufferedReader reader = null;
        StringBuilder content = new StringBuilder();
        
        try {
            URL url = new URL(articleUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.connect();
            
            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
                return content.toString();
            } else {
                Log.e(TAG, "Error downloading article: HTTP " + connection.getResponseCode());
                return null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error downloading article content", e);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing reader", e);
                }
            }
        }
    }
    
    private void saveArticleToFile(String fileName, String content) throws IOException {
        File file = new File(new File(context.getFilesDir(), OFFLINE_DIR), fileName);
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            outputStream.write(content.getBytes());
        }
    }
    
    public boolean isArticleAvailableOffline(String articleUrl) {
        String fileName = getFileNameFromUrl(articleUrl);
        File file = new File(new File(context.getFilesDir(), OFFLINE_DIR), fileName);
        return file.exists();
    }
    
    public String getOfflineArticleContent(String articleUrl) {
        String fileName = getFileNameFromUrl(articleUrl);
        File file = new File(new File(context.getFilesDir(), OFFLINE_DIR), fileName);
        
        if (!file.exists()) {
            return null;
        }
        
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            return content.toString();
        } catch (IOException e) {
            Log.e(TAG, "Error reading offline article", e);
            return null;
        }
    }
    
    public String getLocalFileUrl(String articleUrl) {
        String fileName = getFileNameFromUrl(articleUrl);
        File file = new File(new File(context.getFilesDir(), OFFLINE_DIR), fileName);
        return "file://" + file.getAbsolutePath();
    }
    
    private String getFileNameFromUrl(String url) {
        // Create a safe filename from URL
        return url.replaceAll("[^a-zA-Z0-9]", "_") + ".html";
    }
    
    public void deleteOfflineArticle(String articleUrl) {
        executor.execute(() -> {
            String fileName = getFileNameFromUrl(articleUrl);
            File file = new File(new File(context.getFilesDir(), OFFLINE_DIR), fileName);
            if (file.exists()) {
                boolean deleted = file.delete();
                if (!deleted) {
                    Log.e(TAG, "Failed to delete offline article: " + articleUrl);
                }
            }
        });
    }
    
    public WebViewClient getOfflineEnabledWebViewClient() {
        return new OfflineWebViewClient();
    }
    
    private class OfflineWebViewClient extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            // Not implemented for this simple example, but could be used for more advanced offline caching
            return super.shouldInterceptRequest(view, request);
        }
        
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            // Load all URLs in the WebView
            view.loadUrl(url);
            return true;
        }
    }
} 