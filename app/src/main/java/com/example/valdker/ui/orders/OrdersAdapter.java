package com.example.valdker.ui.orders;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.valdker.R;
import com.example.valdker.models.Order;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OrdersAdapter extends RecyclerView.Adapter<OrdersAdapter.VH> {

    public interface OnOrderClick {
        void onClick(Order order);
    }

    private final List<Order> data = new ArrayList<>();
    private final OnOrderClick onClick;
    private final NumberFormat usd = NumberFormat.getCurrencyInstance(Locale.US);

    public OrdersAdapter(List<Order> initial, OnOrderClick onClick) {
        if (initial != null) data.addAll(initial);
        this.onClick = onClick;
    }

    public void setData(List<Order> orders) {
        data.clear();
        if (orders != null) data.addAll(orders);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_order, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Order o = data.get(position);

        String inv = o.getInvoiceNumber();
        if (inv == null || inv.trim().isEmpty()) {
            h.tvOrderId.setText("Order #" + o.getId());
        } else {
            h.tvOrderId.setText("Invoice: " + inv);
        }
        h.tvPayment.setText(safeCap(o.getPaymentMethod()));
        h.tvTotal.setText(usd.format(o.getTotal()));
        h.tvItemsCount.setText(o.getItemsCount() + " items");
        h.tvStatus.setText(o.isPaid() ? "PAID" : "UNPAID");

        // Simple date formatting: "2026-02-13T16:31:06Z" -> "2026-02-13 16:31"
        h.tvDate.setText(formatIso(o.getCreatedAtIso()));

        h.itemView.setOnClickListener(v -> {
            if (onClick != null) onClick.onClick(o);
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvOrderId, tvDate, tvPayment, tvTotal, tvStatus, tvItemsCount;

        VH(@NonNull View itemView) {
            super(itemView);
            tvOrderId = itemView.findViewById(R.id.tvOrderId);
            tvDate = itemView.findViewById(R.id.tvOrderDate);
            tvPayment = itemView.findViewById(R.id.tvOrderPayment);
            tvTotal = itemView.findViewById(R.id.tvOrderTotal);
            tvStatus = itemView.findViewById(R.id.tvOrderStatus);
            tvItemsCount = itemView.findViewById(R.id.tvOrderItemsCount);
        }
    }

    private String formatIso(String iso) {
        if (iso == null) return "-";
        // Most common: "YYYY-MM-DDTHH:MM:SS....Z"
        try {
            if (iso.length() >= 16) {
                String date = iso.substring(0, 10);
                String time = iso.substring(11, 16);
                return date + " " + time;
            }
            return iso;
        } catch (Exception e) {
            return iso;
        }
    }

    private String safeCap(String s) {
        if (s == null) return "-";
        String t = s.trim();
        if (t.isEmpty()) return "-";
        return t.substring(0, 1).toUpperCase() + t.substring(1);
    }
}
