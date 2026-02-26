package com.example.valdker.adapters;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.example.valdker.R;
import com.example.valdker.models.Product;

import java.lang.reflect.Field;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.VH> {

    public interface Listener {
        void onAdd(Product p);
        void onClick(Product p);
    }

    private static final String TAG = "PRODUCT_ADAPTER";

    private final List<Product> items;
    private final Listener listener;
    private final NumberFormat usd = NumberFormat.getCurrencyInstance(Locale.US);

    public ProductAdapter(List<Product> items, Listener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_product, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Product p = items.get(position);

        // ===== NAME =====
        String name = safeString(readAnyField(p, new String[]{"name", "title", "product_name"}));
        h.tvName.setText(!name.isEmpty() ? name : "-");

        // ===== PRICE =====
        String[] PRICE_FIELDS = new String[]{
                "price",
                "selling_price", "sell_price", "sale_price",
                "unit_price",
                "price_usd", "usd_price",
                "harga",
                "amount", "total",
                "final_price", "finalPrice",
                "price_after_discount", "priceAfterDiscount",
                "cost_price", "costPrice"
        };

        Object priceObj = readAnyField(p, PRICE_FIELDS);

        // Log only first item to identify which price field matches the model
        if (position == 0) {
            Log.i(TAG, "==== DEBUG PRICE FIELDS (first item) ====");
            for (String f : PRICE_FIELDS) {
                Object v = readAnyField(p, new String[]{f});
                if (v != null) Log.i(TAG, "price field hit: " + f + " = " + v);
            }
        }

        double priceVal = toDouble(priceObj);
        h.tvPrice.setText(priceVal <= 0.0 ? "-" : usd.format(priceVal));

        // ===== STOCK =====
        Object stockObj = readAnyField(p, new String[]{"stock", "qty", "quantity", "current_stock"});
        int stockVal = toInt(stockObj);
        h.tvStock.setText("Stock: " + stockVal);

        // ===== IMAGE =====
        Object imgObj = readAnyField(p, new String[]{
                "image", "img", "photo", "thumbnail", "thumb",
                "image_url", "imageUrl", "product_image", "productImage",
                "picture", "pic", "url"
        });
        String imgUrl = safeString(imgObj);

        if (imgUrl.isEmpty()) {
            h.img.setImageResource(android.R.color.darker_gray);
        } else {
            String finalUrl = forceHttpsIfCloudinary(imgUrl);
            Glide.with(h.itemView.getContext())
                    .load(finalUrl)
                    .placeholder(android.R.color.darker_gray)
                    .error(android.R.color.darker_gray)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .into(h.img);
        }

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(p);
        });

        h.btnAdd.setOnClickListener(v -> {
            if (listener != null) listener.onAdd(p);
        });
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    // =========================================================
    // Reflection helpers (safe even if field does not exist)
    // =========================================================

    private Object readAnyField(Object obj, String[] candidates) {
        if (obj == null) return null;

        Class<?> c = obj.getClass();

        for (String key : candidates) {
            try {
                Field f = c.getField(key);
                f.setAccessible(true);
                Object v = f.get(obj);
                if (v != null) return v;
            } catch (Throwable ignored) {}

            try {
                Field f = c.getDeclaredField(key);
                f.setAccessible(true);
                Object v = f.get(obj);
                if (v != null) return v;
            } catch (Throwable ignored) {}
        }

        return null;
    }

    private String safeString(Object v) {
        if (v == null) return "";
        String s = String.valueOf(v).trim();
        if ("null".equalsIgnoreCase(s)) return "";
        return s;
    }

    private double toDouble(Object v) {
        if (v == null) return 0.0;
        if (v instanceof Number) return ((Number) v).doubleValue();

        try {
            String s = String.valueOf(v).trim();
            s = s.replace("$", "")
                    .replace("USD", "")
                    .replace("usd", "")
                    .replace(",", "")
                    .trim();

            if (s.isEmpty()) return 0.0;
            return Double.parseDouble(s);
        } catch (Exception e) {
            return 0.0;
        }
    }

    private int toInt(Object v) {
        if (v == null) return 0;
        if (v instanceof Number) return ((Number) v).intValue();

        try {
            String s = String.valueOf(v).trim().replace(",", "");
            if (s.isEmpty()) return 0;
            return Integer.parseInt(s);
        } catch (Exception e) {
            return 0;
        }
    }

    private String forceHttpsIfCloudinary(String url) {
        if (url == null) return "";
        String u = url.trim();
        if (u.startsWith("http://res.cloudinary.com/")) {
            return u.replace("http://", "https://");
        }
        return u;
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView img;
        TextView tvName, tvPrice, tvStock;
        Button btnAdd;

        VH(@NonNull View itemView) {
            super(itemView);
            img = itemView.findViewById(R.id.imgProduct);
            tvName = itemView.findViewById(R.id.tvName);
            tvPrice = itemView.findViewById(R.id.tvPrice);
            tvStock = itemView.findViewById(R.id.tvStock);
            btnAdd = itemView.findViewById(R.id.btnAdd);
        }
    }
}
