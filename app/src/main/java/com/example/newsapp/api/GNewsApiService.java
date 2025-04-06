package com.example.newsapp.api;

import com.example.newsapp.data.models.NewsResponse;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface GNewsApiService {
    @GET("search")
    Call<NewsResponse> searchNews(
        @Query("q") String query,
        @Query("max") int max,
        @Query("offset") int offset,
        @Query("apikey") String apiKey
    );
    
    @GET("top-headlines")
    Call<NewsResponse> getTopHeadlines(
        @Query("topic") String topic,
        @Query("lang") String language,
        @Query("country") String country,
        @Query("max") int max,
        @Query("page") int page,
        @Query("apikey") String apiKey
    );
}