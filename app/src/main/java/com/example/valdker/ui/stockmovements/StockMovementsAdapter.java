package com.example.valdker.ui.stockmovements;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.valdker.R;
import com.example.valdker.models.StockMovement;

import java.util.List;

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

        h.tvName.setText(safe(it.product_name, "Product #" + it.product));
        h.tvMeta.setText("SKU: " + safe(it.product_sku, "-") + " • Code: " + safe(it.product_code, "-"));
        h.tvType.setText("Type: " + safe(it.movement_type, "-"));
        h.tvDate.setText(formatIso(it.created_at));

        String sign = it.quantity_delta > 0 ? "+" : "";
        h.tvQty.setText("Δ " + sign + it.quantity_delta);
        h.tvStock.setText("Stock: " + it.before_stock + " → " + it.after_stock);
        h.tvRef.setText("Ref: " + it.refLabel());

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

    private static String safe(String s, String fallback) {
        return (s == null || s.trim().isEmpty()) ? fallback : s.trim();
    }

    private static String formatIso(String iso) {
        try {
            String s = iso.replace("Z", "");
            if (s.length() >= 16) {
                String d = s.substring(0, 10);
                String t = s.substring(11, 16);
                String[] p = d.split("-");
                if (p.length == 3) return p[2] + "/" + p[1] + "/" + p[0] + " " + t;
                return d + " " + t;
            }
        } catch (Exception ignored) {}
        return iso;
    }
}