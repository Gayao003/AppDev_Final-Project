package com.example.newsapp.data.models;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import com.google.gson.annotations.SerializedName;

@Entity(tableName = "articles")
public class Article {
    @PrimaryKey
    @NonNull
    private String url = "";
    
    private String title;
    private String description;
    
    @SerializedName("image")
    private String urlToImage;
    
    private String category;
    private boolean isFeatured;
    private long timestamp;
    private String content;
    
    @Ignore
    private Source source;
    
    // Constructors
    public Article() {
        // Required empty constructor
    }

    // Getters and Setters
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @NonNull
    public String getUrl() {
        return url;
    }

    public void setUrl(@NonNull String url) {
        this.url = url;
    }

    public String getUrlToImage() {
        return urlToImage;
    }

    public void setUrlToImage(String urlToImage) {
        this.urlToImage = urlToImage;
    }
    
    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public boolean isFeatured() {
        return isFeatured;
    }

    public void setFeatured(boolean featured) {
        isFeatured = featured;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public Source getSource() {
        return source;
    }
    
    public void setSource(Source source) {
        this.source = source;
    }
    
    // Helper method for debugging
    @Override
    public String toString() {
        return "Article{" +
                "title='" + title + '\'' +
                ", url='" + url + '\'' +
                ", image='" + urlToImage + '\'' +
                '}';
    }
    
    // Inner class for Source object
    public static class Source {
        private String name;
        private String url;
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public String getUrl() {
            return url;
        }
        
        public void setUrl(String url) {
            this.url = url;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        Article article = (Article) o;
        
        // We consider articles equal if they have the same URL
        return url != null ? url.equals(article.url) : article.url == null;
    }

    @Override
    public int hashCode() {
        // URL is the unique identifier
        return url != null ? url.hashCode() : 0;
    }
}