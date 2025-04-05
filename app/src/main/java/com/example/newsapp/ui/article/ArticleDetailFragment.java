package com.example.newsapp.ui.article;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.fragment.app.Fragment;
import com.example.newsapp.R;

public class ArticleDetailFragment extends Fragment {
    private static final String ARG_URL = "url";

    public static ArticleDetailFragment newInstance(String url) {
        ArticleDetailFragment fragment = new ArticleDetailFragment();
        Bundle args = new Bundle();
        args.putString(ARG_URL, url);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_article_detail, container, false);
        WebView webView = view.findViewById(R.id.web_view);
        webView.setWebViewClient(new WebViewClient());
        
        if (getArguments() != null) {
            String url = getArguments().getString(ARG_URL);
            webView.loadUrl(url);
        }
        
        return view;
    }
}