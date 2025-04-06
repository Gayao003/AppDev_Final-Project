package com.example.newsapp.ui.home;

import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestOptions;
import com.example.newsapp.R;
import com.example.newsapp.data.models.Article;
import com.example.newsapp.ui.article.ArticleDetailFragment;
import java.util.List;

public class NewsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final String TAG = "NewsAdapter";
    private List<Article> articles;
    private boolean isFeatured;

    public NewsAdapter(List<Article> articles, boolean isFeatured) {
        this.articles = articles;
        this.isFeatured = isFeatured;
        logArticles();
    }

    public void updateArticles(List<Article> newArticles) {
        this.articles = newArticles;
        logArticles();
        notifyDataSetChanged();
    }
    
    private void logArticles() {
        Log.d(TAG, "Articles count: " + (articles != null ? articles.size() : 0));
        if (articles != null && !articles.isEmpty()) {
            for (int i = 0; i < Math.min(articles.size(), 2); i++) {
                Article article = articles.get(i);
                Log.d(TAG, "Article " + i + ": " + article.toString());
                Log.d(TAG, "  - title: " + article.getTitle());
                Log.d(TAG, "  - desc: " + (article.getDescription() != null ? 
                    article.getDescription().substring(0, Math.min(50, article.getDescription().length())) + "..." : "null"));
                Log.d(TAG, "  - image: " + article.getUrlToImage());
            }
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (isFeatured) {
            return new FeaturedViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_featured_news, parent, false));
        }
        return new NewsViewHolder(LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_news, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (articles != null && position < articles.size()) {
            Article article = articles.get(position);
            
            // Validate article has required data
            if (article == null) {
                Log.e(TAG, "Null article at position " + position);
                return;
            }
            
            if (TextUtils.isEmpty(article.getTitle())) {
                Log.w(TAG, "Article at position " + position + " has empty title");
            }
            
            // Bind data to viewholder
            if (isFeatured) {
                ((FeaturedViewHolder) holder).bind(article);
            } else {
                ((NewsViewHolder) holder).bind(article);
            }
        } else {
            Log.e(TAG, "Invalid position: " + position + ", articles size: " + (articles != null ? articles.size() : 0));
        }
    }

    @Override
    public int getItemCount() {
        return articles.size();
    }

    static class NewsViewHolder extends RecyclerView.ViewHolder {
        TextView title, description;
        ImageView imageView;

        public NewsViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.news_title);
            description = itemView.findViewById(R.id.news_description);
            imageView = itemView.findViewById(R.id.news_image);
        }

        public void bind(Article article) {
            // Set title with fallback
            title.setText(article.getTitle() != null ? article.getTitle() : "No Title Available");
            
            // Set description with fallback
            String desc = article.getDescription();
            description.setText(desc != null && !desc.isEmpty() ? desc : "No description available");
            
            // Load image with proper error handling
            loadImage(article.getUrlToImage(), imageView);

            itemView.setOnClickListener(v -> {
                if (article.getUrl() != null && !article.getUrl().isEmpty()) {
                    // Create an instance of ArticleDetailFragment and pass the article URL
                    ArticleDetailFragment detailFragment = ArticleDetailFragment.newInstance(article.getUrl());
                    // Replace the current fragment with the detail fragment
                    ((FragmentActivity) v.getContext()).getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, detailFragment)
                        .addToBackStack(null)
                        .commit();
                }
            });
        }
    }

    static class FeaturedViewHolder extends RecyclerView.ViewHolder {
        TextView title, description;
        ImageView imageView;

        public FeaturedViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.featured_news_title);
            description = itemView.findViewById(R.id.featured_news_description);
            imageView = itemView.findViewById(R.id.featured_news_image);
        }

        public void bind(Article article) {
            // Set title with fallback
            title.setText(article.getTitle() != null ? article.getTitle() : "No Title Available");
            
            // Set description with fallback
            String desc = article.getDescription();
            description.setText(desc != null && !desc.isEmpty() ? desc : "No description available");
            
            // Load image with proper error handling
            loadImage(article.getUrlToImage(), imageView);

            itemView.setOnClickListener(v -> {
                if (article.getUrl() != null && !article.getUrl().isEmpty()) {
                    // Create an instance of ArticleDetailFragment and pass the article URL
                    ArticleDetailFragment detailFragment = ArticleDetailFragment.newInstance(article.getUrl());
                    // Replace the current fragment with the detail fragment
                    ((FragmentActivity) v.getContext()).getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, detailFragment)
                        .addToBackStack(null)
                        .commit();
                }
            });
        }
    }
    
    private static void loadImage(String imageUrl, ImageView imageView) {
        RequestOptions requestOptions = new RequestOptions()
            .placeholder(R.drawable.placeholder_image)
            .error(R.drawable.error_image)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .transform(new CenterCrop(), new RoundedCorners(16));
            
        // Check if URL is valid and not empty
        if (!TextUtils.isEmpty(imageUrl) && (imageUrl.startsWith("http://") || imageUrl.startsWith("https://"))) {
            Log.d(TAG, "Loading image: " + imageUrl);
            Glide.with(imageView.getContext())
                .load(imageUrl)
                .apply(requestOptions)
                .transition(DrawableTransitionOptions.withCrossFade(300))
                .error(R.drawable.error_image)
                .into(imageView);
        } else {
            // If URL is invalid, directly show error image
            Log.d(TAG, "Invalid image URL: " + imageUrl + ", showing placeholder");
            Glide.with(imageView.getContext())
                .load(R.drawable.error_image)
                .apply(requestOptions)
                .into(imageView);
        }
    }
}