package com.example.newsapp.ui.home;

import android.os.Bundle;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HomeFragment extends Fragment {
    private RecyclerView categoriesRecyclerView;
    private RecyclerView featuredRecyclerView;
    private RecyclerView newsRecyclerView;
    private CategoryAdapter categoryAdapter;
    private NewsAdapter featuredAdapter;
    private NewsAdapter newsAdapter;
    private List<String> categories = Arrays.asList("Technology", "Business", "Sports", "Entertainment", "Health");

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        
        setupCategoriesRecyclerView(view);
        setupFeaturedRecyclerView(view);
        setupNewsRecyclerView(view);
        
        fetchNews("technology", 5, true); // Featured news
        fetchNews("technology", 15, false); // Regular news
        
        return view;
    }

    private void setupCategoriesRecyclerView(View view) {
        categoriesRecyclerView = view.findViewById(R.id.categories_recycler_view);
        categoriesRecyclerView.setLayoutManager(
            new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        categoryAdapter = new CategoryAdapter(categories, category -> {
            fetchNews(category.toLowerCase(), 15, false);
        });
        categoriesRecyclerView.setAdapter(categoryAdapter);
    }

    private void setupFeaturedRecyclerView(View view) {
        featuredRecyclerView = view.findViewById(R.id.featured_recycler_view);
        featuredRecyclerView.setLayoutManager(
            new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        featuredAdapter = new NewsAdapter(new ArrayList<>(), true);
        featuredRecyclerView.setAdapter(featuredAdapter);
    }

    private void setupNewsRecyclerView(View view) {
        newsRecyclerView = view.findViewById(R.id.news_recycler_view);
        newsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        newsAdapter = new NewsAdapter(new ArrayList<>(), false);
        newsRecyclerView.setAdapter(newsAdapter);
    }

    private void fetchNews(String category, int maxResults, boolean isFeatured) {
        GNewsApiService apiService = RetrofitClient.getRetrofitInstance().create(GNewsApiService.class);
        String apiKey = "893c288590b71ad4c86fa2fb5cb31b6c";
        Call<NewsResponse> call = apiService.searchNews(category, maxResults, 1, apiKey);

        call.enqueue(new Callback<NewsResponse>() {
            @Override
            public void onResponse(Call<NewsResponse> call, Response<NewsResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Article> articles = response.body().getArticles();
                    if (!articles.isEmpty()) {
                        if (isFeatured) {
                            featuredAdapter.updateArticles(articles);
                        } else {
                            newsAdapter.updateArticles(articles);
                        }
                    }
                } else {
                    Toast.makeText(getContext(), "Failed to fetch news", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<NewsResponse> call, Throwable t) {
                Toast.makeText(getContext(), "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}