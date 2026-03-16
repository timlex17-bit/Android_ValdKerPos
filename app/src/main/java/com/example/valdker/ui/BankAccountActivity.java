package com.example.valdker.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.example.valdker.R;
import com.example.valdker.SessionManager;
import com.example.valdker.adapters.BankAccountAdapter;
import com.example.valdker.models.BankAccount;
import com.example.valdker.network.ApiClient;
import com.example.valdker.network.ApiConfig;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class BankAccountActivity extends AppCompatActivity implements BankAccountAdapter.OnBankActionListener {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private Button btnAddBank;
    private ImageButton btnBack;

    private SessionManager sessionManager;
    private final ArrayList<BankAccount> bankList = new ArrayList<>();
    private BankAccountAdapter adapter;

    private static final String ENDPOINT_BANK_ACCOUNTS = "api/bank-accounts/";
    private static final int TIMEOUT_MS = 20000;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bank_account);

        sessionManager = new SessionManager(this);

        String role = sessionManager.getRole();
        if (role == null || !role.equalsIgnoreCase("owner")) {
            Toast.makeText(this, "Na'in de'it maka bele asesu ba Konta Bankária sira", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        initViews();
        setupRecycler();
        setupActions();
        loadBankAccounts();
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recyclerBankAccounts);
        progressBar = findViewById(R.id.progressBar);
        tvEmpty = findViewById(R.id.tvEmpty);
        btnAddBank = findViewById(R.id.btnAddBank);
        btnBack = findViewById(R.id.btnBack);
    }

    private void setupRecycler() {
        adapter = new BankAccountAdapter(this, bankList, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void setupActions() {
        btnBack.setOnClickListener(v -> finish());
        btnAddBank.setOnClickListener(v -> showBankDialog(null));
    }

    private void loadBankAccounts() {
        progressBar.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);

        String url = ApiConfig.url(sessionManager, ENDPOINT_BANK_ACCOUNTS);

        JsonArrayRequest request = new JsonArrayRequest(
                Request.Method.GET,
                url,
                null,
                response -> {
                    progressBar.setVisibility(View.GONE);
                    bankList.clear();

                    for (int i = 0; i < response.length(); i++) {
                        JSONObject obj = response.optJSONObject(i);
                        if (obj != null) {
                            bankList.add(BankAccount.fromJson(obj));
                        }
                    }

                    adapter.notifyDataSetChanged();
                    tvEmpty.setVisibility(bankList.isEmpty() ? View.VISIBLE : View.GONE);
                },
                error -> {
                    progressBar.setVisibility(View.GONE);
                    tvEmpty.setVisibility(bankList.isEmpty() ? View.VISIBLE : View.GONE);
                    Toast.makeText(this, parseVolleyError(error), Toast.LENGTH_LONG).show();
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                return buildHeaders();
            }
        };

        request.setRetryPolicy(new DefaultRetryPolicy(
                TIMEOUT_MS,
                1,
                1.0f
        ));

        ApiClient.getInstance(this).add(request);
    }

    private void createBankAccount(JSONObject body, Dialog dialog) {
        String url = ApiConfig.url(sessionManager, ENDPOINT_BANK_ACCOUNTS);

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                url,
                body,
                response -> {
                    Toast.makeText(this, "Konta bankária aumenta ho susesu", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    loadBankAccounts();
                },
                error -> Toast.makeText(this, parseVolleyError(error), Toast.LENGTH_LONG).show()
        ) {
            @Override
            public Map<String, String> getHeaders() {
                return buildHeaders();
            }
        };

        request.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, 1, 1.0f));
        ApiClient.getInstance(this).add(request);
    }

    private void updateBankAccount(int bankId, JSONObject body, Dialog dialog) {
        String url = ApiConfig.url(sessionManager, ENDPOINT_BANK_ACCOUNTS + bankId + "/");

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.PATCH,
                url,
                body,
                response -> {
                    Toast.makeText(this, "Konta bankária atualiza ho susesu", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    loadBankAccounts();
                },
                error -> Toast.makeText(this, parseVolleyError(error), Toast.LENGTH_LONG).show()
        ) {
            @Override
            public Map<String, String> getHeaders() {
                return buildHeaders();
            }
        };

        request.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, 1, 1.0f));
        ApiClient.getInstance(this).add(request);
    }

    private void deleteBankAccount(BankAccount item) {
        new AlertDialog.Builder(this)
                .setTitle("Apaga Konta Bankária")
                .setMessage("Ita-boot iha serteza katak ita-boot hakarak atu hamoos? \"" + item.getBankName() + " - " + item.getName() + "\"?")
                .setPositiveButton("Hapus", (dialog, which) -> {
                    String url = ApiConfig.url(sessionManager, ENDPOINT_BANK_ACCOUNTS + item.getId() + "/");

                    JsonObjectRequest request = new JsonObjectRequest(
                            Request.Method.DELETE,
                            url,
                            null,
                            response -> {
                                Toast.makeText(this, "Konta bankária hetan eliminasaun ho susesu", Toast.LENGTH_SHORT).show();
                                loadBankAccounts();
                            },
                            error -> Toast.makeText(this, parseVolleyError(error), Toast.LENGTH_LONG).show()
                    ) {
                        @Override
                        public Map<String, String> getHeaders() {
                            return buildHeaders();
                        }
                    };

                    request.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, 1, 1.0f));
                    ApiClient.getInstance(this).add(request);
                })
                .setNegativeButton("Kansela", null)
                .show();
    }

    private void showBankDialog(@Nullable BankAccount item) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_bank_account, null, false);
        dialog.setContentView(view);

        TextView tvDialogTitle = view.findViewById(R.id.tvDialogTitle);
        EditText etName = view.findViewById(R.id.etName);
        EditText etBankName = view.findViewById(R.id.etBankName);
        EditText etAccountNumber = view.findViewById(R.id.etAccountNumber);
        EditText etAccountHolder = view.findViewById(R.id.etAccountHolder);
        Spinner spinnerAccountType = view.findViewById(R.id.spinnerAccountType);
        EditText etOpeningBalance = view.findViewById(R.id.etOpeningBalance);
        Switch switchActive = view.findViewById(R.id.switchActive);
        EditText etNote = view.findViewById(R.id.etNote);
        Button btnCancel = view.findViewById(R.id.btnCancel);
        Button btnSave = view.findViewById(R.id.btnSave);

        String[] accountTypes = {"BANKU", "EWALLET", "QRIS"};
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                accountTypes
        );
        spinnerAccountType.setAdapter(typeAdapter);

        boolean isEdit = item != null;
        tvDialogTitle.setText(isEdit ? "Edita Konta Bankária" : "Aumenta Konta Bankária");

        if (isEdit) {
            etName.setText(item.getName());
            etBankName.setText(item.getBankName());
            etAccountNumber.setText(item.getAccountNumber());
            etAccountHolder.setText(item.getAccountHolder());
            etOpeningBalance.setText(item.getOpeningBalance());
            switchActive.setChecked(item.isActive());
            etNote.setText(item.getNote());

            for (int i = 0; i < accountTypes.length; i++) {
                if (accountTypes[i].equalsIgnoreCase(item.getAccountType())) {
                    spinnerAccountType.setSelection(i);
                    break;
                }
            }

            etOpeningBalance.setEnabled(false);
        } else {
            switchActive.setChecked(true);
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String bankName = etBankName.getText().toString().trim();
            String accountNumber = etAccountNumber.getText().toString().trim();
            String accountHolder = etAccountHolder.getText().toString().trim();
            String accountType = spinnerAccountType.getSelectedItem().toString();
            String openingBalance = etOpeningBalance.getText().toString().trim();
            boolean isActive = switchActive.isChecked();
            String note = etNote.getText().toString().trim();

            if (TextUtils.isEmpty(name)) {
                etName.setError("Presiza naran");
                etName.requestFocus();
                return;
            }

            if (TextUtils.isEmpty(bankName)) {
                etBankName.setError("Naran banku presiza");
                etBankName.requestFocus();
                return;
            }

            if (!isEdit && TextUtils.isEmpty(openingBalance)) {
                openingBalance = "0.00";
            }

            try {
                JSONObject body = new JSONObject();
                body.put("name", name);
                body.put("bank_name", bankName);
                body.put("account_number", accountNumber);
                body.put("account_holder", accountHolder);
                body.put("account_type", accountType);
                body.put("is_active", isActive);
                body.put("note", note);

                if (!isEdit) {
                    body.put("opening_balance", openingBalance);
                    createBankAccount(body, dialog);
                } else {
                    updateBankAccount(item.getId(), body, dialog);
                }

            } catch (JSONException e) {
                Toast.makeText(this, "La konsege kria pedidu", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.95),
                    RecyclerView.LayoutParams.WRAP_CONTENT
            );
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
    }

    private Map<String, String> buildHeaders() {
        Map<String, String> headers = new HashMap<>();
        String token = sessionManager.getToken();
        headers.put("Authorization", "Token " + token);
        headers.put("Content-Type", "application/json");
        return headers;
    }

    private String parseVolleyError(com.android.volley.VolleyError error) {
        try {
            NetworkResponse response = error.networkResponse;
            if (response != null && response.data != null) {
                String body = new String(response.data, StandardCharsets.UTF_8);
                return body;
            }
        } catch (Exception ignored) {
        }

        if (error.getMessage() != null) {
            return error.getMessage();
        }

        return "Akontese erru iha rede";
    }

    @Override
    public void onEdit(BankAccount item) {
        showBankDialog(item);
    }

    @Override
    public void onDelete(BankAccount item) {
        deleteBankAccount(item);
    }

    @Override
    public void onViewLedger(BankAccount item) {
        Toast.makeText(this, "Nanti kita lanjut halaman Bank Ledger: " + item.getBankName(), Toast.LENGTH_SHORT).show();
    }
}