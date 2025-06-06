package com.example.newsapp.ui.home;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
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
import com.example.newsapp.data.repository.BookmarkSyncRepository;
import com.example.newsapp.ui.article.ArticleDetailFragment;
import java.util.ArrayList;
import java.util.List;

public class NewsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final String TAG = "NewsAdapter";
    private List<Article> articles;
    private final boolean isFeatured;
    private BookmarkSyncRepository bookmarkSyncRepository;
    
    private static final int VIEW_TYPE_FEATURED = 1;
    private static final int VIEW_TYPE_REGULAR = 2;
    
    public NewsAdapter(List<Article> articles, boolean isFeatured, BookmarkSyncRepository bookmarkSyncRepository) {
        this.articles = new ArrayList<>(articles);
        this.isFeatured = isFeatured;
        this.bookmarkSyncRepository = bookmarkSyncRepository;
    }
    
    public void updateArticles(List<Article> newArticles) {
        Log.d(TAG, "Updating " + (isFeatured ? "featured" : "regular") + " articles: " + 
               newArticles.size() + " articles");
        this.articles = new ArrayList<>(newArticles);
        notifyDataSetChanged();
    }
    
    /**
     * Returns the current list of articles in the adapter
     * @return A new list containing all current articles
     */
    public List<Article> getArticles() {
        if (articles == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(articles);
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

    class NewsViewHolder extends RecyclerView.ViewHolder {
        TextView title, description;
        ImageView imageView;
        ImageButton bookmarkButton;

        public NewsViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.news_title);
            description = itemView.findViewById(R.id.news_description);
            imageView = itemView.findViewById(R.id.news_image);
            bookmarkButton = itemView.findViewById(R.id.bookmark_button);
            
            // Add action if bookmark button exists
            if (bookmarkButton != null) {
                setupBookmarkButton();
            }
        }
        
        private void setupBookmarkButton() {
            bookmarkButton.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    Article article = articles.get(position);
                    toggleBookmark(article, bookmarkButton);
                }
            });
        }

        public void bind(Article article) {
            // Set title with fallback
            title.setText(article.getTitle() != null ? article.getTitle() : "No Title Available");
            
            // Set description with fallback
            String desc = article.getDescription();
            description.setText(desc != null && !desc.isEmpty() ? desc : "No description available");
            
            // Load image with proper error handling
            loadImage(article.getUrlToImage(), imageView);
            
            // Update bookmark button if it exists
            if (bookmarkButton != null) {
                updateBookmarkIcon(article, bookmarkButton);
            }

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

    class FeaturedViewHolder extends RecyclerView.ViewHolder {
        TextView title, description;
        ImageView imageView;
        ImageButton bookmarkButton;

        public FeaturedViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.featured_news_title);
            description = itemView.findViewById(R.id.featured_news_description);
            imageView = itemView.findViewById(R.id.featured_news_image);
            bookmarkButton = itemView.findViewById(R.id.bookmark_button);
            
            // Add action if bookmark button exists
            if (bookmarkButton != null) {
                setupBookmarkButton();
            }
        }
        
        private void setupBookmarkButton() {
            bookmarkButton.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    Article article = articles.get(position);
                    toggleBookmark(article, bookmarkButton);
                }
            });
        }

        public void bind(Article article) {
            // Set title with fallback
            title.setText(article.getTitle() != null ? article.getTitle() : "No Title Available");
            
            // Set description with fallback
            String desc = article.getDescription();
            description.setText(desc != null && !desc.isEmpty() ? desc : "No description available");
            
            // Load image with proper error handling
            loadImage(article.getUrlToImage(), imageView);
            
            // Update bookmark button if it exists
            if (bookmarkButton != null) {
                updateBookmarkIcon(article, bookmarkButton);
            }

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
    
    private void updateBookmarkIcon(Article article, ImageButton bookmarkButton) {
        if (bookmarkSyncRepository != null) {
            bookmarkSyncRepository.isArticleBookmarked(article.getUrl(), isBookmarked -> {
                // Update the article in our list
                article.setBookmarked(isBookmarked);
                
                // Update the button icon
                if (isBookmarked) {
                    bookmarkButton.setImageResource(R.drawable.ic_bookmark_filled);
                } else {
                    bookmarkButton.setImageResource(R.drawable.ic_bookmark_border);
                }
            });
        }
    }
    
    private void toggleBookmark(Article article, ImageButton bookmarkButton) {
        if (article != null) {
            if (article.isBookmarked()) {
                // Remove bookmark
                bookmarkSyncRepository.removeBookmark(article.getUrl(), new BookmarkSyncRepository.SyncCallback() {
                    @Override
                    public void onSuccess(boolean syncedToCloud) {
                        article.setBookmarked(false);
                        bookmarkButton.setImageResource(R.drawable.ic_bookmark_border);
                        
                        String message = syncedToCloud 
                            ? "Removed from bookmarks and synced" 
                            : "Removed from bookmarks (offline mode)";
                            
                        showToastOnMainThread(bookmarkButton.getContext(), message);
                    }

                    @Override
                    public void onError(String message) {
                        showToastOnMainThread(bookmarkButton.getContext(), "Failed to remove bookmark: " + message);
                    }
                });
            } else {
                // Add bookmark
                bookmarkSyncRepository.addBookmark(article, new BookmarkSyncRepository.SyncCallback() {
                    @Override
                    public void onSuccess(boolean syncedToCloud) {
                        article.setBookmarked(true);
                        bookmarkButton.setImageResource(R.drawable.ic_bookmark_filled);
                        
                        String message = syncedToCloud 
                            ? "Added to bookmarks and synced" 
                            : "Added to bookmarks (offline mode)";
                            
                        showToastOnMainThread(bookmarkButton.getContext(), message);
                    }

                    @Override
                    public void onError(String message) {
                        showToastOnMainThread(bookmarkButton.getContext(), "Failed to bookmark: " + message);
                    }
                });
            }
        }
    }
    
    /**
     * Helper method to show Toast messages safely on the main thread
     */
    private void showToastOnMainThread(Context context, String message) {
        new Handler(Looper.getMainLooper()).post(() -> {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        });
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