package com.example.valdker.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.example.valdker.R;
import com.example.valdker.models.BankAccount;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class BankAccountAdapter extends RecyclerView.Adapter<BankAccountAdapter.ViewHolder> {

    public interface OnBankActionListener {
        void onEdit(BankAccount item);
        void onDelete(BankAccount item);
        void onViewLedger(BankAccount item);
    }

    private final Context context;
    private final List<BankAccount> items;
    private final OnBankActionListener listener;

    public BankAccountAdapter(Context context, List<BankAccount> items, OnBankActionListener listener) {
        this.context = context;
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public BankAccountAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_bank_account, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BankAccountAdapter.ViewHolder holder, int position) {
        BankAccount item = items.get(position);

        holder.tvBankTitle.setText(item.getBankName() + " - " + item.getName());

        String accNo = item.getAccountNumber() == null || item.getAccountNumber().trim().isEmpty()
                ? "-"
                : item.getAccountNumber();
        holder.tvAccountNumber.setText("No. Rekening: " + accNo);

        String holderName = item.getAccountHolder() == null || item.getAccountHolder().trim().isEmpty()
                ? "-"
                : item.getAccountHolder();
        holder.tvAccountHolder.setText("Atas Nama: " + holderName);

        holder.tvAccountType.setText("Tipe: " + item.getAccountType());
        holder.tvOpeningBalance.setText("Opening: " + formatCurrency(item.getOpeningBalance()));
        holder.tvCurrentBalance.setText("Saldo Sekarang: " + formatCurrency(item.getCurrentBalance()));
        holder.tvStatus.setText(item.isActive() ? "ACTIVE" : "INACTIVE");

        holder.tvStatus.setBackgroundResource(
                item.isActive() ? R.drawable.bg_status_active : R.drawable.bg_status_inactive
        );

        String note = item.getNote() == null || item.getNote().trim().isEmpty()
                ? "Catatan: -"
                : "Catatan: " + item.getNote();
        holder.tvNote.setText(note);

        holder.btnEdit.setOnClickListener(v -> {
            if (listener != null) listener.onEdit(item);
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(item);
        });

        holder.cardRoot.setOnClickListener(v -> {
            if (listener != null) listener.onViewLedger(item);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String formatCurrency(String value) {
        try {
            double amount = Double.parseDouble(value);
            NumberFormat format = NumberFormat.getCurrencyInstance(Locale.US);
            return format.format(amount);
        } catch (Exception e) {
            return "$0.00";
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        CardView cardRoot;
        TextView tvBankTitle, tvAccountNumber, tvAccountHolder, tvAccountType,
                tvOpeningBalance, tvCurrentBalance, tvStatus, tvNote;
        ImageButton btnEdit, btnDelete;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardRoot = itemView.findViewById(R.id.cardRoot);
            tvBankTitle = itemView.findViewById(R.id.tvBankTitle);
            tvAccountNumber = itemView.findViewById(R.id.tvAccountNumber);
            tvAccountHolder = itemView.findViewById(R.id.tvAccountHolder);
            tvAccountType = itemView.findViewById(R.id.tvAccountType);
            tvOpeningBalance = itemView.findViewById(R.id.tvOpeningBalance);
            tvCurrentBalance = itemView.findViewById(R.id.tvCurrentBalance);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvNote = itemView.findViewById(R.id.tvNote);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}