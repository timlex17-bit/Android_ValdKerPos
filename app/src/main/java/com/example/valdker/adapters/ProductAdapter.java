package com.example.valdker.adapters;

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

    private final List<Product> items;
    private final Listener listener;
    private final NumberFormat usd = NumberFormat.getCurrencyInstance(Locale.US);

    private final boolean useGridPosLayout;
    private final boolean showProductImagesInPos;
    private final String businessType;

    public ProductAdapter(
            @NonNull List<Product> items,
            @NonNull Listener listener,
            boolean useGridPosLayout,
            boolean showProductImagesInPos,
            @NonNull String businessType
    ) {
        this.items = items;
        this.listener = listener;
        this.useGridPosLayout = useGridPosLayout;
        this.showProductImagesInPos = showProductImagesInPos;
        this.businessType = businessType != null
                ? businessType.trim().toLowerCase(Locale.US)
                : "retail";
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutId = useGridPosLayout
                ? R.layout.item_product_grid
                : R.layout.item_product_list;

        View v = LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Product p = items.get(position);

        String name = safeString(readAnyField(p, new String[]{
                "name", "title", "product_name"
        }));
        h.tvName.setText(name.isEmpty() ? "-" : name);

        Object priceObj = readAnyField(p, new String[]{
                "price",
                "selling_price", "sell_price", "sale_price",
                "unit_price",
                "price_usd", "usd_price",
                "harga",
                "final_price", "finalPrice",
                "price_after_discount", "priceAfterDiscount"
        });

        double priceVal = toDouble(priceObj);
        h.tvPrice.setText(priceVal > 0 ? usd.format(priceVal) : "-");

        Object stockObj = readAnyField(p, new String[]{
                "stock", "qty", "quantity", "current_stock"
        });
        int stockVal = toInt(stockObj);
        h.tvStock.setText("Stock: " + stockVal);

        bindImage(h, p);
        applyBusinessUi(h);

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(p);
        });

        h.btnAdd.setOnClickListener(v -> {
            if (listener != null) listener.onAdd(p);
        });
    }

    private void bindImage(@NonNull VH h, @NonNull Product p) {
        if (!showProductImagesInPos) {
            h.img.setVisibility(View.GONE);
            return;
        }

        h.img.setVisibility(View.VISIBLE);

        Object imgObj = readAnyField(p, new String[]{
                "image", "img", "photo", "thumbnail", "thumb",
                "image_url", "imageUrl", "product_image", "productImage",
                "picture", "pic", "url"
        });

        String imgUrl = safeString(imgObj);

        if (imgUrl.isEmpty()) {
            h.img.setImageResource(R.drawable.bg_image_placeholder);
            return;
        }

        String finalUrl = forceHttpsIfCloudinary(imgUrl);

        Glide.with(h.itemView.getContext())
                .load(finalUrl)
                .placeholder(R.drawable.bg_image_placeholder)
                .error(R.drawable.bg_image_placeholder)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .into(h.img);
    }

    private void applyBusinessUi(@NonNull VH h) {
        h.btnAdd.setText("Add");

        if ("restaurant".equals(businessType)) {
            h.tvStock.setVisibility(View.VISIBLE);
        } else if ("retail".equals(businessType)) {
            h.tvStock.setVisibility(View.VISIBLE);
        } else if ("workshop".equals(businessType)) {
            h.tvStock.setVisibility(View.VISIBLE);
        } else {
            h.tvStock.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    private Object readAnyField(Object obj, String[] candidates) {
        if (obj == null) return null;

        Class<?> c = obj.getClass();

        for (String key : candidates) {
            try {
                Field f = c.getField(key);
                f.setAccessible(true);
                Object v = f.get(obj);
                if (v != null) return v;
            } catch (Throwable ignored) {
            }

            try {
                Field f = c.getDeclaredField(key);
                f.setAccessible(true);
                Object v = f.get(obj);
                if (v != null) return v;
            } catch (Throwable ignored) {
            }
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