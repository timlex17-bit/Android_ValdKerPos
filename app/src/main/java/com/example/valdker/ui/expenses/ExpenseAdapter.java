package com.example.valdker.ui.expenses;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.valdker.R;
import com.example.valdker.models.Expense;

import java.util.ArrayList;
import java.util.List;

public class ExpenseAdapter extends RecyclerView.Adapter<ExpenseAdapter.VH> {

    public interface Listener {
        void onEdit(@NonNull Expense e);
        void onDelete(@NonNull Expense e);
    }

    private final List<Expense> items = new ArrayList<>();
    private final Listener listener;

    public ExpenseAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    public void submit(@NonNull List<Expense> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_expense, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Expense e = items.get(pos);

        h.tvName.setText(e.name);
        h.tvMeta.setText((e.date != null ? e.date : "") + " " + (e.time != null ? e.time : ""));
        h.tvAmount.setText("$" + (e.amount != null ? e.amount : "0.00"));

        h.btnEdit.setOnClickListener(v -> listener.onEdit(e));
        h.btnDelete.setOnClickListener(v -> listener.onDelete(e));
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvMeta, tvAmount, btnEdit, btnDelete;

        VH(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvMeta = itemView.findViewById(R.id.tvMeta);
            tvAmount = itemView.findViewById(R.id.tvAmount);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}
