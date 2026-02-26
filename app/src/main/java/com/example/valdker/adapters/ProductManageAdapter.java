package com.example.valdker.adapters;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.example.valdker.R;
import com.example.valdker.models.Product;
import com.google.android.material.button.MaterialButton;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class ProductManageAdapter extends RecyclerView.Adapter<ProductManageAdapter.VH> {

    private static final String TAG = "ProductManageAdapter";

    public interface Listener {
        void onEdit(@NonNull Product p);
        void onDelete(@NonNull Product p);
    }

    private final List<Product> data;
    private final Listener listener;

    private final NumberFormat usd = NumberFormat.getCurrencyInstance(Locale.US);

    public ProductManageAdapter(@NonNull List<Product> data, @NonNull Listener listener) {
        this.data = data;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_product_manage, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Product p = data.get(position);

        h.tvName.setText(p.name);
        h.tvMeta.setText("Stock: " + p.stock + " • " + usd.format(p.price));

        // ✅ FIX: benar-benar load image dari URL
        String url = p.imageUrl;
        if (url != null) {
            url = url.trim();
            if (url.startsWith("http://")) {
                url = url.replace("http://", "https://");
            }
        }

        if (url == null || url.isEmpty()) {
            // fallback kalau url kosong
            h.img.setImageResource(R.drawable.ic_launcher_background);
        } else {
            Glide.with(h.img.getContext())
                    .load(url)
                    .placeholder(R.drawable.ic_launcher_background)
                    .error(R.drawable.ic_launcher_background)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .centerCrop()
                    .into(h.img);
        }

        // ✅ Debug ringan (hapus kalau sudah stabil)
        if (position == 0) {
            Log.d(TAG, "First imageUrl=" + p.imageUrl + " -> urlUsed=" + url);
        }

        h.btnEdit.setOnClickListener(v -> listener.onEdit(p));
        h.btnDelete.setOnClickListener(v -> listener.onDelete(p));
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView img;
        TextView tvName, tvMeta;
        MaterialButton btnEdit, btnDelete;

        VH(@NonNull View itemView) {
            super(itemView);
            img = itemView.findViewById(R.id.imgProduct);
            tvName = itemView.findViewById(R.id.tvName);
            tvMeta = itemView.findViewById(R.id.tvMeta);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}
