package com.example.valdker;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.VH> {

    public interface OnItemClick {
        void onClick(CategoryItem item);
    }

    private final List<CategoryItem> items;
    private final OnItemClick listener;

    public CategoryAdapter(List<CategoryItem> items, OnItemClick listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_category_chip, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        CategoryItem it = items.get(position);

        h.tvName.setText(it.name);

        String url = (it.iconUrl != null) ? it.iconUrl.trim() : "";
        if (url.startsWith("http://")) url = "https://" + url.substring("http://".length());

        Log.i("CATS", "Bind: " + it.name + " -> " + url);

        if (!url.isEmpty()) {
            Glide.with(h.imgIcon.getContext())
                    .load(url)
                    .circleCrop() // 🔥 bulat
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_report_image)
                    .into(h.imgIcon);
        } else {
            h.imgIcon.setImageResource(android.R.drawable.ic_menu_report_image);
        }

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(it);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName;
        ImageView imgIcon;

        VH(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            imgIcon = itemView.findViewById(R.id.imgIcon);
        }
    }
}
