package com.example.newsapp.data.repository;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.example.newsapp.api.GNewsApiService;
import com.example.newsapp.api.RetrofitClient;
import com.example.newsapp.data.db.NewsDatabase;
import com.example.newsapp.data.models.Article;
import com.example.newsapp.data.models.NewsResponse;

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
    
    private final NewsDatabase database;
    private final GNewsApiService apiService;
    private final Executor executor = Executors.newSingleThreadExecutor();
    
    public NewsRepository(Context context) {
        database = NewsDatabase.getInstance(context);
        apiService = RetrofitClient.getRetrofitInstance().create(GNewsApiService.class);
    }
    
    public interface NewsCallback {
        void onSuccess(List<Article> articles);
        void onError(String message);
    }
    
    public void getNewsByCategory(String category, boolean isFeatured, int maxResults, NewsCallback callback) {
        // First check database for cached data
        executor.execute(() -> {
            List<Article> cachedArticles = database.articleDao().getArticlesByCategoryAndType(category, isFeatured);
            
            if (!cachedArticles.isEmpty()) {
                Log.d(TAG, "Using cached data for " + category + ", featured=" + isFeatured + 
                          ", count=" + cachedArticles.size());
                callback.onSuccess(cachedArticles);
            } else {
                Log.d(TAG, "No cached data available for " + category + ", featured=" + isFeatured);
            }
            
            // Attempt to get fresh data from API
            refreshNewsFromApi(category, maxResults, isFeatured, callback);
        });
    }
    
    private void refreshNewsFromApi(String category, int maxResults, boolean isFeatured, NewsCallback callback) {
        Log.d(TAG, "Fetching from API: category=" + category + ", max=" + maxResults + ", featured=" + isFeatured);
        
        // Try using top-headlines first
        Call<NewsResponse> call = apiService.getTopHeadlines(
            category,            // topic
            LANGUAGE,            // language
            COUNTRY,             // country
            maxResults,          // max results
            API_KEY              // API key
        );
        
        call.enqueue(new Callback<NewsResponse>() {
            @Override
            public void onResponse(Call<NewsResponse> call, Response<NewsResponse> response) {
                handleApiResponse(response, category, maxResults, isFeatured, callback);
            }
            
            @Override
            public void onFailure(Call<NewsResponse> call, Throwable t) {
                // If top-headlines fails, try the search endpoint as fallback
                Log.e(TAG, "Top-headlines request failed, trying search endpoint", t);
                
                Call<NewsResponse> searchCall = apiService.searchNews(
                    category,    // search query 
                    maxResults,  // max results
                    1,           // page
                    API_KEY      // API key
                );
                
                searchCall.enqueue(new Callback<NewsResponse>() {
                    @Override
                    public void onResponse(Call<NewsResponse> call, Response<NewsResponse> response) {
                        handleApiResponse(response, category, maxResults, isFeatured, callback);
                    }
                    
                    @Override
                    public void onFailure(Call<NewsResponse> call, Throwable t) {
                        String errorMsg = "Network failure: " + t.getMessage() + " for " + category;
                        Log.e(TAG, errorMsg, t);
                        callback.onError("Network error: " + t.getMessage());
                    }
                });
            }
        });
    }
    
    private void handleApiResponse(Response<NewsResponse> response, String category, 
                                  int maxResults, boolean isFeatured, NewsCallback callback) {
        if (response.isSuccessful() && response.body() != null) {
            List<Article> articles = response.body().getArticles();
            if (!articles.isEmpty()) {
                Log.d(TAG, "API success: got " + articles.size() + " articles for " + category);
                // Save to database in background
                saveArticlesToDb(category, articles, isFeatured);
                // Only update UI if we didn't already have cached data
                callback.onSuccess(articles);
            } else {
                String errorMsg = "API returned success but empty list for " + category;
                Log.e(TAG, errorMsg);
                callback.onError(errorMsg);
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
            
            if (errorCode == 429) {
                callback.onError("API request limit reached. Using cached data.");
            } else {
                callback.onError("Error: " + errorCode + ". Check logs for details.");
            }
        }
    }
    
    private void saveArticlesToDb(String category, List<Article> articles, boolean isFeatured) {
        executor.execute(() -> {
            long currentTime = System.currentTimeMillis();
            
            // Set category, featured status, and timestamp to all articles
            for (Article article : articles) {
                article.setCategory(category);
                article.setFeatured(isFeatured);
                article.setTimestamp(currentTime);
            }
            
            // Update articles in database
            database.articleDao().updateCategoryArticles(category, articles, isFeatured);
            
            // Clean up old cached data
            database.articleDao().deleteOldArticles(currentTime - CACHE_EXPIRATION_TIME);
        });
    }
} 