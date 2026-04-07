package com.valdker.pos.ui.productreturns;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.valdker.pos.R;
import com.valdker.pos.models.ProductLite;
import com.valdker.pos.models.ProductReturnItem;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ProductReturnItemAdapter extends RecyclerView.Adapter<ProductReturnItemAdapter.VH> {

    private final List<ProductReturnItem> data = new ArrayList<>();
    private final NumberFormat usd = NumberFormat.getCurrencyInstance(Locale.US);

    public void setData(List<ProductReturnItem> items) {
        data.clear();
        if (items != null) data.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_product_return_detail_row, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        ProductReturnItem it = data.get(position);

        ProductLite p = it.product;
        String name = (p != null && p.name != null && !p.name.trim().isEmpty()) ? p.name : "-";

        h.tvName.setText(name);
        h.tvQty.setText(trimZero(it.quantity));
        h.tvUnitPrice.setText(usd.format(it.unitPriceAsDouble()));
        h.tvLineTotal.setText(usd.format(it.lineTotal()));
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvQty, tvUnitPrice, tvLineTotal;

        VH(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvQty = itemView.findViewById(R.id.tvQty);
            tvUnitPrice = itemView.findViewById(R.id.tvUnitPrice);
            tvLineTotal = itemView.findViewById(R.id.tvLineTotal);
        }
    }

    private String trimZero(double v) {
        if (Math.abs(v - Math.round(v)) < 0.000001)
            return String.valueOf((long) Math.round(v));
        return String.valueOf(v);
    }
}