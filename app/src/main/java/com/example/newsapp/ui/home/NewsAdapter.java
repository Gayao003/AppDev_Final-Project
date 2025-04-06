package com.example.newsapp.ui.home;

import android.text.TextUtils;
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
    private List<Article> articles;
    private boolean isFeatured;

    public NewsAdapter(List<Article> articles, boolean isFeatured) {
        this.articles = articles;
        this.isFeatured = isFeatured;
    }

    public void updateArticles(List<Article> newArticles) {
        this.articles = newArticles;
        notifyDataSetChanged();
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
        Article article = articles.get(position);
        if (isFeatured) {
            ((FeaturedViewHolder) holder).bind(article);
        } else {
            ((NewsViewHolder) holder).bind(article);
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
            title.setText(article.getTitle());
            description.setText(article.getDescription());
            
            loadImage(article.getUrlToImage(), imageView);

            itemView.setOnClickListener(v -> {
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
            title.setText(article.getTitle());
            description.setText(article.getDescription());
            
            loadImage(article.getUrlToImage(), imageView);

            itemView.setOnClickListener(v -> {
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
    }
    
    private static void loadImage(String imageUrl, ImageView imageView) {
        RequestOptions requestOptions = new RequestOptions()
            .placeholder(R.drawable.placeholder_image)
            .error(R.drawable.error_image)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .transform(new CenterCrop(), new RoundedCorners(16));
            
        // Check if URL is valid and not empty
        if (!TextUtils.isEmpty(imageUrl) && (imageUrl.startsWith("http://") || imageUrl.startsWith("https://"))) {
            Glide.with(imageView.getContext())
                .load(imageUrl)
                .apply(requestOptions)
                .transition(DrawableTransitionOptions.withCrossFade(300))
                .into(imageView);
        } else {
            // If URL is invalid, directly show error image
            Glide.with(imageView.getContext())
                .load(R.drawable.error_image)
                .apply(requestOptions)
                .into(imageView);
        }
    }
}