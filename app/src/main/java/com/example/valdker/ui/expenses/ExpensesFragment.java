package com.example.valdker.ui.expenses;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.valdker.R;
import com.example.valdker.SessionManager;
import com.example.valdker.models.Expense;
import com.example.valdker.repositories.ExpenseRepository;

import java.util.List;

public class ExpensesFragment extends Fragment {

    private SessionManager session;
    private ExpenseRepository repo;

    private RecyclerView rv;
    private ProgressBar progress;
    private TextView tvEmpty;
    private TextView btnAdd;

    private ExpenseAdapter adapter;

    public ExpensesFragment() {
        super(R.layout.fragment_expenses);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        session = new SessionManager(requireContext());
        repo = new ExpenseRepository(requireContext());

        rv = view.findViewById(R.id.rvExpenses);
        progress = view.findViewById(R.id.progress);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        btnAdd = view.findViewById(R.id.btnAdd);

        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ExpenseAdapter(new ExpenseAdapter.Listener() {
            @Override public void onEdit(@NonNull Expense e) { openForm(e); }
            @Override public void onDelete(@NonNull Expense e) { confirmDelete(e); }
        });
        rv.setAdapter(adapter);

        btnAdd.setOnClickListener(v -> openForm(null));

        load();
    }

    private void load() {
        String token = session.getToken();
        if (TextUtils.isEmpty(token)) {
            Toast.makeText(requireContext(), "No token. Please login again.", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading(true);
        repo.fetchExpenses(token, new ExpenseRepository.ListCallback() {
            @Override
            public void onSuccess(@NonNull List<Expense> list) {
                showLoading(false);
                adapter.submit(list);
                tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                showLoading(false);
                Toast.makeText(requireContext(), "Load failed: " + statusCode, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openForm(@Nullable Expense editing) {
        View form = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_expense_form, null, false);

        EditText etName = form.findViewById(R.id.etName);
        EditText etNote = form.findViewById(R.id.etNote);
        EditText etAmount = form.findViewById(R.id.etAmount);
        EditText etDate = form.findViewById(R.id.etDate);
        EditText etTime = form.findViewById(R.id.etTime);

        boolean isEdit = (editing != null);

        if (isEdit) {
            etName.setText(editing.name);
            etNote.setText(editing.note);
            etAmount.setText(editing.amount);
            etDate.setText(editing.date);
            etTime.setText(editing.time);
        }

        new AlertDialog.Builder(requireContext())
                .setTitle(isEdit ? "Edit Expense" : "Add Expense")
                .setView(form)
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .setPositiveButton(isEdit ? "Save" : "Create", (d, w) -> {
                    // will override later to prevent auto dismiss on validation
                })
                .create();

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(isEdit ? "Edit Expense" : "Add Expense")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(isEdit ? "Save" : "Create", null)
                .show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String note = etNote.getText().toString().trim();
            String amount = etAmount.getText().toString().trim();
            String date = etDate.getText().toString().trim();
            String time = etTime.getText().toString().trim();

            if (TextUtils.isEmpty(name) || TextUtils.isEmpty(amount) || TextUtils.isEmpty(date) || TextUtils.isEmpty(time)) {
                Toast.makeText(requireContext(), "Name, Amount, Date, Time are required.", Toast.LENGTH_SHORT).show();
                return;
            }

            Expense payload = new Expense();
            payload.name = name;
            payload.note = note;
            payload.amount = amount;
            payload.date = date;
            payload.time = time;

            String token = session.getToken();

            showLoading(true);

            if (!isEdit) {
                repo.createExpense(token, payload, new ExpenseRepository.ItemCallback() {
                    @Override
                    public void onSuccess(@NonNull Expense expense) {
                        showLoading(false);
                        dialog.dismiss();
                        load();
                    }

                    @Override
                    public void onError(int statusCode, @NonNull String message) {
                        showLoading(false);
                        Toast.makeText(requireContext(), "Create failed: " + statusCode, Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                repo.updateExpense(token, editing.id, payload, new ExpenseRepository.ItemCallback() {
                    @Override
                    public void onSuccess(@NonNull Expense expense) {
                        showLoading(false);
                        dialog.dismiss();
                        load();
                    }

                    @Override
                    public void onError(int statusCode, @NonNull String message) {
                        showLoading(false);
                        Toast.makeText(requireContext(), "Update failed: " + statusCode, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void confirmDelete(@NonNull Expense e) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Expense")
                .setMessage("Delete \"" + e.name + "\"?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> doDelete(e))
                .show();
    }

    private void doDelete(@NonNull Expense e) {
        String token = session.getToken();
        showLoading(true);
        repo.deleteExpense(token, e.id, new ExpenseRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                showLoading(false);
                load();
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                showLoading(false);
                Toast.makeText(requireContext(), "Delete failed: " + statusCode, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showLoading(boolean on) {
        if (progress != null) progress.setVisibility(on ? View.VISIBLE : View.GONE);
        if (rv != null) rv.setEnabled(!on);
        if (btnAdd != null) btnAdd.setEnabled(!on);
    }
}
