package com.example.valdker.adapters;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.valdker.R;
import com.example.valdker.models.Category;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.VH> {

    private final List<Category> data = new ArrayList<>();
    private int selectedPos = RecyclerView.NO_POSITION;

    public interface Listener {
        void onCategorySelected(@NonNull Category c);
    }

    private Listener listener;

    public void setListener(Listener l) {
        this.listener = l;
    }

    public void setData(List<Category> list) {
        data.clear();
        if (list != null) data.addAll(list);

        // ✅ Default-select the first item if data is available
        selectedPos = data.isEmpty() ? RecyclerView.NO_POSITION : 0;

        notifyDataSetChanged();
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
        Category c = data.get(position);

        // TEXT
        String name = (c.name == null) ? "-" : c.name.trim();
        if (name.isEmpty()) name = "-";
        h.tvName.setText(name);

        // ICON
        if (c.iconUrl != null && !c.iconUrl.trim().isEmpty()) {
            Glide.with(h.imgIcon.getContext())
                    .load(c.iconUrl)
                    .placeholder(R.drawable.ic_category_placeholder)
                    .error(R.drawable.ic_category_placeholder)
                    .into(h.imgIcon);
        } else {
            h.imgIcon.setImageResource(R.drawable.ic_category_placeholder);
        }

        // SELECTED STYLE
        boolean isSelected = (position == selectedPos);
        applySelectedStyle(h, isSelected);
    }

    private void applySelectedStyle(@NonNull VH h, boolean isSelected) {
        if (isSelected) {
            h.cardCategory.setCardBackgroundColor(Color.parseColor("#F97316"));
            h.tvName.setTextColor(Color.WHITE);

            h.iconWrapper.setBackgroundTintList(
                    ColorStateList.valueOf(Color.parseColor("#FB923C"))
            );

            // ✅ Force icon color to white for selected state
            h.imgIcon.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);

        } else {
            h.cardCategory.setCardBackgroundColor(Color.WHITE);
            h.tvName.setTextColor(Color.parseColor("#111827"));

            h.iconWrapper.setBackgroundTintList(null);
            h.iconWrapper.setBackgroundResource(R.drawable.bg_category_icon);

            // ✅ Restore original icon color (remove filter)
            h.imgIcon.clearColorFilter();
        }
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    class VH extends RecyclerView.ViewHolder implements View.OnClickListener {
        MaterialCardView cardCategory;
        LinearLayout iconWrapper;
        ImageView imgIcon;
        TextView tvName;

        VH(@NonNull View itemView) {
            super(itemView);
            cardCategory = itemView.findViewById(R.id.cardCategory);
            iconWrapper = itemView.findViewById(R.id.iconWrapper);
            imgIcon = itemView.findViewById(R.id.imgIcon);
            tvName  = itemView.findViewById(R.id.tvName);

            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            int pos = getBindingAdapterPosition(); // ✅ Always get the latest adapter position
            if (pos == RecyclerView.NO_POSITION) return;

            int old = selectedPos;
            selectedPos = pos;

            // ✅ Update only the previously selected and the newly selected items
            if (old != RecyclerView.NO_POSITION) notifyItemChanged(old);
            notifyItemChanged(selectedPos);

            Category c = data.get(pos);
            if (listener != null) listener.onCategorySelected(c);
        }
    }
}
