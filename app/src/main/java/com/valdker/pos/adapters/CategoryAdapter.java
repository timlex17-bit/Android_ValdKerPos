package com.valdker.pos.adapters;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.valdker.pos.R;
import com.valdker.pos.models.Category;
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

    public void setListener(@Nullable Listener l) {
        this.listener = l;
    }

    public void setData(@Nullable List<Category> list) {
        data.clear();
        if (list != null) data.addAll(list);

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

        String name = (c.name == null) ? "-" : c.name.trim();
        if (name.isEmpty()) name = "-";
        h.tvName.setText(name);

        boolean isAll = c.id == -1;

        // reset recycle state
        Glide.with(h.imgIcon.getContext()).clear(h.imgIcon);
        h.imgIcon.setImageDrawable(null);
        h.imgIcon.clearColorFilter();
        h.imgIcon.setVisibility(View.GONE);

        h.iconWrapper.setVisibility(View.GONE);
        h.iconWrapper.setBackgroundTintList(null);
        h.iconWrapper.setBackgroundResource(R.drawable.bg_category_icon);

        String url = normalizeUrl(c.iconUrl);
        boolean hasRemoteIcon = !isAll && !url.isEmpty();

        if (hasRemoteIcon) {
            h.iconWrapper.setVisibility(View.VISIBLE);
            h.imgIcon.setVisibility(View.VISIBLE);

            Glide.with(h.imgIcon.getContext())
                    .load(url)
                    .fitCenter()
                    .placeholder(R.drawable.ic_categories)
                    .error(R.drawable.ic_categories)
                    .into(h.imgIcon);
        }

        boolean isSelected = (position == selectedPos);
        applySelectedStyle(h, isSelected, hasRemoteIcon);
    }

    /**
     * Mewarnai chip kategori sesuai terpilih atau tidak.
     *
     * <p>Warnanya diambil dari palet aplikasi, bukan hex yang ditulis di sini.
     * Sebelumnya chip terpilih memakai "#6204BF" dan "#9D48F1" yang ditulis
     * ulang di berkas ini dan sekali lagi di item_category_chip.xml, sehingga
     * mengganti warna merek berarti mencarinya di dua tempat yang berbeda.
     */
    private void applySelectedStyle(@NonNull VH h, boolean isSelected, boolean hasRemoteIcon) {
        Context ctx = h.cardCategory.getContext();

        int brand = ContextCompat.getColor(ctx, R.color.brand_primary);
        int surface = ContextCompat.getColor(ctx, R.color.surface);
        int stroke = ContextCompat.getColor(ctx, R.color.stroke);

        h.cardCategory.setCardBackgroundColor(isSelected ? brand : surface);
        h.cardCategory.setStrokeColor(isSelected ? brand : stroke);
        h.tvName.setTextColor(ContextCompat.getColor(
                ctx, isSelected ? R.color.text_on_brand : R.color.text_primary));

        if (h.iconWrapper.getVisibility() != View.VISIBLE) return;

        if (isSelected) {
            h.iconWrapper.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(ctx, R.color.brand_tint)));

            if (!hasRemoteIcon) {
                h.imgIcon.setColorFilter(
                        ContextCompat.getColor(ctx, R.color.brand_on_tint), PorterDuff.Mode.SRC_IN);
            } else {
                h.imgIcon.clearColorFilter();
            }
            return;
        }

        h.iconWrapper.setBackgroundTintList(null);
        h.iconWrapper.setBackgroundResource(R.drawable.bg_category_icon);
        h.imgIcon.clearColorFilter();
    }

    @NonNull
    private String normalizeUrl(@Nullable String raw) {
        if (raw == null) return "";

        String url = raw.trim();
        if (url.isEmpty()) return "";
        if ("null".equalsIgnoreCase(url)) return "";
        if ("/null".equalsIgnoreCase(url)) return "";

        return url;
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    class VH extends RecyclerView.ViewHolder implements View.OnClickListener {
        final MaterialCardView cardCategory;
        final LinearLayout iconWrapper;
        final ImageView imgIcon;
        final TextView tvName;

        VH(@NonNull View itemView) {
            super(itemView);
            cardCategory = itemView.findViewById(R.id.cardCategory);
            iconWrapper = itemView.findViewById(R.id.iconWrapper);
            imgIcon = itemView.findViewById(R.id.imgIcon);
            tvName = itemView.findViewById(R.id.tvName);

            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            int pos = getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;

            int old = selectedPos;
            selectedPos = pos;

            if (old != RecyclerView.NO_POSITION) notifyItemChanged(old);
            notifyItemChanged(selectedPos);

            Category c = data.get(pos);
            if (listener != null) listener.onCategorySelected(c);
        }
    }
}