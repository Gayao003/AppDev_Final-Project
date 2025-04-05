package com.example.newsapp.api;

import com.example.newsapp.data.models.Article;
import com.example.newsapp.data.models.NewsResponse;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface GNewsApiService {
    @GET("search")
    Call<NewsResponse> searchNews(
        @Query("q") String query,
        @Query("max") int max,
        @Query("page") int page,
        @Query("token") String token
    );
}