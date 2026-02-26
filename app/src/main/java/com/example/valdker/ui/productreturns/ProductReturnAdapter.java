package com.example.valdker.ui.productreturns;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.valdker.R;
import com.example.valdker.models.ProductReturn;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ProductReturnAdapter extends RecyclerView.Adapter<ProductReturnAdapter.VH> {

    public interface Listener {
        void onClick(@NonNull ProductReturn item);
    }

    private final List<ProductReturn> data;
    private final Listener listener;

    public ProductReturnAdapter(@NonNull List<ProductReturn> data, @NonNull Listener listener) {
        this.data = data;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_product_return, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {

        ProductReturn it = data.get(position);

        h.tvTitle.setText("Return #" + it.id);

        // ✅ Invoice (fallback to Order id)
        String inv = (it.invoiceNumber != null) ? it.invoiceNumber.trim() : "";
        if (inv.isEmpty()) {
            h.tvInvoice.setText("Order: " + (it.order != null ? it.order : "-"));
        } else {
            h.tvInvoice.setText("Invoice: " + inv);
        }

        // ✅ Customer name
        String customerName = (it.customer != null && it.customer.name != null && !it.customer.name.trim().isEmpty())
                ? it.customer.name
                : "-";
        h.tvCustomer.setText("Customer: " + customerName);

        // ✅ Returned by display name
        String returnedByName = (it.returnedBy != null) ? it.returnedBy.bestName() : "-";
        h.tvReturnedBy.setText("Returned by: " + returnedByName);

        // ✅ Note (only show if not empty)
        String note = (it.note == null) ? "" : it.note.trim();
        if (note.isEmpty()) {
            h.tvNote.setVisibility(View.GONE);
        } else {
            h.tvNote.setVisibility(View.VISIBLE);
            h.tvNote.setText("Note: " + note);
        }

        // ✅ Items summary
        h.tvItems.setText("Items: " + it.itemsCount()
                + " • Qty: " + trimZero(it.totalQty())
                + " • Total: " + formatMoney(it.totalAmount()));

        // ✅ Date
        h.tvDate.setText("Returned: " + formatIso(it.returnedAt));

        // ✅ Click -> delegate to Fragment/Activity
        h.itemView.setOnClickListener(v -> listener.onClick(it));
    }

    private String trimZero(double v) {
        if (Math.abs(v - Math.round(v)) < 0.000001)
            return String.valueOf((long) Math.round(v));
        return String.valueOf(v);
    }

    private String formatMoney(double v) {
        java.text.NumberFormat nf =
                java.text.NumberFormat.getCurrencyInstance(java.util.Locale.US);
        return nf.format(v);
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvTitle, tvDate, tvInvoice, tvCustomer, tvReturnedBy, tvNote, tvItems;

        VH(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvInvoice = itemView.findViewById(R.id.tvInvoice);
            tvCustomer = itemView.findViewById(R.id.tvCustomer);
            tvReturnedBy = itemView.findViewById(R.id.tvReturnedBy);
            tvNote = itemView.findViewById(R.id.tvNote);
            tvItems = itemView.findViewById(R.id.tvItems);
        }
    }

    private String formatIso(String iso) {
        try {
            // Example: 2026-02-19T11:21:38Z
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            in.setLenient(true);
            Date d = in.parse(iso);
            SimpleDateFormat out = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
            return d != null ? out.format(d) : iso;
        } catch (Exception e) {
            return iso == null ? "-" : iso;
        }
    }
}