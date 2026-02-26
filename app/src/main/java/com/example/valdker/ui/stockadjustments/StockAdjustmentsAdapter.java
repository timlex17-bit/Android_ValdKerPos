package com.example.valdker.ui.stockadjustments;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.valdker.R;
import com.example.valdker.models.StockAdjustment;

import java.util.List;

public class StockAdjustmentsAdapter extends RecyclerView.Adapter<StockAdjustmentsAdapter.VH> {

    public interface Listener {
        void onClick(StockAdjustment item);
    }

    private final List<StockAdjustment> list;
    private final Listener listener;

    public StockAdjustmentsAdapter(List<StockAdjustment> list, Listener listener) {
        this.list = list;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_stock_adjustment, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        StockAdjustment it = list.get(position);

        h.tvTitle.setText("Product #" + it.product);
        h.tvReason.setText("Reason: " + safe(it.reason));
        h.tvDate.setText(formatIso(it.adjusted_at));

        int d = it.diff();
        String sign = d > 0 ? "+" : "";
        h.tvDiff.setText("Diff: " + sign + d);
        h.tvFromTo.setText("Stock: " + it.old_stock + " → " + it.new_stock);
        h.tvBy.setText("By: #" + it.adjusted_by);

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(it);
        });
    }

    @Override
    public int getItemCount() {
        return list == null ? 0 : list.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvTitle, tvReason, tvDate, tvDiff, tvFromTo, tvBy;
        VH(@NonNull View v) {
            super(v);
            tvTitle = v.findViewById(R.id.tvTitle);
            tvReason = v.findViewById(R.id.tvReason);
            tvDate = v.findViewById(R.id.tvDate);
            tvDiff = v.findViewById(R.id.tvDiff);
            tvFromTo = v.findViewById(R.id.tvFromTo);
            tvBy = v.findViewById(R.id.tvBy);
        }
    }

    private static String safe(String s) {
        return (s == null || s.trim().isEmpty()) ? "-" : s.trim();
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