package com.valdker.pos.ui.warehouses;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.valdker.pos.R;
import com.valdker.pos.models.Warehouse;

import java.util.ArrayList;
import java.util.List;

public class WarehouseAdapter extends RecyclerView.Adapter<WarehouseAdapter.ViewHolder> {

    public interface OnWarehouseActionListener {
        void onEdit(Warehouse warehouse);
        void onDelete(Warehouse warehouse);
    }

    private final List<Warehouse> warehouses = new ArrayList<>();
    private final OnWarehouseActionListener listener;

    public WarehouseAdapter(OnWarehouseActionListener listener) {
        this.listener = listener;
    }

    public void setData(List<Warehouse> data) {
        warehouses.clear();
        if (data != null) {
            warehouses.addAll(data);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public WarehouseAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_warehouse, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull WarehouseAdapter.ViewHolder holder, int position) {
        Warehouse warehouse = warehouses.get(position);

        holder.tvName.setText(warehouse.getName());
        holder.tvCode.setText(holder.itemView.getContext().getString(
                R.string.warehouse_code_format,
                safe(warehouse.getCode())
        ));
        holder.tvLocation.setText(holder.itemView.getContext().getString(
                R.string.warehouse_location_format,
                safe(warehouse.getLocation())
        ));

        holder.tvDefaultBadge.setVisibility(warehouse.isDefault() ? View.VISIBLE : View.GONE);

        if (warehouse.isActive()) {
            holder.tvStatusBadge.setText(holder.itemView.getContext().getString(R.string.label_active));
            holder.tvStatusBadge.setTextColor(Color.parseColor("#0284C7"));
            holder.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_status);
        } else {
            holder.tvStatusBadge.setText(holder.itemView.getContext().getString(R.string.label_inactive));
            holder.tvStatusBadge.setTextColor(Color.parseColor("#DC2626"));
            holder.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_inactive);
        }
        holder.btnEdit.setOnClickListener(v -> {
            if (listener != null) listener.onEdit(warehouse);
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(warehouse);
        });
    }

    @Override
    public int getItemCount() {
        return warehouses.size();
    }

    private String safe(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        TextView tvName, tvCode, tvLocation, tvDefaultBadge, tvStatusBadge, btnEdit, btnDelete;

        ViewHolder(@NonNull View itemView) {
            super(itemView);

            tvName = itemView.findViewById(R.id.tvWarehouseName);
            tvCode = itemView.findViewById(R.id.tvWarehouseCode);
            tvLocation = itemView.findViewById(R.id.tvWarehouseLocation);
            tvDefaultBadge = itemView.findViewById(R.id.tvDefaultBadge);
            tvStatusBadge = itemView.findViewById(R.id.tvStatusBadge);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}
