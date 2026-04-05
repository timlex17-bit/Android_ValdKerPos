package com.example.valdker.ui.customers;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.valdker.R;
import com.example.valdker.models.Customer;

import java.util.List;
import java.util.Locale;

public class CustomerAdapter extends RecyclerView.Adapter<CustomerAdapter.VH> {

    public interface Listener {
        void onEdit(@NonNull Customer c);
        void onDelete(@NonNull Customer c);
    }

    private final List<Customer> data;
    private final Listener listener;

    public CustomerAdapter(@NonNull List<Customer> data, @NonNull Listener listener) {
        this.data = data;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_customer, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Customer c = data.get(position);

        h.tvName.setText(safe(c.name));
        h.tvCell.setText(safeOrDash(c.cell));
        h.tvPoints.setText(String.format(Locale.US, "Points: %d", c.points));

        String meta = "";
        if (c.email != null && !c.email.trim().isEmpty()) {
            meta += c.email.trim();
        }
        if (c.address != null && !c.address.trim().isEmpty()) {
            if (!meta.isEmpty()) meta += " • ";
            meta += c.address.trim();
        }
        h.tvMeta.setText(meta.isEmpty() ? "-" : meta);

        h.btnEdit.setOnClickListener(v -> listener.onEdit(c));
        h.btnDelete.setOnClickListener(v -> listener.onDelete(c));
        h.itemView.setOnClickListener(v -> listener.onEdit(c));
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private String safeOrDash(String s) {
        if (s == null || s.trim().isEmpty()) return "-";
        return s.trim();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvPoints, tvCell, tvMeta;
        ImageButton btnEdit, btnDelete;

        VH(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvCustomerName);
            tvPoints = itemView.findViewById(R.id.tvCustomerPoints);
            tvCell = itemView.findViewById(R.id.tvCustomerCell);
            tvMeta = itemView.findViewById(R.id.tvCustomerMeta);
            btnEdit = itemView.findViewById(R.id.btnCustomerEdit);
            btnDelete = itemView.findViewById(R.id.btnCustomerDelete);
        }
    }
}