package com.valdker.pos.ui.warehousestocks;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.valdker.pos.R;
import com.valdker.pos.models.WarehouseStock;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class WarehouseStockAdapter extends RecyclerView.Adapter<WarehouseStockAdapter.ViewHolder> {

    public interface OnWarehouseStockActionListener {
        void onEdit(WarehouseStock stock);
        void onDelete(WarehouseStock stock);
    }

    private final List<WarehouseStock> stocks = new ArrayList<>();
    private final OnWarehouseStockActionListener listener;
    private final DecimalFormat quantityFormat = new DecimalFormat("#.##");

    public WarehouseStockAdapter(OnWarehouseStockActionListener listener) {
        this.listener = listener;
    }

    public void setData(List<WarehouseStock> data) {
        stocks.clear();
        if (data != null) {
            stocks.addAll(data);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public WarehouseStockAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_warehouse_stock, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull WarehouseStockAdapter.ViewHolder holder, int position) {
        WarehouseStock stock = stocks.get(position);

        holder.tvProductName.setText(safe(stock.getProductName()));

        holder.tvProductInfo.setText(
                holder.itemView.getContext().getString(
                        R.string.warehouse_stock_sku_code_format,
                        safe(stock.getProductSku()),
                        safe(stock.getProductCode())
                )
        );

        holder.tvWarehouseInfo.setText(
                holder.itemView.getContext().getString(
                        R.string.warehouse_stock_warehouse_format,
                        safe(stock.getWarehouseName()),
                        safe(stock.getWarehouseCode())
                )
        );

        holder.tvQtyInfo.setText(
                holder.itemView.getContext().getString(
                        R.string.warehouse_stock_qty_min_format,
                        formatStockQty(stock),
                        formatMinimumStock(stock)
                )
        );

        if (stock.isLowStock()) {
            holder.tvLowStockBadge.setText(holder.itemView.getContext().getString(R.string.label_low_stock));
            holder.tvLowStockBadge.setTextColor(Color.parseColor("#DC2626"));
            holder.tvLowStockBadge.setBackgroundResource(R.drawable.bg_badge_low_stock);
        } else {
            holder.tvLowStockBadge.setText(holder.itemView.getContext().getString(R.string.label_normal));
            holder.tvLowStockBadge.setTextColor(Color.parseColor("#16A34A"));
            holder.tvLowStockBadge.setBackgroundResource(R.drawable.bg_badge_normal);
        }

        holder.btnEdit.setOnClickListener(v -> {
            if (listener != null) listener.onEdit(stock);
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(stock);
        });
    }

    @Override
    public int getItemCount() {
        return stocks.size();
    }

    private String safe(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value.trim();
    }

    private String formatStockQty(WarehouseStock stock) {
        String display = stock.getDisplayStock();
        if (display != null && !display.trim().isEmpty()) {
            return display.trim();
        }

        String baseUnit = stock.getBaseUnitName();
        if (baseUnit != null && !baseUnit.trim().isEmpty()) {
            return quantityFormat.format(stock.getQuantityBase()) + " " + baseUnit.trim().toUpperCase();
        }

        return quantityFormat.format(stock.getQuantity());
    }

    private String formatMinimumStock(WarehouseStock stock) {
        String display = stock.getDisplayMinimumStock();
        if (display != null && !display.trim().isEmpty()) {
            return display.trim();
        }

        String baseUnit = stock.getBaseUnitName();
        if (baseUnit != null && !baseUnit.trim().isEmpty()) {
            return quantityFormat.format(stock.getMinStock()) + " " + baseUnit.trim().toUpperCase();
        }

        return quantityFormat.format(stock.getMinStock());
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        TextView tvProductName;
        TextView tvProductInfo;
        TextView tvWarehouseInfo;
        TextView tvQtyInfo;
        TextView tvLowStockBadge;
        TextView btnEdit;
        TextView btnDelete;

        ViewHolder(@NonNull View itemView) {
            super(itemView);

            tvProductName = itemView.findViewById(R.id.tvProductName);
            tvProductInfo = itemView.findViewById(R.id.tvProductInfo);
            tvWarehouseInfo = itemView.findViewById(R.id.tvWarehouseInfo);
            tvQtyInfo = itemView.findViewById(R.id.tvQtyInfo);
            tvLowStockBadge = itemView.findViewById(R.id.tvLowStockBadge);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}
