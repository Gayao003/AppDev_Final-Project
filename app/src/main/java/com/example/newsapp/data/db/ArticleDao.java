package com.example.newsapp.data.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import com.example.newsapp.data.models.Article;

import java.util.List;

@Dao
public interface ArticleDao {
    @Query("SELECT * FROM articles WHERE category = :category AND isFeatured = :isFeatured ORDER BY timestamp DESC")
    List<Article> getArticlesByCategoryAndType(String category, boolean isFeatured);
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertArticles(List<Article> articles);
    
    @Query("DELETE FROM articles WHERE category = :category AND isFeatured = :isFeatured")
    void deleteArticlesByCategoryAndType(String category, boolean isFeatured);
    
    @Query("DELETE FROM articles WHERE timestamp < :expirationTime")
    void deleteOldArticles(long expirationTime);
    
    @Transaction
    default void updateCategoryArticles(String category, List<Article> articles, boolean isFeatured) {
        // Delete old articles of this category and type
        deleteArticlesByCategoryAndType(category, isFeatured);
        
        // Insert new articles
        insertArticles(articles);
    }
    
    // Methods for bookmarks functionality
    @Query("UPDATE articles SET isBookmarked = 1 WHERE url = :articleUrl")
    void bookmarkArticle(String articleUrl);
    
    @Query("UPDATE articles SET isBookmarked = 0 WHERE url = :articleUrl")
    void unbookmarkArticle(String articleUrl);
    
    @Query("SELECT * FROM articles WHERE isBookmarked = 1 ORDER BY timestamp DESC")
    List<Article> getBookmarkedArticles();
    
    @Query("SELECT isBookmarked FROM articles WHERE url = :articleUrl")
    boolean isArticleBookmarked(String articleUrl);
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertArticle(Article article);
    
    @Query("SELECT * FROM articles WHERE url = :url")
    List<Article> getArticlesByUrl(String url);
    
    // Methods for offline functionality
    @Query("UPDATE articles SET isDownloadedForOffline = 1 WHERE url = :articleUrl")
    void markArticleAsDownloaded(String articleUrl);
    
    @Query("UPDATE articles SET isDownloadedForOffline = 0 WHERE url = :articleUrl")
    void markArticleAsNotDownloaded(String articleUrl);
    
    @Query("SELECT isDownloadedForOffline FROM articles WHERE url = :articleUrl")
    boolean isArticleDownloadedForOffline(String articleUrl);
    
    @Query("SELECT * FROM articles WHERE isDownloadedForOffline = 1 ORDER BY timestamp DESC")
    List<Article> getDownloadedArticles();
} 