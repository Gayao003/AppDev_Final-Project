package com.example.newsapp.ui.home;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.newsapp.data.models.Article;
import com.example.newsapp.data.repository.NewsRepository;
import java.util.ArrayList;
import java.util.List;

public class HomeViewModel extends AndroidViewModel {
    private final NewsRepository newsRepository;
    
    // LiveData for UI state
    private final MutableLiveData<List<Article>> featuredArticles = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Article>> regularArticles = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> hasMorePages = new MutableLiveData<>(true);
    
    // Pagination state
    private int currentPage = 1;
    private String currentCategory = "technology";
    private boolean isLoadingMore = false;
    
    public HomeViewModel(Application application) {
        super(application);
        newsRepository = new NewsRepository(application);
    }
    
    // Getters for LiveData
    public LiveData<List<Article>> getFeaturedArticles() { return featuredArticles; }
    public LiveData<List<Article>> getRegularArticles() { return regularArticles; }
    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<String> getErrorMessage() { return errorMessage; }
    public LiveData<Boolean> getHasMorePages() { return hasMorePages; }
    
    public void loadNewsForCategory(String category) {
        currentCategory = category;
        currentPage = 1;
        isLoading.setValue(true);
        hasMorePages.setValue(true);
        
        // Load featured news
        newsRepository.getNewsByCategory(category, true, 5, new NewsRepository.NewsCallback() {
            @Override
            public void onSuccess(List<Article> articles) {
                featuredArticles.postValue(articles);
            }
            
            @Override
            public void onError(String message) {
                errorMessage.postValue(message);
            }
        });
        
        // Load regular news
        loadRegularNews();
    }
    
    public void loadNextPage() {
        if (!isLoadingMore && Boolean.TRUE.equals(hasMorePages.getValue())) {
            isLoadingMore = true;
            currentPage++;
            loadRegularNews();
        }
    }
    
    private void loadRegularNews() {
        newsRepository.getNewsByCategory(currentCategory, false, 10, 
            new NewsRepository.PaginatedNewsCallback() {
                @Override
                public void onSuccess(List<Article> articles) {
                    // Handled in onSuccessWithHasMore
                }
                
                @Override
                public void onError(String message) {
                    isLoadingMore = false;
                    errorMessage.postValue(message);
                }
                
                @Override
                public void onSuccessWithHasMore(List<Article> articles, boolean morePages) {
                    isLoadingMore = false;
                    isLoading.postValue(false);
                    hasMorePages.postValue(morePages);
                    
                    if (currentPage == 1) {
                        regularArticles.postValue(articles);
                    } else {
                        List<Article> currentArticles = regularArticles.getValue();
                        if (currentArticles != null) {
                            List<Article> newArticles = new ArrayList<>(currentArticles);
                            newArticles.addAll(articles);
                            regularArticles.postValue(newArticles);
                        }
                    }
                }
            });
    }
} 