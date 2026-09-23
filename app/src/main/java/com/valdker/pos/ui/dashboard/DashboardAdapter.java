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

    private final List<DashboardItem> data = new java.util.ArrayList<>();
    private final Listener listener;

    public DashboardAdapter(@NonNull List<DashboardItem> data, @NonNull Listener listener) {
        this.data.addAll(data);
        this.listener = listener;
    }

    /**
     * Memperbarui daftar modul tanpa mengganti adapternya.
     *
     * <p>Layar Home menyusun ulang daftarnya di setiap {@code onResume()},
     * dan dulu itu berarti {@code setAdapter()} dengan adapter baru - yang
     * selalu mengembalikan gulir ke baris teratas. Akibatnya modul bengkel,
     * yang duduk di bagian bawah daftar, mengharuskan pengguna menggulir dari
     * awal setiap kali ia kembali dari salah satunya.
     *
     * @return true kalau daftarnya benar-benar berubah.
     */
    public boolean submit(@NonNull List<DashboardItem> next) {
        if (sameContent(next)) return false;
        data.clear();
        data.addAll(next);
        notifyDataSetChanged();
        return true;
    }

    private boolean sameContent(@NonNull List<DashboardItem> next) {
        if (data.size() != next.size()) return false;
        for (int i = 0; i < data.size(); i++) {
            DashboardItem a = data.get(i);
            DashboardItem b = next.get(i);
            if (a.id != b.id
                    || !a.title.equals(b.title)
                    || !a.subtitle.equals(b.subtitle)
                    || a.iconRes != b.iconRes
                    || a.accentRes != b.accentRes) {
                return false;
            }
        }
        return true;
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
