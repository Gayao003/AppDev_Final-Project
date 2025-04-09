package com.example.newsapp.ui.bookmarks;

import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
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

import java.util.ArrayList;
import java.util.List;

public class BookmarksAdapter extends RecyclerView.Adapter<BookmarksAdapter.BookmarkViewHolder> {
    private static final String TAG = "BookmarksAdapter";
    private List<Article> articles;
    private final BookmarkActionListener actionListener;
    
    public interface BookmarkActionListener {
        void onRemoveBookmark(Article article);
    }
    
    public BookmarksAdapter(List<Article> articles, BookmarkActionListener actionListener) {
        this.articles = new ArrayList<>(articles);
        this.actionListener = actionListener;
    }
    
    public void updateArticles(List<Article> newArticles) {
        this.articles = new ArrayList<>(newArticles);
        notifyDataSetChanged();
    }
    
    public void removeArticle(Article article) {
        int position = articles.indexOf(article);
        if (position != -1) {
            articles.remove(position);
            notifyItemRemoved(position);
        }
    }
    
    @NonNull
    @Override
    public BookmarkViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new BookmarkViewHolder(
            LayoutInflater.from(parent.getContext()).inflate(R.layout.item_bookmark, parent, false)
        );
    }
    
    @Override
    public void onBindViewHolder(@NonNull BookmarkViewHolder holder, int position) {
        Article article = articles.get(position);
        holder.bind(article);
    }
    
    @Override
    public int getItemCount() {
        return articles.size();
    }
    
    class BookmarkViewHolder extends RecyclerView.ViewHolder {
        TextView title, description;
        ImageView imageView;
        ImageButton removeButton;
        
        public BookmarkViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.news_title);
            description = itemView.findViewById(R.id.news_description);
            imageView = itemView.findViewById(R.id.news_image);
            
            // Add the remove bookmark button if it exists
            removeButton = itemView.findViewById(R.id.remove_bookmark_button);
            if (removeButton == null) {
                // If the button doesn't exist in the layout, we'll handle removal through context menu or similar
                Log.d(TAG, "Remove bookmark button not found in layout");
            }
        }
        
        public void bind(Article article) {
            // Set title with fallback
            title.setText(article.getTitle() != null ? article.getTitle() : "No Title Available");
            
            // Set description with fallback
            String desc = article.getDescription();
            description.setText(desc != null && !desc.isEmpty() ? desc : "No description available");
            
            // Load image with proper error handling
            loadImage(article.getUrlToImage(), imageView);
            
            // Set up remove button if available
            if (removeButton != null) {
                removeButton.setVisibility(View.VISIBLE);
                removeButton.setOnClickListener(v -> {
                    if (actionListener != null) {
                        actionListener.onRemoveBookmark(article);
                    }
                });
            }
            
            // Set up item click to open article detail
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
            Glide.with(imageView.getContext())
                .load(imageUrl)
                .apply(requestOptions)
                .transition(DrawableTransitionOptions.withCrossFade(300))
                .error(R.drawable.error_image)
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