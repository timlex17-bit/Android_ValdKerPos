package com.valdker.pos.ui.retail;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.valdker.pos.R;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RetailProductAdapter extends RecyclerView.Adapter<RetailProductAdapter.ProductVH> {

    public interface Listener {
        void onProductClick(@NonNull RetailProductItem item);
        void onAddToCartClick(@NonNull RetailProductItem item);
        int getQuantity(@NonNull RetailProductItem item);
        void onIncreaseQty(@NonNull RetailProductItem item);
        void onDecreaseQty(@NonNull RetailProductItem item);
    }

    private final List<RetailProductItem> items = new ArrayList<>();
    private final NumberFormat moneyFormat = NumberFormat.getCurrencyInstance(Locale.US);

    @Nullable
    private Listener listener;

    private boolean showImages = true;

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    public void setShowImages(boolean showImages) {
        this.showImages = showImages;
        notifyDataSetChanged();
    }

    public void setData(@Nullable List<RetailProductItem> data) {
        items.clear();
        if (data != null) {
            items.addAll(data);
        }
        notifyDataSetChanged();
    }

    @NonNull
    public List<RetailProductItem> getItems() {
        return new ArrayList<>(items);
    }

    @Nullable
    public RetailProductItem getItemAt(int position) {
        if (position < 0 || position >= items.size()) {
            return null;
        }
        return items.get(position);
    }

    @NonNull
    @Override
    public ProductVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_product_list, parent, false);
        return new ProductVH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProductVH holder, int position) {
        RetailProductItem item = items.get(position);
        holder.bind(item, listener, showImages, moneyFormat);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ProductVH extends RecyclerView.ViewHolder {
        private final ImageView imgProduct;
        private final TextView tvName;
        private final TextView tvPrice;
        private final TextView tvSku;
        private final TextView tvStock;
        private final TextView tvQty;
        private final MaterialButton btnMinus;
        private final MaterialButton btnPlus;
        private final View btnAdd;

        ProductVH(@NonNull View itemView) {
            super(itemView);
            imgProduct = itemView.findViewById(R.id.imgProduct);
            tvName = itemView.findViewById(R.id.tvName);
            tvPrice = itemView.findViewById(R.id.tvPrice);
            tvSku = itemView.findViewById(R.id.tvSku);
            tvStock = itemView.findViewById(R.id.tvStock);
            btnMinus = itemView.findViewById(R.id.btnMinus);
            btnPlus = itemView.findViewById(R.id.btnPlus);
            tvQty = itemView.findViewById(R.id.tvQty);
            btnAdd = itemView.findViewById(R.id.btnAdd);
        }

        void bind(@NonNull RetailProductItem item,
                  @Nullable Listener listener,
                  boolean showImages,
                  @NonNull NumberFormat moneyFormat) {

            if (tvName != null) tvName.setText(notEmpty(item.name, "-"));
            if (tvPrice != null) tvPrice.setText(moneyFormat.format(item.price));

            String sku = notEmpty(item.sku, "-");
            if (tvSku != null) tvSku.setText("SKU: " + sku);

            if (tvStock != null) tvStock.setText("Stock: " + formatStock(item.stock));

            int qty = listener != null ? listener.getQuantity(item) : 1;
            if (tvQty != null) tvQty.setText(String.valueOf(Math.max(1, qty)));

            if (imgProduct != null) {
                if (showImages) {
                    imgProduct.setVisibility(View.VISIBLE);

                    if (item.imageUrl != null && !item.imageUrl.trim().isEmpty()) {
                        Glide.with(itemView.getContext())
                                .load(item.imageUrl)
                                .centerCrop()
                                .placeholder(R.drawable.bg_logo_circle)
                                .error(R.drawable.bg_logo_circle)
                                .into(imgProduct);
                    } else {
                        imgProduct.setImageResource(R.drawable.bg_logo_circle);
                    }
                } else {
                    imgProduct.setVisibility(View.GONE);
                }
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onProductClick(item);
                }
            });

            if (btnAdd != null) {
                btnAdd.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onAddToCartClick(item);
                    }
                });
            }

            if (btnPlus != null) {
                btnPlus.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onIncreaseQty(item);
                    }
                });
            }

            if (btnMinus != null) {
                btnMinus.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onDecreaseQty(item);
                    }
                });
            }
        }

        @NonNull
        private static String notEmpty(@Nullable String value, @NonNull String fallback) {
            if (value == null) return fallback;
            String clean = value.trim();
            return clean.isEmpty() ? fallback : clean;
        }

        @NonNull
        private static String formatStock(double stock) {
            if (Math.floor(stock) == stock) {
                return String.valueOf((long) stock);
            }
            return String.valueOf(stock);
        }
    }
}
