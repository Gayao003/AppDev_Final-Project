package com.example.newsapp.data.repository;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import com.example.newsapp.data.db.NewsDatabase;
import com.example.newsapp.data.models.Article;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Repository that handles syncing bookmarks between Room database and Firestore
 */
public class BookmarkSyncRepository {
    private static final String TAG = "BookmarkSyncRepository";
    private static final String BOOKMARKS_COLLECTION = "bookmarks";
    private static final String USER_BOOKMARKS_COLLECTION = "user_bookmarks";
    private static final String ARTICLES_COLLECTION = "articles";

    private final NewsDatabase database;
    private final FirebaseFirestore firestore;
    private final FirebaseAuth auth;
    private final Context context;
    private final Executor executor = Executors.newSingleThreadExecutor();

    public BookmarkSyncRepository(Context context) {
        this.context = context;
        this.database = NewsDatabase.getInstance(context);
        this.firestore = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
    }

    /**
     * Add a bookmark both locally and to Firestore if the user is signed in and online
     */
    public void addBookmark(Article article, SyncCallback callback) {
        // First add to local database
        executor.execute(() -> {
            try {
                // Set bookmark flag
                article.setBookmarked(true);
                
                // Save to local database
                database.articleDao().insertArticle(article);
                
                // Sync with Firestore if online and logged in
                if (isUserSignedIn() && isNetworkAvailable()) {
                    syncBookmarkToFirestore(article, callback);
                } else {
                    // Success but no cloud sync
                    if (callback != null) {
                        callback.onSuccess(false);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error adding bookmark locally", e);
                if (callback != null) {
                    callback.onError("Failed to bookmark article: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Remove a bookmark both locally and from Firestore if the user is signed in and online
     */
    public void removeBookmark(String articleUrl, SyncCallback callback) {
        executor.execute(() -> {
            try {
                // Remove bookmark locally
                database.articleDao().unbookmarkArticle(articleUrl);
                
                // Remove from Firestore if online and logged in
                if (isUserSignedIn() && isNetworkAvailable()) {
                    removeBookmarkFromFirestore(articleUrl, callback);
                } else {
                    // Success but no cloud sync
                    if (callback != null) {
                        callback.onSuccess(false);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error removing bookmark locally", e);
                if (callback != null) {
                    callback.onError("Failed to remove bookmark: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Get all bookmarked articles, first from local database, then sync with Firestore
     */
    public void getBookmarkedArticles(SyncCallback callback) {
        executor.execute(() -> {
            try {
                // First get local bookmarks
                List<Article> localBookmarks = database.articleDao().getBookmarkedArticles();
                
                // Return local bookmarks immediately
                if (callback != null) {
                    callback.onArticlesLoaded(localBookmarks, false);
                }
                
                // Then try to sync with Firestore if online and logged in
                if (isUserSignedIn() && isNetworkAvailable()) {
                    syncBookmarksFromFirestore(callback);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting local bookmarks", e);
                if (callback != null) {
                    callback.onError("Failed to get bookmarks: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Check if an article is bookmarked (local check only for speed)
     */
    public void isArticleBookmarked(String articleUrl, BookmarkStatusCallback callback) {
        executor.execute(() -> {
            try {
                boolean isBookmarked = database.articleDao().isArticleBookmarked(articleUrl);
                if (callback != null) {
                    callback.onResult(isBookmarked);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error checking bookmark status", e);
                if (callback != null) {
                    callback.onResult(false);
                }
            }
        });
    }

    /**
     * Synchronize bookmarks with Firestore when user logs in
     */
    public void syncBookmarksOnLogin(SyncCallback callback) {
        if (isUserSignedIn() && isNetworkAvailable()) {
            executor.execute(() -> {
                // First get local bookmarks
                List<Article> localBookmarks = database.articleDao().getBookmarkedArticles();
                
                // Upload all local bookmarks that aren't already in Firestore
                uploadLocalBookmarksToFirestore(localBookmarks);
                
                // Then get bookmarks from Firestore and merge with local
                syncBookmarksFromFirestore(callback);
            });
        }
    }

    // Private helper methods

    private boolean isUserSignedIn() {
        FirebaseUser currentUser = auth.getCurrentUser();
        return currentUser != null;
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            return activeNetworkInfo != null && activeNetworkInfo.isConnected();
        }
        return false;
    }

    private String getCurrentUserId() {
        FirebaseUser user = auth.getCurrentUser();
        return (user != null) ? user.getUid() : null;
    }

    /**
     * Convert URL to a safe document ID for Firestore
     */
    private String encodeUrlForFirestore(String url) {
        if (url == null) return null;
        return url.replaceAll("[.#$\\[\\]/]", "_");
    }

    private void syncBookmarkToFirestore(Article article, SyncCallback callback) {
        String userId = getCurrentUserId();
        if (userId == null) {
            if (callback != null) {
                callback.onSuccess(false);
            }
            return;
        }

        // Create a safe document ID from the URL
        String safeDocId = encodeUrlForFirestore(article.getUrl());
        
        // First save the article data
        Map<String, Object> articleData = new HashMap<>();
        articleData.put("url", article.getUrl());
        articleData.put("title", article.getTitle());
        articleData.put("description", article.getDescription());
        articleData.put("urlToImage", article.getUrlToImage());
        articleData.put("content", article.getContent());
        articleData.put("category", article.getCategory());
        articleData.put("timestamp", article.getTimestamp());

        // Save article to Firestore
        firestore.collection(ARTICLES_COLLECTION)
                .document(safeDocId)
                .set(articleData, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    // Create bookmark reference
                    Map<String, Object> bookmarkData = new HashMap<>();
                    bookmarkData.put("articleUrl", article.getUrl());
                    bookmarkData.put("timestamp", System.currentTimeMillis());

                    // Add bookmark to user's bookmarks collection
                    firestore.collection(USER_BOOKMARKS_COLLECTION)
                            .document(userId)
                            .collection(BOOKMARKS_COLLECTION)
                            .document(safeDocId)
                            .set(bookmarkData)
                            .addOnSuccessListener(aVoid1 -> {
                                if (callback != null) {
                                    callback.onSuccess(true);
                                }
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to add bookmark to Firestore", e);
                                if (callback != null) {
                                    callback.onSuccess(false);
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to save article to Firestore", e);
                    if (callback != null) {
                        callback.onSuccess(false);
                    }
                });
    }

    private void removeBookmarkFromFirestore(String articleUrl, SyncCallback callback) {
        String userId = getCurrentUserId();
        if (userId == null) {
            if (callback != null) {
                callback.onSuccess(false);
            }
            return;
        }

        // Remove bookmark from user's bookmarks collection
        String safeDocId = encodeUrlForFirestore(articleUrl);
        firestore.collection(USER_BOOKMARKS_COLLECTION)
                .document(userId)
                .collection(BOOKMARKS_COLLECTION)
                .document(safeDocId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    if (callback != null) {
                        callback.onSuccess(true);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to remove bookmark from Firestore", e);
                    if (callback != null) {
                        callback.onSuccess(false);
                    }
                });
    }

    private void syncBookmarksFromFirestore(SyncCallback callback) {
        String userId = getCurrentUserId();
        if (userId == null) {
            return;
        }

        // Get user's bookmarks from Firestore
        firestore.collection(USER_BOOKMARKS_COLLECTION)
                .document(userId)
                .collection(BOOKMARKS_COLLECTION)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        List<String> articleUrls = new ArrayList<>();
                        
                        // Extract article URLs
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            String articleUrl = document.getString("articleUrl");
                            if (articleUrl != null) {
                                articleUrls.add(articleUrl);
                            }
                        }
                        
                        // Fetch full article data for each bookmark
                        fetchArticlesFromFirestore(articleUrls, callback);
                    } else {
                        Log.e(TAG, "Error getting bookmarks from Firestore", task.getException());
                        if (callback != null) {
                            callback.onError("Failed to sync bookmarks: " + task.getException().getMessage());
                        }
                    }
                });
    }

    private void fetchArticlesFromFirestore(List<String> articleUrls, SyncCallback callback) {
        if (articleUrls.isEmpty()) {
            if (callback != null) {
                callback.onArticlesLoaded(new ArrayList<>(), true);
            }
            return;
        }

        List<Article> articles = new ArrayList<>();
        final int[] completed = {0};
        final boolean[] hasErrors = {false};

        for (String url : articleUrls) {
            String safeDocId = encodeUrlForFirestore(url);
            firestore.collection(ARTICLES_COLLECTION)
                    .document(safeDocId)
                    .get()
                    .addOnCompleteListener(task -> {
                        completed[0]++;
                        
                        if (task.isSuccessful() && task.getResult() != null) {
                            DocumentSnapshot document = task.getResult();
                            
                            if (document.exists()) {
                                Article article = documentToArticle(document);
                                article.setBookmarked(true);
                                articles.add(article);
                                
                                // Save to local database
                                executor.execute(() -> database.articleDao().insertArticle(article));
                            }
                        } else {
                            hasErrors[0] = true;
                        }
                        
                        // Check if all articles have been processed
                        if (completed[0] == articleUrls.size()) {
                            // Return articles to caller
                            if (callback != null) {
                                callback.onArticlesLoaded(articles, true);
                            }
                        }
                    });
        }
    }

    private void uploadLocalBookmarksToFirestore(List<Article> localBookmarks) {
        String userId = getCurrentUserId();
        if (userId == null || localBookmarks.isEmpty()) {
            return;
        }

        for (Article article : localBookmarks) {
            syncBookmarkToFirestore(article, null);
        }
    }

    private Article documentToArticle(DocumentSnapshot document) {
        Article article = new Article();
        
        // Get the actual URL from the document data, not the encoded document ID
        article.setUrl(document.getString("url"));
        article.setTitle(document.getString("title"));
        article.setDescription(document.getString("description"));
        article.setUrlToImage(document.getString("urlToImage"));
        article.setContent(document.getString("content"));
        article.setCategory(document.getString("category"));
        
        Long timestamp = document.getLong("timestamp");
        if (timestamp != null) {
            article.setTimestamp(timestamp);
        } else {
            article.setTimestamp(System.currentTimeMillis());
        }
        
        return article;
    }

    /**
     * Callback interface for bookmark operations
     */
    public interface SyncCallback {
        default void onSuccess(boolean syncedToCloud) {}
        default void onError(String message) {}
        default void onArticlesLoaded(List<Article> articles, boolean fromCloud) {}
    }
    
    /**
     * Callback for bookmark status check
     */
    public interface BookmarkStatusCallback {
        void onResult(boolean isBookmarked);
    }
} 