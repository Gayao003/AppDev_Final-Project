package com.example.newsapp.data.repository;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.newsapp.api.GNewsApiService;
import com.example.newsapp.api.RetrofitClient;
import com.example.newsapp.data.db.NewsDatabase;
import com.example.newsapp.data.models.Article;
import com.example.newsapp.data.models.NewsResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class NewsRepository {
    
    private static final String TAG = "NewsRepository";
    private static final long CACHE_EXPIRATION_TIME = 24 * 60 * 60 * 1000; // 24 hours in milliseconds
    private static final String API_KEY = "a271f6f010f9d6cc173bd9ecf0b7ab6b";
    private static final String LANGUAGE = "en";
    private static final String COUNTRY = "us";
    private static final int ARTICLES_PER_PAGE = 5; // Back to 5 articles per page
    
    private final NewsDatabase database;
    private final GNewsApiService apiService;
    private final Executor executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    // Store current loaded pages for categories
    private final List<Article> currentRegularArticles = new ArrayList<>();
    
    // Keep track of total articles retrieved to avoid duplicates
    private int articlesOffset = 0;
    
    public NewsRepository(Context context) {
        database = NewsDatabase.getInstance(context);
        apiService = RetrofitClient.getRetrofitInstance().create(GNewsApiService.class);
    }
    
    public interface NewsCallback {
        void onSuccess(List<Article> articles);
        void onError(String message);
    }
    
    public interface PaginatedNewsCallback extends NewsCallback {
        void onSuccessWithHasMore(List<Article> articles, boolean hasMorePages);
    }
    
    public void getNewsByCategory(String category, boolean isFeatured, int maxResults, NewsCallback callback) {
        // First check database for cached data
        executor.execute(() -> {
            List<Article> cachedArticles = database.articleDao().getArticlesByCategoryAndType(category, isFeatured);
            
            // Limit the cached articles to maxResults if not featured
            if (!cachedArticles.isEmpty()) {
                Log.d(TAG, "Using cached data for " + category + ", featured=" + isFeatured + 
                          ", count=" + cachedArticles.size());
                
                // For non-featured, respect the maxResults parameter
                final List<Article> finalCachedArticles;
                if (!isFeatured && cachedArticles.size() > maxResults) {
                    finalCachedArticles = new ArrayList<>(cachedArticles.subList(0, maxResults));
                } else {
                    finalCachedArticles = new ArrayList<>(cachedArticles);
                }
                
                mainHandler.post(() -> callback.onSuccess(finalCachedArticles));
            } else {
                Log.d(TAG, "No cached data available for " + category + ", featured=" + isFeatured);
            }
            
            // Attempt to get fresh data from API
            if (isFeatured) {
                refreshNewsFromApi(category, maxResults, isFeatured, 1, callback);
            } else {
                // Clear the list when loading a new category
                currentRegularArticles.clear();
                refreshNewsFromApi(category, maxResults, isFeatured, 1, 
                    new PaginatedNewsCallback() {
                        @Override
                        public void onSuccess(List<Article> articles) {
                            mainHandler.post(() -> callback.onSuccess(articles));
                        }

                        @Override
                        public void onError(String message) {
                            mainHandler.post(() -> callback.onError(message));
                        }

                        @Override
                        public void onSuccessWithHasMore(List<Article> articles, boolean hasMorePages) {
                            mainHandler.post(() -> callback.onSuccess(articles));
                        }
                    });
            }
        });
    }
    
    public void loadMoreNews(String category, int page, PaginatedNewsCallback callback) {
        Log.d(TAG, "Loading more news for category: " + category + ", page: " + page);
        
        // Calculate offset based on page number - we use ARTICLES_PER_PAGE for consistency
        int offset = (page - 1) * ARTICLES_PER_PAGE;
        Log.d(TAG, "Using offset: " + offset + " for page " + page);
        
        // Use search endpoint which supports offset better than page parameter
        Call<NewsResponse> searchCall = apiService.searchNews(
            category,               // search query
            ARTICLES_PER_PAGE,      // max results
            offset,                 // offset instead of page
            API_KEY                 // API key
        );
        
        searchCall.enqueue(new Callback<NewsResponse>() {
            @Override
            public void onResponse(Call<NewsResponse> call, Response<NewsResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    NewsResponse newsResponse = response.body();
                    List<Article> newArticles = newsResponse.getArticles();
                    
                    if (!newArticles.isEmpty()) {
                        Log.d(TAG, "Successfully loaded " + newArticles.size() + " new articles with offset " + offset);
                        
                        // Filter out duplicates from the new articles based on URL
                        Set<String> existingUrls = new HashSet<>();
                        for (Article article : currentRegularArticles) {
                            if (article.getUrl() != null) {
                                existingUrls.add(article.getUrl());
                            }
                        }
                        
                        // Filter out articles we already have by URL
                        List<Article> uniqueNewArticles = new ArrayList<>();
                        for (Article article : newArticles) {
                            if (article.getUrl() != null && !existingUrls.contains(article.getUrl())) {
                                uniqueNewArticles.add(article);
                                // Also add to our tracking set
                                existingUrls.add(article.getUrl());
                            }
                        }
                        
                        Log.d(TAG, "After filtering duplicates, we have " + uniqueNewArticles.size() + " unique new articles");
                        
                        // Save only unique articles to database
                        if (!uniqueNewArticles.isEmpty()) {
                            saveArticlesToDb(category, uniqueNewArticles, false);
                            
                            // Log titles for debugging
                            for (Article article : uniqueNewArticles) {
                                Log.d(TAG, "Loaded: " + article.getTitle());
                            }
                        }
                        
                        // Always show more pages if we got results back
                        boolean hasMorePages = newArticles.size() >= ARTICLES_PER_PAGE;
                        Log.d(TAG, "Setting hasMorePages=" + hasMorePages + " based on received " + newArticles.size() + " >= " + ARTICLES_PER_PAGE);
                        
                        // Return only the unique new articles
                        callback.onSuccessWithHasMore(uniqueNewArticles, hasMorePages);
                        
                        // Add unique new articles to our tracked list
                        currentRegularArticles.addAll(uniqueNewArticles);
                    } else {
                        Log.d(TAG, "API returned empty list for offset " + offset);
                        callback.onSuccessWithHasMore(new ArrayList<>(), false);
                    }
                } else {
                    String errorMsg = "Failed to load more articles";
                    if (response.errorBody() != null) {
                        try {
                            errorMsg += ": " + response.errorBody().string();
                        } catch (IOException e) {
                            Log.e(TAG, "Error reading error body", e);
                        }
                    }
                    callback.onError(errorMsg);
                }
            }
            
            @Override
            public void onFailure(Call<NewsResponse> call, Throwable t) {
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }
    
    private void refreshNewsFromApi(String category, int maxResults, boolean isFeatured, 
                                  int page, NewsCallback callback) {
        Log.d(TAG, "Fetching from API: category=" + category + ", max=" + maxResults + 
                  ", featured=" + isFeatured + ", page=" + page);
        
        // Try using top-headlines first
        Call<NewsResponse> call = apiService.getTopHeadlines(
            category,            // topic
            LANGUAGE,            // language
            COUNTRY,             // country
            maxResults,          // max results
            page,                // page number
            API_KEY              // API key
        );
        
        call.enqueue(new Callback<NewsResponse>() {
            @Override
            public void onResponse(Call<NewsResponse> call, Response<NewsResponse> response) {
                handleApiResponse(response, category, maxResults, isFeatured, page, callback);
            }
            
            @Override
            public void onFailure(Call<NewsResponse> call, Throwable t) {
                // If top-headlines fails, try the search endpoint as fallback
                Log.e(TAG, "Top-headlines request failed, trying search endpoint", t);
                
                Call<NewsResponse> searchCall = apiService.searchNews(
                    category,    // search query 
                    maxResults,  // max results
                    page,        // page
                    API_KEY      // API key
                );
                
                searchCall.enqueue(new Callback<NewsResponse>() {
                    @Override
                    public void onResponse(Call<NewsResponse> call, Response<NewsResponse> response) {
                        handleApiResponse(response, category, maxResults, isFeatured, page, callback);
                    }
                    
                    @Override
                    public void onFailure(Call<NewsResponse> call, Throwable t) {
                        String errorMsg = "Network failure: " + t.getMessage() + " for " + category;
                        Log.e(TAG, errorMsg, t);
                        mainHandler.post(() -> callback.onError("Network error: " + t.getMessage()));
                    }
                });
            }
        });
    }
    
    private void handleApiResponse(Response<NewsResponse> response, String category, 
                                 int maxResults, boolean isFeatured, int page, NewsCallback callback) {
        if (response.isSuccessful() && response.body() != null) {
            NewsResponse newsResponse = response.body();
            List<Article> articles = newsResponse.getArticles();
            
            if (!articles.isEmpty()) {
                Log.d(TAG, "API success: got " + articles.size() + " articles for " + category + 
                          " (page " + page + ")");
                
                // Save to database in background
                saveArticlesToDb(category, articles, isFeatured);
                
                // For regular (non-featured) articles, handle pagination
                if (!isFeatured) {
                    // For page > 1, we're handling pagination - we want to show just the new articles
                    if (page > 1) {
                        // Log all article titles for debugging
                        Log.d(TAG, "Page " + page + " articles:");
                        for (Article article : articles) {
                            Log.d(TAG, "- Title: " + article.getTitle());
                        }
                        
                        // Don't accumulate, just use the new articles for this page
                        final List<Article> pageArticles = new ArrayList<>(articles);
                        boolean hasMorePages = articles.size() >= maxResults;
                        Log.d(TAG, "Pagination: returning " + pageArticles.size() + " NEW articles for page " + page);
                        
                        // Return only the current page's articles
                        if (callback instanceof PaginatedNewsCallback) {
                            final boolean finalHasMore = hasMorePages;
                            mainHandler.post(() -> ((PaginatedNewsCallback) callback)
                                .onSuccessWithHasMore(pageArticles, finalHasMore));
                        } else {
                            mainHandler.post(() -> callback.onSuccess(pageArticles));
                        }
                    } else {
                        // First page - initialize the list with these articles
                        currentRegularArticles.clear();
                        currentRegularArticles.addAll(articles);
                        
                        // For first page, always assume there are more pages
                        boolean hasMorePages = true;
                        Log.d(TAG, "Setting hasMorePages=" + hasMorePages + " for first page");
                        
                        // Return the full list for the first page
                        if (callback instanceof PaginatedNewsCallback) {
                            final List<Article> finalArticles = new ArrayList<>(currentRegularArticles);
                            final boolean finalHasMore = hasMorePages;
                            mainHandler.post(() -> ((PaginatedNewsCallback) callback)
                                .onSuccessWithHasMore(finalArticles, finalHasMore));
                        } else {
                            final List<Article> finalArticles = new ArrayList<>(currentRegularArticles);
                            mainHandler.post(() -> callback.onSuccess(finalArticles));
                        }
                    }
                } else {
                    // Featured articles don't use pagination
                    final List<Article> finalArticles = new ArrayList<>(articles);
                    mainHandler.post(() -> callback.onSuccess(finalArticles));
                }
            } else {
                String errorMsg = "API returned success but empty list for " + category;
                Log.e(TAG, errorMsg);
                mainHandler.post(() -> callback.onError(errorMsg));
            }
        } else {
            int errorCode = response.code();
            String errorBody = null;
            try {
                errorBody = response.errorBody() != null ? response.errorBody().string() : "null";
            } catch (Exception e) {
                errorBody = "Error reading error body: " + e.getMessage();
            }
            
            String errorMsg = "API error: HTTP " + errorCode + " for " + category;
            Log.e(TAG, errorMsg + ", Body: " + errorBody);
            
            final String finalErrorMsg;
            if (errorCode == 429) {
                finalErrorMsg = "API request limit reached. Using cached data.";
            } else {
                finalErrorMsg = "Error: " + errorCode + ". Check logs for details.";
            }
            
            mainHandler.post(() -> callback.onError(finalErrorMsg));
        }
    }
    
    private void saveArticlesToDb(String category, List<Article> articles, boolean isFeatured) {
        executor.execute(() -> {
            long currentTime = System.currentTimeMillis();
            
            Log.d(TAG, "Saving articles to database: " + articles.size() + " articles");
            
            // Set category, featured status, and timestamp to all articles
            for (Article article : articles) {
                article.setCategory(category);
                article.setFeatured(isFeatured);
                article.setTimestamp(currentTime);
                
                // Ensure URL is set as the primary key
                if (article.getUrl() == null || article.getUrl().isEmpty()) {
                    Log.w(TAG, "Article has empty URL, using title as key");
                    article.setUrl(article.getTitle());
                }
                
                // Debug logs for article data
                Log.d(TAG, "Saving article: " + article.getTitle());
                Log.d(TAG, "URL: " + article.getUrl());
                Log.d(TAG, "Image: " + article.getUrlToImage());
            }
            
            // Update articles in database
            database.articleDao().updateCategoryArticles(category, articles, isFeatured);
            
            // Clean up old cached data
            database.articleDao().deleteOldArticles(currentTime - CACHE_EXPIRATION_TIME);
        });
    }
    
    /**
     * Reset the state of the articles repository
     */
    public void resetArticles() {
        currentRegularArticles.clear();
        
        Log.d(TAG, "Reset articles repository state, cleared cached articles list");
    }
    
    /**
     * Search for articles using the GNews API
     * @param query The search query
     * @param page The page number (1-based)
     * @param callback Callback for search results
     */
    public void searchArticles(String query, int page, PaginatedNewsCallback callback) {
        Log.d(TAG, "Searching for articles with query: " + query + ", page: " + page);
        
        // Calculate offset based on page number
        int offset = (page - 1) * ARTICLES_PER_PAGE;
        
        // Use search endpoint which works better for search queries
        Call<NewsResponse> searchCall = apiService.searchNews(
            query,                  // search query
            ARTICLES_PER_PAGE,      // max results
            offset,                 // offset - calculated from page
            API_KEY                 // API key
        );
        
        searchCall.enqueue(new Callback<NewsResponse>() {
            @Override
            public void onResponse(Call<NewsResponse> call, Response<NewsResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    NewsResponse newsResponse = response.body();
                    List<Article> articles = newsResponse.getArticles();
                    
                    if (!articles.isEmpty()) {
                        Log.d(TAG, "Search returned " + articles.size() + " articles for query: " + query + ", page: " + page);
                        
                        // Add search category and metadata to articles
                        for (Article article : articles) {
                            article.setCategory("search_" + query);
                            article.setFeatured(false);
                            article.setTimestamp(System.currentTimeMillis());
                        }
                        
                        // Save to database with special search category
                        saveArticlesToDb("search_" + query, articles, false);
                        
                        // Determine if there are more pages based on results count
                        boolean hasMorePages = articles.size() >= ARTICLES_PER_PAGE;
                        
                        // Return articles to caller with pagination info
                        mainHandler.post(() -> callback.onSuccessWithHasMore(articles, hasMorePages));
                    } else {
                        Log.d(TAG, "Search returned empty list for query: " + query + ", page: " + page);
                        mainHandler.post(() -> callback.onSuccessWithHasMore(new ArrayList<>(), false));
                    }
                } else {
                    String errorMsg = "Failed to search articles";
                    try {
                        if (response.errorBody() != null) {
                            errorMsg += ": " + response.errorBody().string();
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Error reading error body", e);
                    }
                    Log.e(TAG, errorMsg);
                    mainHandler.post(() -> callback.onError(errorMsg));
                }
            }
            
            @Override
            public void onFailure(Call<NewsResponse> call, Throwable t) {
                String errorMsg = "Network error: " + t.getMessage();
                Log.e(TAG, errorMsg, t);
                mainHandler.post(() -> callback.onError(errorMsg));
            }
        });
    }
    
    /**
     * Search for articles using the GNews API (simplified version for backward compatibility)
     */
    public void searchArticles(String query, NewsCallback callback) {
        searchArticles(query, 1, new PaginatedNewsCallback() {
            @Override
            public void onSuccessWithHasMore(List<Article> articles, boolean hasMorePages) {
                callback.onSuccess(articles);
            }
            
            @Override
            public void onSuccess(List<Article> articles) {
                callback.onSuccess(articles);
            }
            
            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }
    
    // Bookmark functionality
    public void getBookmarkedArticles(NewsCallback callback) {
        executor.execute(() -> {
            try {
                List<Article> bookmarkedArticles = database.articleDao().getBookmarkedArticles();
                mainHandler.post(() -> callback.onSuccess(bookmarkedArticles));
            } catch (Exception e) {
                Log.e(TAG, "Error fetching bookmarked articles", e);
                mainHandler.post(() -> callback.onError("Failed to load bookmarked articles"));
            }
        });
    }
    
    public void bookmarkArticle(Article article, NewsCallback callback) {
        executor.execute(() -> {
            try {
                // Check if we have all the article details needed
                if (article.getTitle() == null || article.getTitle().isEmpty()) {
                    // Fetch the article data from the database if it exists
                    List<Article> existingArticles = database.articleDao().getArticlesByUrl(article.getUrl());
                    
                    if (existingArticles != null && !existingArticles.isEmpty()) {
                        // Use the existing article data
                        Article existingArticle = existingArticles.get(0);
                        existingArticle.setBookmarked(true);
                        database.articleDao().insertArticle(existingArticle);
                    } else {
                        // We don't have the article details, just set the URL and bookmark flag
                        article.setBookmarked(true);
                        if (article.getTimestamp() == 0) {
                            article.setTimestamp(System.currentTimeMillis());
                        }
                        database.articleDao().insertArticle(article);
                    }
                } else {
                    // We have full article details
                    article.setBookmarked(true);
                    database.articleDao().insertArticle(article);
                }
                
                mainHandler.post(() -> callback.onSuccess(null));
            } catch (Exception e) {
                Log.e(TAG, "Error bookmarking article", e);
                mainHandler.post(() -> callback.onError("Failed to bookmark article"));
            }
        });
    }
    
    public void unbookmarkArticle(String articleUrl, NewsCallback callback) {
        executor.execute(() -> {
            try {
                database.articleDao().unbookmarkArticle(articleUrl);
                mainHandler.post(() -> callback.onSuccess(null));
            } catch (Exception e) {
                Log.e(TAG, "Error removing bookmark", e);
                mainHandler.post(() -> callback.onError("Failed to remove bookmark"));
            }
        });
    }
    
    public void isArticleBookmarked(String articleUrl, BookmarkStatusCallback callback) {
        executor.execute(() -> {
            try {
                boolean isBookmarked = database.articleDao().isArticleBookmarked(articleUrl);
                mainHandler.post(() -> callback.onResult(isBookmarked));
            } catch (Exception e) {
                Log.e(TAG, "Error checking bookmark status", e);
                mainHandler.post(() -> callback.onResult(false));
            }
        });
    }
    
    public interface BookmarkStatusCallback {
        void onResult(boolean isBookmarked);
    }
} 