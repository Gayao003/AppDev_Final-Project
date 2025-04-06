package com.example.newsapp.data.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.newsapp.data.models.Article;

@Database(entities = {Article.class}, version = 2, exportSchema = false)
public abstract class NewsDatabase extends RoomDatabase {
    
    private static final String DATABASE_NAME = "news_db";
    private static NewsDatabase instance;
    
    public abstract ArticleDao articleDao();
    
    public static synchronized NewsDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(
                    context.getApplicationContext(),
                    NewsDatabase.class,
                    DATABASE_NAME)
                    .fallbackToDestructiveMigration()
                    .build();
        }
        return instance;
    }
} 