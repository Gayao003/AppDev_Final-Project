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
        
        // Calculate offset based on page number
        articlesOffset = (page - 1) * ARTICLES_PER_PAGE;
        Log.d(TAG, "Using offset: " + articlesOffset + " for page " + page);
        
        // Use search endpoint which supports offset better than page parameter
        Call<NewsResponse> searchCall = apiService.searchNews(
            category,               // search query
            ARTICLES_PER_PAGE,      // max results
            articlesOffset,         // offset instead of page
            API_KEY                 // API key
        );
        
        searchCall.enqueue(new Callback<NewsResponse>() {
            @Override
            public void onResponse(Call<NewsResponse> call, Response<NewsResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    NewsResponse newsResponse = response.body();
                    List<Article> articles = newsResponse.getArticles();
                    
                    if (!articles.isEmpty()) {
                        Log.d(TAG, "Successfully loaded " + articles.size() + " new articles with offset " + articlesOffset);
                        
                        // Save to database
                        saveArticlesToDb(category, articles, false);
                        
                        // Log titles for debugging
                        for (Article article : articles) {
                            Log.d(TAG, "Loaded: " + article.getTitle());
                        }
                        
                        // Always show more pages if we got articles
                        boolean hasMorePages = articles.size() >= ARTICLES_PER_PAGE;
                        
                        // Return only the new articles
                        callback.onSuccessWithHasMore(articles, hasMorePages);
                    } else {
                        Log.d(TAG, "API returned empty list for offset " + articlesOffset);
                        callback.onError("No more articles available");
                    }
                } else {
                    String errorMsg = "Failed to load more articles";
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
     * Reset the repository state when changing categories
     */
    public void resetArticles() {
        articlesOffset = 0;
        currentRegularArticles.clear();
        Log.d(TAG, "Reset articles state, offset=0");
    }
    
    /**
     * Search for articles using the GNews API
     */
    public void searchArticles(String query, NewsCallback callback) {
        Log.d(TAG, "Searching for articles with query: " + query);
        
        // Use search endpoint which works better for search queries
        Call<NewsResponse> searchCall = apiService.searchNews(
            query,                  // search query
            ARTICLES_PER_PAGE,      // max results
            0,                      // offset - start from beginning
            API_KEY                 // API key
        );
        
        searchCall.enqueue(new Callback<NewsResponse>() {
            @Override
            public void onResponse(Call<NewsResponse> call, Response<NewsResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    NewsResponse newsResponse = response.body();
                    List<Article> articles = newsResponse.getArticles();
                    
                    if (!articles.isEmpty()) {
                        Log.d(TAG, "Search returned " + articles.size() + " articles for query: " + query);
                        
                        // Save to database with special search category
                        for (Article article : articles) {
                            article.setCategory("search_" + query);
                            article.setFeatured(false);
                            article.setTimestamp(System.currentTimeMillis());
                        }
                        
                        saveArticlesToDb("search_" + query, articles, false);
                        
                        // Return articles to caller
                        mainHandler.post(() -> callback.onSuccess(articles));
                    } else {
                        Log.d(TAG, "Search returned empty list for query: " + query);
                        mainHandler.post(() -> callback.onSuccess(new ArrayList<>()));
                    }
                } else {
                    String errorMsg = "Failed to search articles";
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
} 