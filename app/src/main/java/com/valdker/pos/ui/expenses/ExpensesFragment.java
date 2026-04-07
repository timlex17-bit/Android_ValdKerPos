package com.valdker.pos.ui.expenses;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.valdker.pos.R;
import com.valdker.pos.SessionManager;
import com.valdker.pos.base.BaseFragment;
import com.valdker.pos.models.Expense;
import com.valdker.pos.repositories.ExpenseRepository;
import com.valdker.pos.utils.InsetsHelper;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class ExpensesFragment extends BaseFragment {

    private static final long FAB_CLICK_DELAY_MS = 700L;

    private SessionManager session;
    private ExpenseRepository repo;

    private RecyclerView rv;
    private ProgressBar progress;
    private TextView tvEmpty;
    private TextView tvTitle;
    private FloatingActionButton fabAdd;
    private EditText etSearchExpense;
    private ImageView btnBack;
    private ImageView ivHeaderAction;

    private ExpenseAdapter adapter;

    private final List<Expense> allExpenses = new ArrayList<>();

    private long lastFabClickTime = 0L;
    private boolean isFormShowing = false;
    private boolean isLoading = false;
    private boolean isDeleteRunning = false;
    private String currentQuery = "";

    public ExpensesFragment() {
        super(R.layout.fragment_expenses);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        applyTopInset(view.findViewById(R.id.topBar));

        session = new SessionManager(requireContext());
        repo = new ExpenseRepository(requireContext());

        rv = view.findViewById(R.id.rvExpenses);
        progress = view.findViewById(R.id.progress);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        tvTitle = view.findViewById(R.id.tvTitle);
        fabAdd = view.findViewById(R.id.fabAddExpense);
        etSearchExpense = view.findViewById(R.id.etSearchExpense);
        btnBack = view.findViewById(R.id.btnBack);
        ivHeaderAction = view.findViewById(R.id.ivHeaderAction);

        InsetsHelper.applyRecyclerBottomInsets(view, rv, "EXPENSES");
        applyFabBottomInset(fabAdd, 56);

        if (rv != null) {
            rv.setLayoutManager(new LinearLayoutManager(requireContext()));
            rv.setHasFixedSize(false);
            rv.setClipToPadding(false);
        }

        adapter = new ExpenseAdapter(new ExpenseAdapter.Listener() {
            @Override
            public void onEdit(@NonNull Expense e) {
                openForm(e);
            }

            @Override
            public void onDelete(@NonNull Expense e) {
                confirmDelete(e);
            }
        });

        if (rv != null) {
            rv.setAdapter(adapter);
        }

        if (fabAdd != null) {
            fabAdd.setOnClickListener(v -> openAddExpenseSafely());

            fabAdd.post(() -> {
                if (fabAdd == null) return;
                fabAdd.bringToFront();
                fabAdd.setElevation(100f);
                fabAdd.setTranslationZ(100f);
            });
        }

        if (btnBack != null) {
            btnBack.setOnClickListener(v ->
                    requireActivity().getOnBackPressedDispatcher().onBackPressed()
            );
        }

        if (ivHeaderAction != null) {
            ivHeaderAction.setOnClickListener(v -> {
                if (isLoading) return;
                load();
            });
        }

        if (etSearchExpense != null) {
            etSearchExpense.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    currentQuery = s == null ? "" : s.toString().trim();
                    applyFilter();
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });
        }

        load();
    }

    private void openAddExpenseSafely() {
        if (!isAdded()) return;
        if (isFormShowing) return;
        if (isLoading) return;

        long now = SystemClock.elapsedRealtime();
        if (now - lastFabClickTime < FAB_CLICK_DELAY_MS) {
            return;
        }
        lastFabClickTime = now;

        setFabEnabled(false);
        openForm(null);
    }

    private void load() {
        if (!isAdded()) return;
        if (isLoading) return;

        String token = session != null ? session.getToken() : null;
        if (TextUtils.isEmpty(token)) {
            allExpenses.clear();
            if (adapter != null) {
                adapter.submit(new ArrayList<>());
            }
            setEmpty(true);
            toast("No token. Please login again.");
            return;
        }

        isLoading = true;
        showLoading(true);

        repo.fetchExpenses(token, new ExpenseRepository.ListCallback() {
            @Override
            public void onSuccess(@NonNull List<Expense> list) {
                isLoading = false;
                if (!isAdded()) return;

                showLoading(false);
                allExpenses.clear();
                allExpenses.addAll(list);
                applyFilter();
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                isLoading = false;
                if (!isAdded()) return;

                showLoading(false);
                toast("Load failed: " + statusCode);
                setEmpty(adapter == null || adapter.getItemCount() == 0);
            }
        });
    }

    private void applyFilter() {
        if (!isAdded() || adapter == null) return;

        List<Expense> filtered = new ArrayList<>();

        if (TextUtils.isEmpty(currentQuery)) {
            filtered.addAll(allExpenses);
        } else {
            String q = currentQuery.toLowerCase(Locale.US);

            for (Expense e : allExpenses) {
                String name = safeLower(e.name);
                String note = safeLower(e.note);
                String amount = safeLower(e.amount);
                String date = safeLower(e.date);
                String time = safeLower(e.time);

                if (name.contains(q)
                        || note.contains(q)
                        || amount.contains(q)
                        || date.contains(q)
                        || time.contains(q)) {
                    filtered.add(e);
                }
            }
        }

        adapter.submit(filtered);
        setEmpty(filtered.isEmpty());
    }

    @NonNull
    private String safeLower(@Nullable String value) {
        return value == null ? "" : value.toLowerCase(Locale.US);
    }

    private void openForm(@Nullable Expense editing) {
        if (!isAdded()) return;
        if (isFormShowing) return;

        isFormShowing = true;
        setFabEnabled(false);

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

        Calendar cal = Calendar.getInstance();

        if (isEdit) {
            if (!TextUtils.isEmpty(editing.date)) {
                try {
                    String[] p = editing.date.split("-");
                    cal.set(Calendar.YEAR, Integer.parseInt(p[0]));
                    cal.set(Calendar.MONTH, Integer.parseInt(p[1]) - 1);
                    cal.set(Calendar.DAY_OF_MONTH, Integer.parseInt(p[2]));
                } catch (Exception ignore) {
                }
            }

            if (!TextUtils.isEmpty(editing.time)) {
                try {
                    String[] t = editing.time.split(":");
                    cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(t[0]));
                    cal.set(Calendar.MINUTE, Integer.parseInt(t[1]));
                } catch (Exception ignore) {
                }
            }
        }

        etDate.setOnClickListener(v -> {
            int y = cal.get(Calendar.YEAR);
            int m = cal.get(Calendar.MONTH);
            int d = cal.get(Calendar.DAY_OF_MONTH);

            DatePickerDialog dp = new DatePickerDialog(requireContext(), (view, year, month, dayOfMonth) -> {
                cal.set(Calendar.YEAR, year);
                cal.set(Calendar.MONTH, month);
                cal.set(Calendar.DAY_OF_MONTH, dayOfMonth);

                String formatted = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth);
                etDate.setText(formatted);
            }, y, m, d);

            dp.show();
        });

        etTime.setOnClickListener(v -> {
            int hour = cal.get(Calendar.HOUR_OF_DAY);
            int minute = cal.get(Calendar.MINUTE);

            TimePickerDialog tp = new TimePickerDialog(requireContext(), (view, h, min) -> {
                cal.set(Calendar.HOUR_OF_DAY, h);
                cal.set(Calendar.MINUTE, min);

                String formatted = String.format(Locale.US, "%02d:%02d:00", h, min);
                etTime.setText(formatted);
            }, hour, minute, true);

            tp.show();
        });

        if (!isEdit) {
            etDate.setText(String.format(Locale.US, "%04d-%02d-%02d",
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH) + 1,
                    cal.get(Calendar.DAY_OF_MONTH)));

            etTime.setText(String.format(Locale.US, "%02d:%02d:00",
                    cal.get(Calendar.HOUR_OF_DAY),
                    cal.get(Calendar.MINUTE)));
        }

        int titleRes = isEdit
                ? R.string.title_edit_expense
                : R.string.title_add_expense;

        int positiveRes = isEdit
                ? R.string.btn_save
                : R.string.btn_create;

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(getString(titleRes))
                .setView(form)
                .setNegativeButton(getString(R.string.btn_cancel), (d, w) -> d.dismiss())
                .setPositiveButton(getString(positiveRes), null)
                .create();

        dialog.setOnShowListener(d -> {
            View positiveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (positiveBtn == null) return;

            positiveBtn.setOnClickListener(v -> {
                String name = etName.getText().toString().trim();
                String note = etNote.getText().toString().trim();
                String amount = etAmount.getText().toString().trim();
                String date = etDate.getText().toString().trim();
                String time = etTime.getText().toString().trim();

                if (TextUtils.isEmpty(name) || TextUtils.isEmpty(amount) || TextUtils.isEmpty(date) || TextUtils.isEmpty(time)) {
                    toast("Name, Amount, Date, Time are required.");
                    return;
                }

                Expense payload = new Expense();
                payload.name = name;
                payload.note = note;
                payload.amount = amount;
                payload.date = date;
                payload.time = time;

                String token = session != null ? session.getToken() : null;
                if (TextUtils.isEmpty(token)) {
                    toast("No token. Please login again.");
                    return;
                }

                showLoading(true);
                positiveBtn.setEnabled(false);

                if (!isEdit) {
                    repo.createExpense(token, payload, new ExpenseRepository.ItemCallback() {
                        @Override
                        public void onSuccess(@NonNull Expense expense) {
                            if (!isAdded()) return;

                            showLoading(false);
                            dialog.dismiss();
                            load();
                        }

                        @Override
                        public void onError(int statusCode, @NonNull String message) {
                            if (!isAdded()) return;

                            showLoading(false);
                            positiveBtn.setEnabled(true);
                            toast("Create failed: " + statusCode);
                        }
                    });
                } else {
                    repo.updateExpense(token, editing.id, payload, new ExpenseRepository.ItemCallback() {
                        @Override
                        public void onSuccess(@NonNull Expense expense) {
                            if (!isAdded()) return;

                            showLoading(false);
                            dialog.dismiss();
                            load();
                        }

                        @Override
                        public void onError(int statusCode, @NonNull String message) {
                            if (!isAdded()) return;

                            showLoading(false);
                            positiveBtn.setEnabled(true);
                            toast("Update failed: " + statusCode);
                        }
                    });
                }
            });
        });

        dialog.show();
    }

    private void confirmDelete(@NonNull Expense e) {
        if (!isAdded()) return;
        if (isDeleteRunning) return;

        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Expense")
                .setMessage("Delete \"" + safeText(e.name) + "\"?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> doDelete(e))
                .show();
    }

    private void doDelete(@NonNull Expense e) {
        if (!isAdded()) return;
        if (isDeleteRunning) return;

        String token = session != null ? session.getToken() : null;
        if (TextUtils.isEmpty(token)) {
            toast("No token. Please login again.");
            return;
        }

        isDeleteRunning = true;
        showLoading(true);

        repo.deleteExpense(token, e.id, new ExpenseRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                isDeleteRunning = false;
                if (!isAdded()) return;

                showLoading(false);
                load();
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                isDeleteRunning = false;
                if (!isAdded()) return;

                showLoading(false);
                toast("Delete failed: " + statusCode);
            }
        });
    }

    private void showLoading(boolean on) {
        if (progress != null) progress.setVisibility(on ? View.VISIBLE : View.GONE);
        if (rv != null) rv.setEnabled(!on);
        setFabEnabled(!on);
        if (ivHeaderAction != null) {
            ivHeaderAction.setEnabled(!on);
            ivHeaderAction.setAlpha(on ? 0.5f : 1f);
        }
    }

    private void setFabEnabled(boolean enabled) {
        if (fabAdd == null) return;
        boolean finalEnabled = enabled && !isFormShowing && !isLoading && !isDeleteRunning;
        fabAdd.setEnabled(finalEnabled);
        fabAdd.setAlpha(finalEnabled ? 1f : 0.65f);
    }

    private void setEmpty(boolean empty) {
        if (tvEmpty != null) {
            tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            if (empty) {
                tvEmpty.setText(TextUtils.isEmpty(currentQuery) ? "No expense yet." : "No matching expense.");
            }
        }
        if (rv != null) {
            rv.setVisibility(empty ? View.GONE : View.VISIBLE);
        }
    }

    @NonNull
    private String safeText(@Nullable String value) {
        if (value == null) return "-";
        String trimmed = value.trim();
        return trimmed.isEmpty() ? "-" : trimmed;
    }

    private void toast(@NonNull String msg) {
        if (!isAdded()) return;
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        isFormShowing = false;
        isLoading = false;
        isDeleteRunning = false;

        rv = null;
        progress = null;
        tvEmpty = null;
        tvTitle = null;
        fabAdd = null;
        etSearchExpense = null;
        btnBack = null;
        ivHeaderAction = null;
    }
}