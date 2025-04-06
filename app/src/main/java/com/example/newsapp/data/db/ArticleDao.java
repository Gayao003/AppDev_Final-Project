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
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertArticles(List<Article> articles);
    
    @Query("SELECT * FROM articles WHERE category = :category AND isFeatured = :isFeatured ORDER BY timestamp DESC")
    List<Article> getArticlesByCategoryAndType(String category, boolean isFeatured);
    
    @Query("SELECT * FROM articles WHERE category = :category ORDER BY timestamp DESC")
    List<Article> getArticlesByCategory(String category);
    
    @Query("DELETE FROM articles WHERE category = :category")
    void deleteArticlesByCategory(String category);
    
    @Query("DELETE FROM articles WHERE timestamp < :timestamp")
    void deleteOldArticles(long timestamp);
    
    @Transaction
    default void updateCategoryArticles(String category, List<Article> articles, boolean isFeatured) {
        // Delete existing articles of this category and type
        if (isFeatured) {
            deleteArticlesByCategory(category);
        }
        // Insert new articles
        insertArticles(articles);
    }
} 