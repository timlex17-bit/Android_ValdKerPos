package com.example.valdker.ui.stockadjustments;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.valdker.R;
import com.example.valdker.models.StockAdjustment;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

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

        String productName = safe(it.product_name);
        String byName = safe(it.adjusted_by_name);

        h.tvTitle.setText(!"-".equals(productName) ? productName : "Product #" + it.product);
        h.tvReason.setText("Reason: " + safe(it.reason));
        h.tvDate.setText(formatIso(it.adjusted_at));

        int d = it.diff();
        String sign = d > 0 ? "+" : "";
        h.tvDiff.setText("Diff: " + sign + d);

        if (d > 0) {
            h.tvDiff.setTextColor(Color.parseColor("#16A34A"));
        } else if (d < 0) {
            h.tvDiff.setTextColor(Color.parseColor("#DC2626"));
        } else {
            h.tvDiff.setTextColor(Color.parseColor("#374151"));
        }

        h.tvFromTo.setText("Stock: " + it.old_stock + " → " + it.new_stock);
        h.tvBy.setText("By: " + (!"-".equals(byName) ? byName : "User #" + it.adjusted_by));

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
        if (iso == null || iso.trim().isEmpty()) return "-";

        try {
            String clean = iso.trim();
            clean = clean.replaceFirst("\\.\\d+(?=[+-]\\d{2}:\\d{2}$)", "");

            SimpleDateFormat in;
            if (clean.endsWith("Z")) {
                in = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            } else {
                in = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US);
            }

            in.setLenient(false);
            Date d = in.parse(clean);

            SimpleDateFormat out = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
            return d != null ? out.format(d) : iso;

        } catch (Exception e) {
            return iso;
        }
    }
}