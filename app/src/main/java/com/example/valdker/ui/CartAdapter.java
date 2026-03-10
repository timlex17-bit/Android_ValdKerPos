package com.example.valdker.ui;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.example.valdker.R;
import com.example.valdker.cart.CartManager;
import com.example.valdker.models.CartItem;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CartAdapter extends RecyclerView.Adapter<CartAdapter.VH> {

    public interface Listener {
        void onIncrease(@NonNull CartItem item);
        void onDecrease(@NonNull CartItem item);
        void onRemove(@NonNull CartItem item);
        void onTypeChanged(@NonNull CartItem item, @NonNull String orderType); // can be "" (UNSET)
    }

    private final List<CartItem> items = new ArrayList<>();
    private final Listener listener;
    private final NumberFormat usd = NumberFormat.getCurrencyInstance(Locale.US);

    public CartAdapter(@NonNull Listener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    public void submit(@NonNull List<CartItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        CartItem it = items.get(position);
        return (it != null) ? it.productId : position;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_cart, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        CartItem it = items.get(position);
        if (it == null) return;

        h.tvName.setText(safe(it.name, "-"));
        h.tvPrice.setText(usd.format(Math.max(0.0, it.price)));
        h.tvQty.setText(String.valueOf(Math.max(0, it.qty)));

        String url = safe(it.imageUrl, "");
        if (!url.isEmpty()) url = forceHttpsIfCloudinary(url);

        if (url.isEmpty()) {
            h.imgThumb.setImageResource(android.R.color.darker_gray);
        } else {
            Glide.with(h.itemView.getContext())
                    .load(url)
                    .placeholder(android.R.color.darker_gray)
                    .error(android.R.color.darker_gray)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .into(h.imgThumb);
        }

        h.btnPlus.setOnClickListener(v -> { if (listener != null) listener.onIncrease(it); });
        h.btnMinus.setOnClickListener(v -> { if (listener != null) listener.onDecrease(it); });

        if (h.btnRemove != null) {
            h.btnRemove.setOnClickListener(v -> { if (listener != null) listener.onRemove(it); });
        }

        bindOrderTypeChips(h, it);
    }

    private void bindOrderTypeChips(@NonNull VH h, @NonNull CartItem it) {

        String type = normalizeTypeOrEmpty(it.orderType);

        // 1) detach listener to avoid recycle loops
        h.chipGroupItemType.setOnCheckedChangeListener(null);

        // 2) apply checked state based on model
        if (type.isEmpty()) {
            // ✅ UNSET -> no chip selected (requires selectionRequired=false in XML)
            h.chipGroupItemType.clearCheck();
            h.chipItemDineIn.setChecked(false);
            h.chipItemTakeOut.setChecked(false);
            h.chipItemDelivery.setChecked(false);
        } else if (CartManager.TYPE_DINE_IN.equals(type)) {
            h.chipItemDineIn.setChecked(true);
        } else if (CartManager.TYPE_TAKE_OUT.equals(type)) {
            h.chipItemTakeOut.setChecked(true);
        } else if (CartManager.TYPE_DELIVERY.equals(type)) {
            h.chipItemDelivery.setChecked(true);
        } else {
            h.chipGroupItemType.clearCheck();
        }

        // 3) style
        applyChipStyle(h.chipItemDineIn, h.chipItemDineIn.isChecked());
        applyChipStyle(h.chipItemTakeOut, h.chipItemTakeOut.isChecked());
        applyChipStyle(h.chipItemDelivery, h.chipItemDelivery.isChecked());

        // 4) attach listener back
        h.chipGroupItemType.setOnCheckedChangeListener((group, checkedId) -> {

            String newType = "";
            if (checkedId == R.id.chipItemDineIn) newType = CartManager.TYPE_DINE_IN;
            else if (checkedId == R.id.chipItemTakeOut) newType = CartManager.TYPE_TAKE_OUT;
            else if (checkedId == R.id.chipItemDelivery) newType = CartManager.TYPE_DELIVERY;
            else newType = ""; // ✅ NO_ID -> UNSET (only possible if selectionRequired=false)

            // update model immediately
            it.orderType = newType;

            // style
            applyChipStyle(h.chipItemDineIn, checkedId == R.id.chipItemDineIn);
            applyChipStyle(h.chipItemTakeOut, checkedId == R.id.chipItemTakeOut);
            applyChipStyle(h.chipItemDelivery, checkedId == R.id.chipItemDelivery);

            if (listener != null) listener.onTypeChanged(it, newType);
        });
    }

    private static void applyChipStyle(@NonNull Chip chip, boolean checked) {
        int bgChecked = Color.parseColor("#FDE68A");     // yellow-200
        int strokeChecked = Color.parseColor("#FB923C"); // orange-400
        int bgNormal = Color.WHITE;
        int strokeNormal = Color.parseColor("#E5E7EB");  // gray-200

        chip.setChipBackgroundColor(ColorStateList.valueOf(checked ? bgChecked : bgNormal));
        chip.setChipStrokeColor(ColorStateList.valueOf(checked ? strokeChecked : strokeNormal));
        chip.setChipStrokeWidth(dpToPx(chip, checked ? 2f : 1f));
        chip.setTextColor(Color.parseColor("#111827"));
    }

    private static float dpToPx(@NonNull View v, float dp) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, v.getResources().getDisplayMetrics()
        );
    }

    /**
     * Normalize with UNSET:
     * null/empty -> ""
     */
    private static String normalizeTypeOrEmpty(String t) {
        if (t == null) return "";
        String v = t.trim().toUpperCase(Locale.US);
        if (v.isEmpty()) return "";
        if (CartManager.TYPE_DINE_IN.equals(v)) return CartManager.TYPE_DINE_IN;
        if (CartManager.TYPE_TAKE_OUT.equals(v)) return CartManager.TYPE_TAKE_OUT;
        if (CartManager.TYPE_DELIVERY.equals(v)) return CartManager.TYPE_DELIVERY;
        return "";
    }

    @Override
    public int getItemCount() { return items.size(); }

    private static String safe(String s, String fallback) {
        if (s == null) return fallback;
        String t = s.trim();
        return t.isEmpty() ? fallback : t;
    }

    private static String forceHttpsIfCloudinary(String url) {
        String u = url.trim();
        if (u.startsWith("http://res.cloudinary.com/")) return u.replace("http://", "https://");
        return u;
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView imgThumb;
        TextView tvName, tvPrice, tvQty;
        MaterialButton btnMinus, btnPlus;
        ImageButton btnRemove;

        ChipGroup chipGroupItemType;
        Chip chipItemDineIn, chipItemTakeOut, chipItemDelivery;

        VH(@NonNull View itemView) {
            super(itemView);
            imgThumb = itemView.findViewById(R.id.imgCartThumb);
            tvName = itemView.findViewById(R.id.tvCartName);
            tvPrice = itemView.findViewById(R.id.tvCartPrice);
            tvQty = itemView.findViewById(R.id.tvCartQty);
            btnMinus = itemView.findViewById(R.id.btnCartMinus);
            btnPlus = itemView.findViewById(R.id.btnCartPlus);

            // optional remove button if you add it later
            // btnRemove = itemView.findViewById(R.id.btnCartRemove);

            chipGroupItemType = itemView.findViewById(R.id.chipGroupItemType);
            chipItemDineIn = itemView.findViewById(R.id.chipItemDineIn);
            chipItemTakeOut = itemView.findViewById(R.id.chipItemTakeOut);
            chipItemDelivery = itemView.findViewById(R.id.chipItemDelivery);
        }
    }
}
