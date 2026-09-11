package com.valdker.pos.ui.dashboard;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import com.valdker.pos.R;

import java.util.List;

public class DashboardAdapter extends RecyclerView.Adapter<DashboardAdapter.VH> {

    public interface Listener {
        void onClick(@NonNull DashboardItem item);
    }

    private final List<DashboardItem> data;
    private final Listener listener;

    public DashboardAdapter(@NonNull List<DashboardItem> data, @NonNull Listener listener) {
        this.data = data;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_dashboard_tile, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        DashboardItem item = data.get(position);

        holder.tvTitle.setText(item.title);

        if (holder.tvSubtitle != null) {
            holder.tvSubtitle.setText(item.subtitle != null ? item.subtitle : "");
        }

        holder.icon.setImageResource(item.iconRes);

        int accent = ContextCompat.getColor(holder.itemView.getContext(), item.accentRes);
        holder.icon.setColorFilter(accent);
        if (holder.iconContainer != null) {
            // Kotak di belakang ikon memakai warna yang sama, dipucatkan, jadi
            // setiap modul punya satu identitas warna dan bukan dua.
            holder.iconContainer.setCardBackgroundColor(
                    ColorUtils.setAlphaComponent(accent, ICON_BACKDROP_ALPHA));
        }

        holder.itemView.setOnClickListener(v -> listener.onClick(item));
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    /** Sekitar 17% - terbaca jelas berwarna tanpa melawan ikon di atasnya. */
    private static final int ICON_BACKDROP_ALPHA = 44;

    static class VH extends RecyclerView.ViewHolder {
        MaterialCardView iconContainer;
        ImageView icon;
        TextView tvTitle;
        TextView tvSubtitle;

        VH(@NonNull View itemView) {
            super(itemView);
            iconContainer = itemView.findViewById(R.id.iconContainer);
            icon = itemView.findViewById(R.id.imgTileIcon);
            tvTitle = itemView.findViewById(R.id.tvTileTitle);
            tvSubtitle = itemView.findViewById(R.id.tvTileSubtitle);
        }
    }
}
