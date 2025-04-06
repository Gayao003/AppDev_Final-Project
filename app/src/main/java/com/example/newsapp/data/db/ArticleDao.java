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
} 