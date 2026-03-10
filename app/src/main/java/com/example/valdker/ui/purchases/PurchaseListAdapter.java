package com.example.valdker.ui.purchases;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.valdker.R;

import java.text.DecimalFormat;
import java.util.List;

public class PurchaseListAdapter extends RecyclerView.Adapter<PurchaseListAdapter.VH> {

    private final List<PurchaseLite> data;
    private final DecimalFormat moneyFormat = new DecimalFormat("#,##0.00");

    public PurchaseListAdapter(@NonNull List<PurchaseLite> data) {
        this.data = data;
    }

    public void setData(@NonNull List<PurchaseLite> newData) {
        data.clear();
        data.addAll(newData);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_purchase_row, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        PurchaseLite p = data.get(pos);

        String title = (p.invoiceId != null && !p.invoiceId.trim().isEmpty())
                ? p.invoiceId.trim()
                : ("Purchase #" + p.id);

        String supplier = (p.supplierName != null && !p.supplierName.trim().isEmpty())
                ? p.supplierName.trim()
                : "No supplier";

        String date = (p.purchaseDate != null && !p.purchaseDate.trim().isEmpty())
                ? p.purchaseDate.trim()
                : "—";

        h.tvTitle.setText(title);
        h.tvSub.setText(supplier);
        h.tvDate.setText(date);

        h.tvItems.setText("Items: " + formatQtyPlain(p.totalItems));
        h.tvTotalCost.setText("Total Cost: $" + formatMoney(p.totalCost));
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    @NonNull
    private String formatMoney(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "0.00";
        try {
            String clean = raw.replace("$", "").replace(",", "").trim();
            double value = Double.parseDouble(clean);
            return moneyFormat.format(value);
        } catch (Exception ignored) {
            return raw;
        }
    }

    @NonNull
    private String formatQtyPlain(int value) {
        return String.valueOf(value);
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvTitle, tvSub, tvDate, tvItems, tvTotalCost;

        VH(@NonNull View itemView) {
            super(itemView);

            tvTitle = itemView.findViewById(R.id.tvPurchaseTitle);
            tvSub = itemView.findViewById(R.id.tvPurchaseSupplier);
            tvDate = itemView.findViewById(R.id.tvPurchaseDate);
            tvItems = itemView.findViewById(R.id.tvPurchaseItems);
            tvTotalCost = itemView.findViewById(R.id.tvPurchaseTotalCost);
        }
    }
}