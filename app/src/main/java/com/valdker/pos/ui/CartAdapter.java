package com.valdker.pos.ui;

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
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.valdker.pos.R;
import com.valdker.pos.cart.CartManager;
import com.valdker.pos.models.CartItem;
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
        void onTypeChanged(@NonNull CartItem item, @NonNull String orderType);
    }

    private final List<CartItem> items = new ArrayList<>();
    private final Listener listener;
    private final NumberFormat usd = NumberFormat.getCurrencyInstance(Locale.US);

    private final String businessType;
    private final boolean enableDineIn;
    private final boolean enableTakeaway;
    private final boolean enableDelivery;

    public CartAdapter(
            @NonNull Listener listener,
            @NonNull String businessType,
            boolean enableDineIn,
            boolean enableTakeaway,
            boolean enableDelivery
    ) {
        this.listener = listener;
        this.businessType = businessType != null ? businessType.trim().toLowerCase(Locale.US) : "retail";
        this.enableDineIn = enableDineIn;
        this.enableTakeaway = enableTakeaway;
        this.enableDelivery = enableDelivery;
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

        h.btnPlus.setOnClickListener(v -> {
            if (listener != null) listener.onIncrease(it);
        });

        h.btnMinus.setOnClickListener(v -> {
            if (listener != null) listener.onDecrease(it);
        });

        if (h.btnRemove != null) {
            h.btnRemove.setOnClickListener(v -> {
                if (listener != null) listener.onRemove(it);
            });
        }

        applyBusinessUi(h, it);
    }

    private void applyBusinessUi(@NonNull VH h, @NonNull CartItem it) {
        boolean isRestaurant = isRestaurantBusiness();

        if (!isRestaurant) {
            if (h.scrollItemType != null) {
                h.scrollItemType.setVisibility(View.GONE);
            }
            it.orderType = CartManager.TYPE_GENERAL;
            return;
        }

        if (h.scrollItemType != null) {
            h.scrollItemType.setVisibility(View.VISIBLE);
        }

        if (h.chipItemDineIn != null) {
            h.chipItemDineIn.setVisibility(enableDineIn ? View.VISIBLE : View.GONE);
        }
        if (h.chipItemTakeOut != null) {
            h.chipItemTakeOut.setVisibility(enableTakeaway ? View.VISIBLE : View.GONE);
        }
        if (h.chipItemDelivery != null) {
            h.chipItemDelivery.setVisibility(enableDelivery ? View.VISIBLE : View.GONE);
        }

        bindOrderTypeChips(h, it);
    }

    private void bindOrderTypeChips(@NonNull VH h, @NonNull CartItem it) {
        if (h.chipGroupItemType == null) return;

        String type = normalizeTypeOrEmpty(it.orderType);

        h.chipGroupItemType.setOnCheckedChangeListener(null);

        if (!isTypeAllowed(type)) {
            type = getDefaultType();
            it.orderType = type;
        }

        h.chipGroupItemType.clearCheck();

        if (CartManager.TYPE_DINE_IN.equals(type) && h.chipItemDineIn != null && h.chipItemDineIn.getVisibility() == View.VISIBLE) {
            h.chipItemDineIn.setChecked(true);
        } else if (CartManager.TYPE_TAKE_OUT.equals(type) && h.chipItemTakeOut != null && h.chipItemTakeOut.getVisibility() == View.VISIBLE) {
            h.chipItemTakeOut.setChecked(true);
        } else if (CartManager.TYPE_DELIVERY.equals(type) && h.chipItemDelivery != null && h.chipItemDelivery.getVisibility() == View.VISIBLE) {
            h.chipItemDelivery.setChecked(true);
        }

        if (h.chipItemDineIn != null) applyChipStyle(h.chipItemDineIn, h.chipItemDineIn.isChecked());
        if (h.chipItemTakeOut != null) applyChipStyle(h.chipItemTakeOut, h.chipItemTakeOut.isChecked());
        if (h.chipItemDelivery != null) applyChipStyle(h.chipItemDelivery, h.chipItemDelivery.isChecked());

        h.chipGroupItemType.setOnCheckedChangeListener((group, checkedId) -> {
            String newType = getDefaultType();

            if (checkedId == R.id.chipItemDineIn && enableDineIn) {
                newType = CartManager.TYPE_DINE_IN;
            } else if (checkedId == R.id.chipItemTakeOut && enableTakeaway) {
                newType = CartManager.TYPE_TAKE_OUT;
            } else if (checkedId == R.id.chipItemDelivery && enableDelivery) {
                newType = CartManager.TYPE_DELIVERY;
            }

            it.orderType = newType;

            if (h.chipItemDineIn != null) applyChipStyle(h.chipItemDineIn, checkedId == R.id.chipItemDineIn);
            if (h.chipItemTakeOut != null) applyChipStyle(h.chipItemTakeOut, checkedId == R.id.chipItemTakeOut);
            if (h.chipItemDelivery != null) applyChipStyle(h.chipItemDelivery, checkedId == R.id.chipItemDelivery);

            if (listener != null) listener.onTypeChanged(it, newType);
        });
    }

    private boolean isTypeAllowed(@Nullable String type) {
        String t = normalizeTypeOrEmpty(type);

        if (!isRestaurantBusiness()) {
            return CartManager.TYPE_GENERAL.equals(t) || t.isEmpty();
        }

        if (CartManager.TYPE_DINE_IN.equals(t)) return enableDineIn;
        if (CartManager.TYPE_TAKE_OUT.equals(t)) return enableTakeaway;
        if (CartManager.TYPE_DELIVERY.equals(t)) return enableDelivery;
        return false;
    }

    @NonNull
    private String getDefaultType() {
        if (isRestaurantBusiness()) {
            if (enableTakeaway) return CartManager.TYPE_TAKE_OUT;
            if (enableDineIn) return CartManager.TYPE_DINE_IN;
            if (enableDelivery) return CartManager.TYPE_DELIVERY;
            return CartManager.TYPE_TAKE_OUT;
        }
        return CartManager.TYPE_GENERAL;
    }

    private boolean isRestaurantBusiness() {
        return "restaurant".equalsIgnoreCase(businessType);
    }

    private static void applyChipStyle(@NonNull Chip chip, boolean checked) {
        int bgChecked = Color.parseColor("#FDE68A");
        int strokeChecked = Color.parseColor("#FB923C");
        int bgNormal = Color.WHITE;
        int strokeNormal = Color.parseColor("#E5E7EB");

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

    private static String normalizeTypeOrEmpty(String t) {
        if (t == null) return "";
        String v = t.trim().toUpperCase(Locale.US);
        if (v.isEmpty()) return "";
        if (CartManager.TYPE_GENERAL.equals(v)) return CartManager.TYPE_GENERAL;
        if (CartManager.TYPE_DINE_IN.equals(v)) return CartManager.TYPE_DINE_IN;
        if (CartManager.TYPE_TAKE_OUT.equals(v)) return CartManager.TYPE_TAKE_OUT;
        if (CartManager.TYPE_DELIVERY.equals(v)) return CartManager.TYPE_DELIVERY;
        return "";
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

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

        View scrollItemType;
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

            btnRemove = itemView.findViewById(R.id.btnCartRemove);
            scrollItemType = itemView.findViewById(R.id.scrollItemType);
            chipGroupItemType = itemView.findViewById(R.id.chipGroupItemType);
            chipItemDineIn = itemView.findViewById(R.id.chipItemDineIn);
            chipItemTakeOut = itemView.findViewById(R.id.chipItemTakeOut);
            chipItemDelivery = itemView.findViewById(R.id.chipItemDelivery);
        }
    }
}