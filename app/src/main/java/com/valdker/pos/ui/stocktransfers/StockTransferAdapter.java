package com.valdker.pos.ui.stocktransfers;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.valdker.pos.R;
import com.valdker.pos.models.StockTransfer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class StockTransferAdapter extends RecyclerView.Adapter<StockTransferAdapter.ViewHolder> {

    public interface OnStockTransferActionListener {
        void onEdit(StockTransfer transfer);
        void onDelete(StockTransfer transfer);
        void onComplete(StockTransfer transfer);
        void onCancel(StockTransfer transfer);
    }

    private final List<StockTransfer> transfers = new ArrayList<>();
    private final OnStockTransferActionListener listener;

    public StockTransferAdapter(OnStockTransferActionListener listener) {
        this.listener = listener;
    }

    public void setData(List<StockTransfer> data) {
        transfers.clear();
        if (data != null) {
            transfers.addAll(data);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public StockTransferAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_stock_transfer, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StockTransferAdapter.ViewHolder holder, int position) {
        StockTransfer transfer = transfers.get(position);

        String reference = transfer.getReferenceNo();
        if (reference == null || reference.trim().isEmpty()) {
            reference = holder.itemView.getContext().getString(
                    R.string.stock_transfer_fallback_reference,
                    transfer.getId()
            );
        }

        holder.tvReferenceNo.setText(reference);

        String fromWarehouse = safe(transfer.getFromWarehouseName());
        String toWarehouse = safe(transfer.getToWarehouseName());

        holder.tvWarehouseRoute.setText(holder.itemView.getContext().getString(
                R.string.stock_transfer_route_format,
                fromWarehouse,
                toWarehouse
        ));
        holder.tvNote.setText(holder.itemView.getContext().getString(
                R.string.stock_transfer_note_format,
                safe(transfer.getNote())
        ));
        holder.tvCreatedAt.setText(holder.itemView.getContext().getString(
                R.string.stock_transfer_created_format,
                safe(transfer.getCreatedAt())
        ));

        String status = safe(transfer.getStatus()).toUpperCase(Locale.US);
        holder.tvStatus.setText(formatStatus(status));

        applyStatusStyle(holder.tvStatus, status);

        boolean isDraft = "DRAFT".equalsIgnoreCase(status);

        holder.btnEdit.setVisibility(isDraft ? View.VISIBLE : View.GONE);
        holder.btnComplete.setVisibility(isDraft ? View.VISIBLE : View.GONE);
        holder.btnCancel.setVisibility(isDraft ? View.VISIBLE : View.GONE);

        holder.btnEdit.setOnClickListener(v -> {
            if (listener != null) listener.onEdit(transfer);
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(transfer);
        });

        holder.btnComplete.setOnClickListener(v -> {
            if (listener != null) listener.onComplete(transfer);
        });

        holder.btnCancel.setOnClickListener(v -> {
            if (listener != null) listener.onCancel(transfer);
        });
    }

    private void applyStatusStyle(TextView tvStatus, String status) {
        if ("COMPLETED".equalsIgnoreCase(status) || "COMPLETE".equalsIgnoreCase(status)) {
            tvStatus.setTextColor(Color.parseColor("#16A34A"));
            tvStatus.setBackgroundResource(R.drawable.bg_stock_transfer_status_completed);
        } else if ("CANCELLED".equalsIgnoreCase(status) || "CANCELED".equalsIgnoreCase(status)) {
            tvStatus.setTextColor(Color.parseColor("#DC2626"));
            tvStatus.setBackgroundResource(R.drawable.bg_stock_transfer_status_cancelled);
        } else {
            tvStatus.setTextColor(Color.parseColor("#2563EB"));
            tvStatus.setBackgroundResource(R.drawable.bg_stock_transfer_status_draft);
        }
    }

    private String formatStatus(String status) {
        if ("COMPLETED".equalsIgnoreCase(status) || "COMPLETE".equalsIgnoreCase(status)) {
            return "Completed";
        }
        if ("CANCELLED".equalsIgnoreCase(status) || "CANCELED".equalsIgnoreCase(status)) {
            return "Cancelled";
        }
        if ("DRAFT".equalsIgnoreCase(status)) {
            return "Draft";
        }
        return status;
    }

    private String safe(String value) {
        if (value == null) return "-";
        String trimmed = value.trim();
        return trimmed.isEmpty() ? "-" : trimmed;
    }

    @Override
    public int getItemCount() {
        return transfers.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        TextView tvReferenceNo;
        TextView tvStatus;
        TextView tvWarehouseRoute;
        TextView tvNote;
        TextView tvCreatedAt;

        TextView btnEdit;
        TextView btnComplete;
        TextView btnCancel;
        TextView btnDelete;

        View actionContainer;

        ViewHolder(@NonNull View itemView) {
            super(itemView);

            tvReferenceNo = itemView.findViewById(R.id.tvReferenceNo);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvWarehouseRoute = itemView.findViewById(R.id.tvWarehouseRoute);
            tvNote = itemView.findViewById(R.id.tvNote);
            tvCreatedAt = itemView.findViewById(R.id.tvCreatedAt);

            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnComplete = itemView.findViewById(R.id.btnComplete);
            btnCancel = itemView.findViewById(R.id.btnCancel);
            btnDelete = itemView.findViewById(R.id.btnDelete);

            actionContainer = itemView.findViewById(R.id.actionContainer);
        }
    }
}

