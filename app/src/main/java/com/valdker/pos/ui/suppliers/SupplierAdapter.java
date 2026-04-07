package com.valdker.pos.ui.suppliers;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.valdker.pos.R;
import com.valdker.pos.models.Supplier;

import java.util.List;

public class SupplierAdapter extends RecyclerView.Adapter<SupplierAdapter.VH> {

    public interface Listener {
        void onEdit(@NonNull Supplier s);
        void onDelete(@NonNull Supplier s);
    }

    private final List<Supplier> data;
    private final Listener listener;

    public SupplierAdapter(@NonNull List<Supplier> data, @NonNull Listener listener) {
        this.data = data;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_supplier, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Supplier s = data.get(position);

        h.tvName.setText(s.name);
        h.tvContact.setText("Contact: " + s.contactPerson);
        h.tvCell.setText("Cell: " + s.cell);

        String meta = "";
        if (s.email != null && !s.email.trim().isEmpty()) meta += s.email.trim();
        if (s.address != null && !s.address.trim().isEmpty()) {
            if (!meta.isEmpty()) meta += " • ";
            meta += s.address.trim();
        }
        h.tvMeta.setText(meta.isEmpty() ? "-" : meta);

        h.btnEdit.setOnClickListener(v -> listener.onEdit(s));
        h.btnDelete.setOnClickListener(v -> listener.onDelete(s));
        h.itemView.setOnClickListener(v -> listener.onEdit(s));
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvContact, tvCell, tvMeta;
        ImageButton btnEdit, btnDelete;

        VH(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvSupplierName);
            tvContact = itemView.findViewById(R.id.tvSupplierContact);
            tvCell = itemView.findViewById(R.id.tvSupplierCell);
            tvMeta = itemView.findViewById(R.id.tvSupplierMeta);
            btnEdit = itemView.findViewById(R.id.btnSupplierEdit);
            btnDelete = itemView.findViewById(R.id.btnSupplierDelete);
        }
    }
}
