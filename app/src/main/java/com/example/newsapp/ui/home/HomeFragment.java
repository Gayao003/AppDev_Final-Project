package com.example.newsapp.ui.home;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.newsapp.R;
import com.example.newsapp.api.GNewsApiService;
import com.example.newsapp.api.RetrofitClient;
import com.example.newsapp.data.models.Article;
import com.example.newsapp.data.models.NewsResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HomeFragment extends Fragment {
    private RecyclerView recyclerView;
    private NewsAdapter newsAdapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        recyclerView = view.findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        newsAdapter = new NewsAdapter(new ArrayList<>());
        recyclerView.setAdapter(newsAdapter);
        fetchNews();
        return view;
    }

    private void fetchNews() {
        GNewsApiService apiService = RetrofitClient.getRetrofitInstance().create(GNewsApiService.class);
        String apiKey = "893c288590b71ad4c86fa2fb5cb31b6c";
        Call<NewsResponse> call = apiService.searchNews("technology", 20, 1, apiKey);

        call.enqueue(new Callback<NewsResponse>() {
            @Override
            public void onResponse(Call<NewsResponse> call, Response<NewsResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Article> articles = response.body().getArticles();
                    Log.d("HomeFragment", "Total results: " + response.body().getTotalResults());
                    if (articles.isEmpty()) {
                        Log.d("HomeFragment", "No articles found.");
                    } else {
                        newsAdapter = new NewsAdapter(articles);
                        recyclerView.setAdapter(newsAdapter);
                    }
                } else {
                    Log.e("HomeFragment", "Response code: " + response.code() + ", message: " + response.message());
                    if (response.errorBody() != null) {
                        try {
                            Log.e("HomeFragment", "Error body: " + response.errorBody().string());
                        } catch (IOException e) {
                            Log.e("HomeFragment", "Error reading error body: " + e.getMessage());
                        }
                    }
                    Toast.makeText(getContext(), "Failed to fetch news", Toast.LENGTH_SHORT).show();
                }
                Log.d("HomeFragment", call.request().url().toString());
            }

            @Override
            public void onFailure(Call<NewsResponse> call, Throwable t) {
                Toast.makeText(getContext(), "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}