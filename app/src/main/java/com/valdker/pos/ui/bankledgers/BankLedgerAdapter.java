package com.valdker.pos.ui.bankledgers;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.valdker.pos.R;
import com.valdker.pos.models.BankLedger;

import java.util.ArrayList;
import java.util.List;

public class BankLedgerAdapter extends RecyclerView.Adapter<BankLedgerAdapter.ViewHolder> {

    public interface Listener {
        void onClick(BankLedger ledger);
    }

    private final Context context;
    private final Listener listener;
    private final List<BankLedger> data = new ArrayList<>();

    public BankLedgerAdapter(@NonNull Context context, @NonNull Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setData(List<BankLedger> items) {
        data.clear();
        if (items != null) {
            data.addAll(items);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_bank_ledger, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BankLedger ledger = data.get(position);

        holder.tvBankName.setText(safe(ledger.getBankAccountName()));
        holder.tvTransactionType.setText(ledger.getDisplayTransactionType());
        holder.tvDirection.setText(safe(ledger.getDirection()));
        holder.tvAmount.setText(context.getString(R.string.bank_ledger_amount_format, ledger.getFormattedAmount()));
        holder.tvBalance.setText(context.getString(
                R.string.bank_ledger_balance_format,
                ledger.getFormattedBalanceBefore(),
                ledger.getFormattedBalanceAfter()
        ));
        holder.tvDate.setText(context.getString(R.string.bank_ledger_date_format, ledger.getDisplayDate()));

        String invoice = ledger.getReferenceOrderInvoice();
        holder.tvInvoice.setVisibility(invoice.isEmpty() ? View.GONE : View.VISIBLE);
        holder.tvInvoice.setText(context.getString(R.string.bank_ledger_invoice_format, invoice));

        String description = ledger.getDescription();
        holder.tvDescription.setVisibility(description.isEmpty() ? View.GONE : View.VISIBLE);
        holder.tvDescription.setText(context.getString(R.string.bank_ledger_description_format, description));

        if (ledger.isIn()) {
            holder.tvDirection.setBackgroundColor(Color.parseColor("#DCFCE7"));
            holder.tvDirection.setTextColor(Color.parseColor("#16A34A"));
        } else if (ledger.isOut()) {
            holder.tvDirection.setBackgroundColor(Color.parseColor("#FFEDD5"));
            holder.tvDirection.setTextColor(Color.parseColor("#C2410C"));
        } else {
            holder.tvDirection.setBackgroundColor(Color.parseColor("#E5E7EB"));
            holder.tvDirection.setTextColor(Color.parseColor("#374151"));
        }

        holder.cardRoot.setOnClickListener(v -> {
            if (listener != null) listener.onClick(ledger);
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    private String safe(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value.trim();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CardView cardRoot;
        TextView tvBankName;
        TextView tvTransactionType;
        TextView tvDirection;
        TextView tvAmount;
        TextView tvBalance;
        TextView tvInvoice;
        TextView tvDescription;
        TextView tvDate;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardRoot = itemView.findViewById(R.id.cardRoot);
            tvBankName = itemView.findViewById(R.id.tvBankName);
            tvTransactionType = itemView.findViewById(R.id.tvTransactionType);
            tvDirection = itemView.findViewById(R.id.tvDirection);
            tvAmount = itemView.findViewById(R.id.tvAmount);
            tvBalance = itemView.findViewById(R.id.tvBalance);
            tvInvoice = itemView.findViewById(R.id.tvInvoice);
            tvDescription = itemView.findViewById(R.id.tvDescription);
            tvDate = itemView.findViewById(R.id.tvDate);
        }
    }
}
