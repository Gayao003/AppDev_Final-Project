package com.example.newsapp.ui.search;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.newsapp.data.models.Article;
import com.example.newsapp.data.repository.NewsRepository;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SearchViewModel extends AndroidViewModel {
    private final NewsRepository newsRepository;
    
    // LiveData for UI state
    private final MutableLiveData<List<Article>> searchResults = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    
    // Popular searches suggestions
    private final List<String> popularSearches = Arrays.asList(
        "Technology", "Climate Change", "Sports", "Politics", "Health"
    );
    
    // Recent searches (would ideally be stored in SharedPreferences)
    private final List<String> recentSearches = new ArrayList<>();
    private static final int MAX_RECENT_SEARCHES = 5;
    
    public SearchViewModel(Application application) {
        super(application);
        newsRepository = new NewsRepository(application);
    }
    
    // Getters for LiveData
    public LiveData<List<Article>> getSearchResults() { return searchResults; }
    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<String> getErrorMessage() { return errorMessage; }
    
    // Getters for suggestions
    public List<String> getPopularSearches() { return popularSearches; }
    public List<String> getRecentSearches() { return recentSearches; }
    
    public void performSearch(String query) {
        if (query == null || query.trim().isEmpty()) {
            return;
        }
        
        // Add to recent searches
        addToRecentSearches(query);
        
        // Show loading state
        isLoading.setValue(true);
        
        // Clear previous results
        searchResults.setValue(new ArrayList<>());
        
        // Perform search
        newsRepository.searchArticles(query, new NewsRepository.NewsCallback() {
            @Override
            public void onSuccess(List<Article> articles) {
                isLoading.setValue(false);
                searchResults.setValue(articles);
            }
            
            @Override
            public void onError(String message) {
                isLoading.setValue(false);
                errorMessage.setValue(message);
            }
        });
    }
    
    private void addToRecentSearches(String query) {
        // Remove if already exists (to reorder)
        recentSearches.remove(query);
        
        // Add to the beginning
        recentSearches.add(0, query);
        
        // Trim if needed
        if (recentSearches.size() > MAX_RECENT_SEARCHES) {
            recentSearches.remove(recentSearches.size() - 1);
        }
    }
    
    public void removeRecentSearch(String query) {
        recentSearches.remove(query);
    }
} 