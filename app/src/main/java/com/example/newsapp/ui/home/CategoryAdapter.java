package com.example.newsapp.ui.home;

import android.view.ViewGroup;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import java.util.List;
import com.example.newsapp.R;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder> {
    private List<String> categories;
    private OnCategoryClickListener listener;
    private int selectedPosition = 0; // Default first item as selected

    public interface OnCategoryClickListener {
        void onCategoryClick(String category);
    }

    public CategoryAdapter(List<String> categories, OnCategoryClickListener listener) {
        this.categories = categories;
        this.listener = listener;
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        MaterialButton button = new MaterialButton(parent.getContext());

        // Create LayoutParams and set margins
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        layoutParams.setMargins(8, 8, 8, 8); // Set margins (left, top, right, bottom)

        button.setLayoutParams(layoutParams); // Apply layout params
        button.setTextSize(14);
        button.setPadding(32, 16, 32, 16);
        button.setCornerRadius(16); // Rounded corners

        return new CategoryViewHolder(button);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        holder.bind(categories.get(position), position == selectedPosition);
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }
    
    class CategoryViewHolder extends RecyclerView.ViewHolder {
        MaterialButton button;

        CategoryViewHolder(MaterialButton button) {
            super(button);
            this.button = button;
        }

        void bind(String category, boolean isSelected) {
            button.setText(category);
            
            // Set style based on selection state
            if (isSelected) {
                button.setBackgroundColor(button.getContext().getResources().getColor(R.color.primary_color));
                button.setTextColor(button.getContext().getResources().getColor(R.color.white));
                button.setStrokeWidth(0);
            } else {
                button.setBackgroundColor(button.getContext().getResources().getColor(R.color.white));
                button.setTextColor(button.getContext().getResources().getColor(R.color.text_primary));
                button.setStrokeWidth(1);
                button.setStrokeColor(button.getContext().getResources().getColorStateList(R.color.divider_color));
            }
            
            button.setOnClickListener(v -> {
                int previousSelected = selectedPosition;
                selectedPosition = getAdapterPosition();
                
                // Update the previously selected and newly selected items
                notifyItemChanged(previousSelected);
                notifyItemChanged(selectedPosition);
                
                if (listener != null) {
                    listener.onCategoryClick(category);
                }
            });
        }
    }
}
