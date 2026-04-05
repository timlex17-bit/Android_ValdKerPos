package com.example.valdker.ui.stockmovements;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.valdker.R;
import com.example.valdker.models.StockMovement;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class StockMovementsAdapter extends RecyclerView.Adapter<StockMovementsAdapter.VH> {

    public interface Listener {
        void onClick(StockMovement item);
    }

    private final List<StockMovement> list;
    private final Listener listener;

    public StockMovementsAdapter(List<StockMovement> list, Listener listener) {
        this.list = list;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_stock_movement, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        StockMovement it = list.get(position);
        Context context = h.itemView.getContext();

        h.tvName.setText(safeProductName(context, it.product_name, it.product));

        String sku = safe(context, it.product_sku);
        String code = safe(context, it.product_code);
        h.tvMeta.setText(context.getString(R.string.label_sku_and_code, sku, code));

        String movementType = mapMovementType(context, it.movement_type);
        h.tvType.setText(context.getString(R.string.label_type_value, movementType));

        h.tvDate.setText(formatIso(context, it.created_at));

        String sign = it.quantity_delta > 0 ? "+" : "";
        String qtyValue = sign + it.quantity_delta;
        h.tvQty.setText(context.getString(R.string.label_delta_short_value, qtyValue));

        h.tvStock.setText(context.getString(
                R.string.label_stock_range,
                it.before_stock,
                it.after_stock
        ));

        h.tvRef.setText(context.getString(
                R.string.label_ref_value,
                safeRef(context, it.refLabel())
        ));

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(it);
        });
    }

    @Override
    public int getItemCount() {
        return list == null ? 0 : list.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvMeta, tvType, tvDate, tvQty, tvStock, tvRef;

        VH(@NonNull View v) {
            super(v);
            tvName = v.findViewById(R.id.tvName);
            tvMeta = v.findViewById(R.id.tvMeta);
            tvType = v.findViewById(R.id.tvType);
            tvDate = v.findViewById(R.id.tvDate);
            tvQty = v.findViewById(R.id.tvQty);
            tvStock = v.findViewById(R.id.tvStock);
            tvRef = v.findViewById(R.id.tvRef);
        }
    }

    private static String safe(Context context, String s) {
        return (s == null || s.trim().isEmpty())
                ? context.getString(R.string.label_default_dash)
                : s.trim();
    }

    private static String safeProductName(Context context, String name, int productId) {
        if (name == null || name.trim().isEmpty()) {
            return context.getString(R.string.label_product_with_id, productId);
        }
        return name.trim();
    }

    private static String safeRef(Context context, String ref) {
        return (ref == null || ref.trim().isEmpty())
                ? context.getString(R.string.label_default_dash)
                : ref.trim();
    }

    private static String formatIso(Context context, String iso) {
        if (iso == null || iso.trim().isEmpty()) {
            return context.getString(R.string.label_default_dash);
        }

        String[] inputPatterns = new String[]{
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"
        };

        for (String pattern : inputPatterns) {
            try {
                SimpleDateFormat inputFormat = new SimpleDateFormat(pattern, Locale.getDefault());
                inputFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

                Date date = inputFormat.parse(iso);
                if (date != null) {
                    SimpleDateFormat outputFormat =
                            new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
                    return outputFormat.format(date);
                }
            } catch (Exception ignored) {
            }
        }

        return iso;
    }

    private static String mapMovementType(Context context, String rawType) {
        if (rawType == null || rawType.trim().isEmpty()) {
            return context.getString(R.string.label_default_dash);
        }

        String normalized = rawType.trim().toUpperCase(Locale.US);

        switch (normalized) {
            case "SALE":
                return context.getString(R.string.movement_type_sale);
            case "PURCHASE":
                return context.getString(R.string.movement_type_purchase);
            case "ADJUSTMENT":
                return context.getString(R.string.movement_type_adjustment);
            case "RETURN":
                return context.getString(R.string.movement_type_return);
            case "OPENING":
                return context.getString(R.string.movement_type_opening);
            case "STOCK_IN":
                return context.getString(R.string.movement_type_stock_in);
            case "STOCK_OUT":
                return context.getString(R.string.movement_type_stock_out);
            case "COUNT":
                return context.getString(R.string.movement_type_count);
            case "TRANSFER":
                return context.getString(R.string.movement_type_transfer);
            default:
                return rawType.trim();
        }
    }
}