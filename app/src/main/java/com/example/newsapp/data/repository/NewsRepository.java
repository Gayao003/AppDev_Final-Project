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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class NewsRepository {
    
    private static final String TAG = "NewsRepository";
    private static final long CACHE_EXPIRATION_TIME = 24 * 60 * 60 * 1000; // 24 hours in milliseconds
    private static final String API_KEY = "7535951de6c15f5c01b372004f720c25";
    private static final String LANGUAGE = "en";
    private static final String COUNTRY = "us";
    private static final int ARTICLES_PER_PAGE = 5;
    
    private final NewsDatabase database;
    private final GNewsApiService apiService;
    private final Executor executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    // Store current loaded pages for categories
    private final List<Article> currentRegularArticles = new ArrayList<>();
    
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
            
            if (!cachedArticles.isEmpty()) {
                Log.d(TAG, "Using cached data for " + category + ", featured=" + isFeatured + 
                          ", count=" + cachedArticles.size());
                final List<Article> finalCachedArticles = new ArrayList<>(cachedArticles);
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
                refreshNewsFromApi(category, ARTICLES_PER_PAGE, isFeatured, 1, 
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
        refreshNewsFromApi(category, ARTICLES_PER_PAGE, false, page, callback);
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
                    // Append new articles to our current list if loading more pages
                    if (page > 1) {
                        currentRegularArticles.addAll(articles);
                    } else {
                        currentRegularArticles.clear();
                        currentRegularArticles.addAll(articles);
                    }
                    
                    // Check if we potentially have more pages
                    boolean hasMorePages = articles.size() >= maxResults;
                    
                    // If callback supports pagination, use it
                    if (callback instanceof PaginatedNewsCallback) {
                        final List<Article> finalArticles = new ArrayList<>(currentRegularArticles);
                        final boolean finalHasMore = hasMorePages;
                        mainHandler.post(() -> ((PaginatedNewsCallback) callback)
                            .onSuccessWithHasMore(finalArticles, finalHasMore));
                    } else {
                        final List<Article> finalArticles = new ArrayList<>(currentRegularArticles);
                        mainHandler.post(() -> callback.onSuccess(finalArticles));
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
} 