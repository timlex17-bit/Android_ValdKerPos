package com.example.valdker.ui.purchases;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.valdker.R;

import java.util.List;
import java.util.Locale;

public class PurchaseItemDraftAdapter extends RecyclerView.Adapter<PurchaseItemDraftAdapter.VH> {

    public interface Listener {
        void onRemove(int position);
    }

    private static final String NO_EXPIRED_DATE = "No Expired Date";

    private final List<PurchaseItemDraft> data;
    private final Listener listener;

    public PurchaseItemDraftAdapter(@NonNull List<PurchaseItemDraft> data, @NonNull Listener listener) {
        this.data = data;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_purchase_item_draft, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        PurchaseItemDraft d = data.get(pos);

        double unitCost = parseMoney(d.costPrice);
        double total = d.qty * unitCost;

        h.tvName.setText(d.productName);
        h.tvQty.setText("Qty: " + d.qty);
        h.tvCost.setText("Unit: $" + formatMoneyValue(unitCost));
        h.tvTotal.setText("Total: $" + formatMoneyValue(total));

        String expText;
        if (d.expiredDate != null && !d.expiredDate.trim().isEmpty()) {
            expText = d.expiredDate.trim();
        } else {
            expText = NO_EXPIRED_DATE;
        }
        h.tvExp.setText("Exp: " + expText);

        h.btnRemove.setOnClickListener(v -> listener.onRemove(pos));
    }

    private double parseMoney(String raw) {
        if (raw == null || raw.trim().isEmpty()) return 0d;
        try {
            String clean = raw.replace("$", "").replace(",", "").trim();
            return Double.parseDouble(clean);
        } catch (Exception ignored) {
            return 0d;
        }
    }

    @NonNull
    private String formatMoneyValue(double value) {
        try {
            return String.format(Locale.US, "%.2f", value);
        } catch (Exception ignored) {
            return "0.00";
        }
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvQty, tvCost, tvTotal, tvExp;
        ImageButton btnRemove;

        VH(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvDraftName);
            tvQty = itemView.findViewById(R.id.tvDraftQty);
            tvCost = itemView.findViewById(R.id.tvDraftCost);
            tvTotal = itemView.findViewById(R.id.tvDraftTotal);
            tvExp = itemView.findViewById(R.id.tvDraftExp);
            btnRemove = itemView.findViewById(R.id.btnRemoveDraft);
        }
    }
}