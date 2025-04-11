package com.example.newsapp.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.example.newsapp.data.db.NewsDatabase;
import com.example.newsapp.data.models.Article;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class OfflineArticleManager {
    private static final String TAG = "OfflineArticleManager";
    private static final String OFFLINE_DIR = "offline_articles";
    private static final String IMAGES_DIR = "article_images";
    
    private final Context context;
    private final Executor executor;
    private final NewsDatabase database;
    private final OkHttpClient httpClient;
    
    public OfflineArticleManager(Context context) {
        this.context = context.getApplicationContext();
        this.executor = Executors.newSingleThreadExecutor();
        this.database = NewsDatabase.getInstance(context);
        this.httpClient = new OkHttpClient.Builder().build();
        createOfflineDirectory();
    }
    
    private void createOfflineDirectory() {
        File offlineDir = new File(context.getFilesDir(), OFFLINE_DIR);
        if (!offlineDir.exists()) {
            if (!offlineDir.mkdirs()) {
                Log.e(TAG, "Failed to create offline directory");
            }
        }
        
        File imagesDir = new File(offlineDir, IMAGES_DIR);
        if (!imagesDir.exists()) {
            if (!imagesDir.mkdirs()) {
                Log.e(TAG, "Failed to create images directory");
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
                // Download the article content
                String articleContent = downloadArticleContent(article.getUrl());
                
                if (articleContent != null) {
                    // Process and format the content
                    String formattedHtml = createFormattedHtml(article, articleContent);
                    
                    // Save to file
                    String fileName = getFileNameFromUrl(article.getUrl());
                    saveArticleToFile(fileName, formattedHtml);
                    
                    // Update database
                    database.articleDao().markArticleAsDownloaded(article.getUrl());
                    
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
    
    private String createFormattedHtml(Article article, String content) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault());
        String date = dateFormat.format(new Date(article.getTimestamp()));
        
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>")
            .append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>")
            .append("<style>")
            .append("body { font-family: Arial, sans-serif; line-height: 1.6; padding: 15px; max-width: 800px; margin: 0 auto; }")
            .append("h1 { font-size: 22px; color: #333; margin-bottom: 8px; }")
            .append("h2 { font-size: 20px; color: #444; }")
            .append("h3, h4, h5, h6 { color: #555; }")
            .append("p { margin-bottom: 16px; font-size: 16px; color: #333; }")
            .append("img { max-width: 100%; height: auto; display: block; margin: 20px auto; border-radius: 8px; }")
            .append(".meta { color: #666; font-size: 14px; margin-bottom: 20px; }")
            .append(".content { font-size: 16px; color: #333; }")
            .append("a { color: #0066cc; text-decoration: none; }")
            .append("a:hover { text-decoration: underline; }")
            .append("figure { margin: 20px 0; }")
            .append("figcaption { font-size: 14px; color: #666; text-align: center; }")
            .append(".offline-note { background-color: #f8f8f8; padding: 10px; border-radius: 4px; margin: 20px 0; }")
            .append("blockquote { border-left: 4px solid #ddd; padding-left: 15px; margin-left: 0; color: #555; }")
            .append("</style>")
            .append("<title>").append(article.getTitle()).append("</title></head><body>");
        
        // Article header
        html.append("<h1>").append(article.getTitle()).append("</h1>");
        html.append("<div class='meta'>").append(date);
        
        if (article.getSource() != null && article.getSource().getName() != null) {
            html.append(" | ").append(article.getSource().getName());
        }
        html.append("</div>");
        
        // Featured image
        if (article.getUrlToImage() != null && !article.getUrlToImage().isEmpty()) {
            html.append("<figure>")
                .append("<img src='").append(article.getUrlToImage()).append("' alt='Featured image'>")
                .append("<figcaption>Image from article</figcaption>")
                .append("</figure>");
        }
        
        // Article content
        html.append("<div class='content'>");
        
        // Description as intro if available
        if (article.getDescription() != null && !article.getDescription().isEmpty()) {
            html.append("<p><strong>").append(article.getDescription()).append("</strong></p>");
        }
        
        // Main content
        if (article.getContent() != null && !article.getContent().isEmpty()) {
            // If we have content from the API, include it first
            html.append("<p>").append(article.getContent()).append("</p>");
        }
        
        // Now include content from the scraped page
        if (content != null && !content.isEmpty()) {
            // Process the content to clean it up
            String processedContent = cleanupHtmlContent(article, content);
            
            // Insert the cleaned content
            html.append(processedContent);
        } else {
            // If no content is available
            html.append("<p>The full article could not be loaded. You may need to visit the original website.</p>");
        }
        
        html.append("</div>");
        
        // Add offline notice and article source
        html.append("<div class='offline-note'>")
            .append("<p><strong>Offline copy:</strong> This article has been saved for offline reading.</p>")
            .append("<p>Original source: <a href='")
            .append(article.getUrl()).append("'>").append(article.getUrl()).append("</a></p>")
            .append("<p>Downloaded on: ").append(dateFormat.format(new Date())).append("</p>")
            .append("</div>");
        
        html.append("</body></html>");
        
        return html.toString();
    }
    
    private String downloadArticleContent(String articleUrl) {
        HttpURLConnection connection = null;
        BufferedReader reader = null;
        StringBuilder content = new StringBuilder();
        
        try {
            URL url = new URL(articleUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(30000); // Increased timeout
            connection.setReadTimeout(30000);    // Increased timeout
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.110 Safari/537.36");
            connection.setInstanceFollowRedirects(true);
            connection.connect();
            
            // Check if we got redirected
            if (connection.getResponseCode() == HttpURLConnection.HTTP_MOVED_PERM || 
                connection.getResponseCode() == HttpURLConnection.HTTP_MOVED_TEMP) {
                String newUrl = connection.getHeaderField("Location");
                connection.disconnect();
                
                // Create a new connection to the redirect location
                connection = (HttpURLConnection) new URL(newUrl).openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.110 Safari/537.36");
                connection.connect();
            }
            
            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
                
                String htmlContent = content.toString();
                Log.d(TAG, "Downloaded HTML size: " + htmlContent.length() + " bytes");
                
                return extractMainContent(htmlContent);
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
    
    private String extractMainContent(String htmlContent) {
        // This is a simple extraction, in a real app you might use a library like Jsoup
        // for better HTML parsing and content extraction
        try {
            // Try to find the main content div/article tag
            String lowerContent = htmlContent.toLowerCase();
            
            // First look for article tags which usually contain the main content
            int articleStart = lowerContent.indexOf("<article");
            if (articleStart != -1) {
                int articleEnd = findClosingTag(lowerContent, articleStart, "article");
                if (articleEnd > articleStart) {
                    Log.d(TAG, "Found article tag, extracting content...");
                    return htmlContent.substring(articleStart, articleEnd + 10); // +10 for </article>
                }
            }
            
            // Look for common article containers
            String[] possibleContainers = {
                "<div class=\"article", "<div class=\"post",
                "<div class=\"content", "<div id=\"content", "<div class=\"entry",
                "<div class=\"main", "<section class=\"article", "<div class=\"story",
                "<div role=\"main", "<main", "<div class=\"body"
            };
            
            for (String container : possibleContainers) {
                int startIndex = lowerContent.indexOf(container);
                if (startIndex != -1) {
                    // We found a potential content container
                    // Find the matching closing tag
                    String tagName = container.substring(1, container.indexOf(" "));
                    int closeIndex = findClosingTag(lowerContent, startIndex, tagName);
                    
                    if (closeIndex > startIndex) {
                        Log.d(TAG, "Found content in " + container + " tag");
                        return htmlContent.substring(startIndex, closeIndex + tagName.length() + 3); // +3 for </>
                    }
                }
            }
            
            // If we can't find a specific container, try to extract the body content
            int bodyStart = lowerContent.indexOf("<body");
            int bodyEnd = lowerContent.lastIndexOf("</body>");
            
            if (bodyStart != -1 && bodyEnd != -1) {
                Log.d(TAG, "Falling back to extracting body content");
                // Find the actual start of content (after the body tag)
                int contentStart = lowerContent.indexOf(">", bodyStart) + 1;
                return htmlContent.substring(contentStart, bodyEnd);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error extracting content", e);
        }
        
        // If extraction fails, return the full HTML
        Log.d(TAG, "Could not extract specific content, returning full HTML");
        return htmlContent;
    }
    
    // Helper method to find the proper closing tag
    private int findClosingTag(String html, int openingTagPosition, String tagName) {
        // Count opening and closing tags to handle nested tags properly
        int position = openingTagPosition;
        int openCount = 1;
        String openTag = "<" + tagName;
        String closeTag = "</" + tagName;
        
        while (openCount > 0 && position < html.length()) {
            // Find next opening tag
            int nextOpenPos = html.indexOf(openTag, position + 1);
            // Find next closing tag
            int nextClosePos = html.indexOf(closeTag, position + 1);
            
            // If no more closing tags, return end of string
            if (nextClosePos == -1) {
                return html.length();
            }
            
            // If no more opening tags or closing tag comes first
            if (nextOpenPos == -1 || nextClosePos < nextOpenPos) {
                openCount--;
                position = nextClosePos;
            } else {
                openCount++;
                position = nextOpenPos;
            }
        }
        
        return position;
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
            try {
                // Delete the file
                String fileName = getFileNameFromUrl(articleUrl);
                File file = new File(new File(context.getFilesDir(), OFFLINE_DIR), fileName);
                if (file.exists()) {
                    boolean deleted = file.delete();
                    if (!deleted) {
                        Log.e(TAG, "Failed to delete offline article file: " + articleUrl);
                    }
                }
                
                // Update the database status
                database.articleDao().markArticleAsNotDownloaded(articleUrl);
                
                Log.d(TAG, "Offline article deleted: " + articleUrl);
            } catch (Exception e) {
                Log.e(TAG, "Error deleting offline article", e);
            }
        });
    }
    
    public WebViewClient getOfflineEnabledWebViewClient() {
        return new OfflineWebViewClient();
    }
    
    private class OfflineWebViewClient extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            
            // If we're in offline mode and this is a web request, try to serve from cache
            if (isArticleAvailableOffline(url)) {
                try {
                    String content = getOfflineArticleContent(url);
                    if (content != null) {
                        return new WebResourceResponse(
                            "text/html", 
                            "UTF-8",
                            new java.io.ByteArrayInputStream(content.getBytes())
                        );
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error serving offline content", e);
                }
            } else if (url.startsWith("file://")) {
                // Let the WebView handle file requests normally
                return super.shouldInterceptRequest(view, request);
            }
            
            // For all other requests, try to handle them
            String fileExtension = MimeTypeMap.getFileExtensionFromUrl(url);
            if (fileExtension != null) {
                String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension);
                
                // For images, try to load them even in offline mode
                if (mimeType != null && mimeType.startsWith("image/")) {
                    try {
                        // Try to download the image for offline use
                        URL imageUrl = new URL(url);
                        HttpURLConnection connection = (HttpURLConnection) imageUrl.openConnection();
                        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
                        connection.connect();
                        
                        if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                            InputStream inputStream = connection.getInputStream();
                            return new WebResourceResponse(
                                mimeType,
                                "UTF-8",
                                inputStream
                            );
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error loading image in offline mode: " + url, e);
                    }
                }
            }
            
            return super.shouldInterceptRequest(view, request);
        }
        
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            // If article is available offline, load the local file
            if (isArticleAvailableOffline(url)) {
                String localUrl = getLocalFileUrl(url);
                view.loadUrl(localUrl);
                return true;
            }
            
            // Otherwise, load the URL normally
            view.loadUrl(url);
            return true;
        }
        
        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            Log.d(TAG, "Page loading started: " + url);
        }
        
        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            Log.d(TAG, "Page loading finished: " + url);
            
            // Inject CSS to improve readability if it's an offline article
            if (url.startsWith("file://")) {
                String css = "body { font-size: 18px; line-height: 1.6; }";
                view.evaluateJavascript(
                    "(function() {" +
                    "    var style = document.createElement('style');" +
                    "    style.type = 'text/css';" +
                    "    style.innerHTML = '" + css + "';" +
                    "    document.head.appendChild(style);" +
                    "})();", null);
            }
        }
        
        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            super.onReceivedError(view, errorCode, description, failingUrl);
            Log.e(TAG, "WebView error: " + description + " for URL: " + failingUrl);
            
            // Check if we have an offline version to show instead
            if (isArticleAvailableOffline(failingUrl)) {
                view.loadUrl(getLocalFileUrl(failingUrl));
            }
        }
    }
    
    // Clean up HTML content to ensure it displays well in the WebView
    private String cleanupHtmlContent(Article article, String html) {
        // Check if the HTML already contains processed content 
        // (avoids processing whole HTML documents multiple times)
        if (html.contains("<html") && html.contains("<body")) {
            try {
                // Extract just the content within <body> tags
                int bodyStart = html.indexOf("<body");
                if (bodyStart != -1) {
                    bodyStart = html.indexOf(">", bodyStart) + 1;
                    int bodyEnd = html.indexOf("</body>", bodyStart);
                    if (bodyEnd != -1) {
                        html = html.substring(bodyStart, bodyEnd);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error extracting body content", e);
            }
        }
        
        // Remove scripts that might interfere with offline viewing
        html = html.replaceAll("<script[^>]*>[\\s\\S]*?</script>", "");
        
        // Remove iframes that won't work offline
        html = html.replaceAll("<iframe[^>]*>[\\s\\S]*?</iframe>", "");
        
        // Fix relative URLs for images
        if (html.contains("src=\"/")) {
            String baseUrl = extractBaseUrl(article.getUrl());
            html = html.replaceAll("src=\"/", "src=\"" + baseUrl + "/");
        }
        
        return html;
    }
    
    // Extract base URL (protocol + domain) from full URL
    private String extractBaseUrl(String url) {
        if (url == null) return "";
        try {
            URL parsedUrl = new URL(url);
            return parsedUrl.getProtocol() + "://" + parsedUrl.getHost();
        } catch (Exception e) {
            Log.e(TAG, "Error parsing URL", e);
            return "";
        }
    }
} 