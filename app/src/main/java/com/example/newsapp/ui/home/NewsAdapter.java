package com.example.newsapp.ui.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;
import com.example.newsapp.R;
import com.example.newsapp.data.models.Article;
import com.example.newsapp.ui.article.ArticleDetailFragment;
import java.util.List;

public class NewsAdapter extends RecyclerView.Adapter<NewsAdapter.NewsViewHolder> {
    private List<Article> articles;

    public NewsAdapter(List<Article> articles) {
        this.articles = articles;
    }

    @NonNull
    @Override
    public NewsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_news, parent, false);
        return new NewsViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NewsViewHolder holder, int position) {
        Article article = articles.get(position);
        holder.title.setText(article.getTitle());
        holder.description.setText(article.getDescription());

        holder.itemView.setOnClickListener(v -> {
            // Create an instance of ArticleDetailFragment and pass the article URL
            ArticleDetailFragment detailFragment = ArticleDetailFragment.newInstance(article.getUrl());
            // Replace the current fragment with the detail fragment
            ((FragmentActivity) v.getContext()).getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, detailFragment) // Ensure you have a container in your activity
                .addToBackStack(null)
                .commit();
        });
    }

    @Override
    public int getItemCount() {
        return articles.size();
    }

    static class NewsViewHolder extends RecyclerView.ViewHolder {
        TextView title, description;

        public NewsViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.news_title);
            description = itemView.findViewById(R.id.news_description);
        }
    }
}