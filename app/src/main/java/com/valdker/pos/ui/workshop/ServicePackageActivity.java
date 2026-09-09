package com.valdker.pos.ui.workshop;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.valdker.pos.ModuleRegistry;
import com.valdker.pos.R;
import com.valdker.pos.SessionManager;
import com.valdker.pos.models.ServicePackageRequest;
import com.valdker.pos.models.ServicePackageResponse;
import com.valdker.pos.repositories.ServicePackageRepository;
import com.valdker.pos.utils.Toast;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ServicePackageActivity extends AppCompatActivity implements ServicePackageAdapter.Listener {
    private static final String TAG = "ServicePackagesActivity";

    private SessionManager session;
    private ServicePackageRepository repository;
    private ServicePackageAdapter adapter;

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private TextView tvError;
    private FloatingActionButton fabAdd;

    private boolean loading = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "Opening ServicePackagesActivity");

        session = new SessionManager(this);
        if (!session.isWorkshop() || !session.canAccessModule(ModuleRegistry.SERVICE_PACKAGES)) {
            Log.w(TAG, "Access denied. businessType=" + session.getBusinessType());
            Toast.makeText(this, getString(R.string.msg_permission_denied), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        repository = new ServicePackageRepository(this);
        setContentView(R.layout.activity_workshop_module_list);
        bindViews();
        setupViews();
        loadServicePackages();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (repository != null && !loading) {
            loadServicePackages();
        }
    }

    private void bindViews() {
        ImageButton btnBack = findViewById(R.id.btnBack);
        TextView tvTitle = findViewById(R.id.tvTitle);
        TextView tvSubtitle = findViewById(R.id.tvSubtitle);
        recyclerView = findViewById(R.id.rvItems);
        progressBar = findViewById(R.id.progressBar);
        tvEmpty = findViewById(R.id.tvEmpty);
        tvError = findViewById(R.id.tvError);
        fabAdd = findViewById(R.id.fabAdd);

        if (btnBack != null) btnBack.setOnClickListener(v -> finish());
        if (tvTitle != null) tvTitle.setText("Service Packages");
        if (tvSubtitle != null) tvSubtitle.setText("Workshop service package catalog");
    }

    private void setupViews() {
        adapter = new ServicePackageAdapter(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        fabAdd.setOnClickListener(v -> showServicePackageDialog(null));
    }

    private void loadServicePackages() {
        loading = true;
        showLoading();
        repository.fetchServicePackages(new ServicePackageRepository.ListCallback() {
            @Override
            public void onSuccess(@NonNull List<ServicePackageResponse> packages) {
                loading = false;
                Log.i(TAG, "Service Packages API success. count=" + packages.size());
                adapter.submit(packages);
                if (packages.isEmpty()) {
                    showEmpty();
                } else {
                    showList();
                }
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                loading = false;
                Log.w(TAG, "Service Packages API failed. status=" + statusCode + " message=" + message);
                adapter.submit(new ArrayList<>());
                showError(message);
            }
        });
    }

    private void showLoading() {
        progressBar.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        tvEmpty.setVisibility(View.GONE);
        tvError.setVisibility(View.GONE);
    }

    private void showList() {
        progressBar.setVisibility(View.GONE);
        recyclerView.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);
        tvError.setVisibility(View.GONE);
    }

    private void showEmpty() {
        Log.i(TAG, "Showing empty state: No service packages found");
        progressBar.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
        tvError.setVisibility(View.GONE);
        tvEmpty.setText("No service packages found");
        tvEmpty.setVisibility(View.VISIBLE);
    }

    private void showError(@NonNull String message) {
        progressBar.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
        tvEmpty.setVisibility(View.GONE);
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
    }

    @Override
    public void onEdit(@NonNull ServicePackageResponse item) {
        showServicePackageDialog(item);
    }

    @Override
    public void onDelete(@NonNull ServicePackageResponse item) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Service Package")
                .setMessage("Delete \"" + item.name + "\"?")
                .setPositiveButton(getString(R.string.action_delete), (dialog, which) -> deleteServicePackage(item))
                .setNegativeButton(getString(R.string.action_cancel), null)
                .show();
    }

    private void showServicePackageDialog(@Nullable ServicePackageResponse item) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_service_package_form, null, false);
        TextView title = view.findViewById(R.id.tvDialogTitle);
        EditText etName = view.findViewById(R.id.etName);
        EditText etPrice = view.findViewById(R.id.etPrice);
        EditText etDuration = view.findViewById(R.id.etDuration);
        EditText etDescription = view.findViewById(R.id.etDescription);
        Switch swActive = view.findViewById(R.id.swActive);

        boolean isEdit = item != null;
        title.setText(isEdit ? "Edit Service Package" : "Add Service Package");
        if (isEdit) {
            etName.setText(item.name);
            etPrice.setText(item.price);
            etDuration.setText(String.valueOf(item.durationMinutes));
            etDescription.setText(item.description);
            swActive.setChecked(item.isActive);
        } else {
            etDuration.setText("0");
            swActive.setChecked(true);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .setPositiveButton(isEdit ? getString(R.string.action_update) : getString(R.string.action_create), null)
                .setNegativeButton(getString(R.string.action_cancel), null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = text(etName);
            String price = text(etPrice);
            String durationText = text(etDuration);
            String description = text(etDescription);

            if (TextUtils.isEmpty(name)) {
                etName.setError(getString(R.string.error_required));
                etName.requestFocus();
                return;
            }
            if (TextUtils.isEmpty(price)) {
                etPrice.setError(getString(R.string.error_required));
                etPrice.requestFocus();
                return;
            }
            if (!isValidPrice(price)) {
                etPrice.setError("Invalid price");
                etPrice.requestFocus();
                return;
            }

            int duration = parseDuration(durationText);
            ServicePackageRequest request = new ServicePackageRequest(
                    name,
                    description,
                    normalizePrice(price),
                    duration,
                    swActive.isChecked()
            );
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            if (isEdit) {
                updateServicePackage(item.id, request, dialog);
            } else {
                createServicePackage(request, dialog);
            }
        }));

        dialog.show();
    }

    private void createServicePackage(@NonNull ServicePackageRequest request, @NonNull AlertDialog dialog) {
        repository.create(request, new ServicePackageRepository.ItemCallback() {
            @Override
            public void onSuccess(@NonNull ServicePackageResponse item) {
                Log.i(TAG, "Service package created id=" + item.id);
                dialog.dismiss();
                Toast.makeText(ServicePackageActivity.this, "Service package created", Toast.LENGTH_SHORT).show();
                loadServicePackages();
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                Log.w(TAG, "Create service package failed. status=" + statusCode + " message=" + message);
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                Toast.makeText(ServicePackageActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void updateServicePackage(int id, @NonNull ServicePackageRequest request, @NonNull AlertDialog dialog) {
        repository.update(id, request, new ServicePackageRepository.ItemCallback() {
            @Override
            public void onSuccess(@NonNull ServicePackageResponse item) {
                Log.i(TAG, "Service package updated id=" + item.id);
                dialog.dismiss();
                Toast.makeText(ServicePackageActivity.this, "Service package updated", Toast.LENGTH_SHORT).show();
                loadServicePackages();
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                Log.w(TAG, "Update service package failed. status=" + statusCode + " message=" + message);
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                Toast.makeText(ServicePackageActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void deleteServicePackage(@NonNull ServicePackageResponse item) {
        repository.delete(item.id, new ServicePackageRepository.DeleteCallback() {
            @Override
            public void onSuccess() {
                Log.i(TAG, "Service package deleted id=" + item.id);
                Toast.makeText(ServicePackageActivity.this, "Service package deleted", Toast.LENGTH_SHORT).show();
                loadServicePackages();
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                Log.w(TAG, "Delete service package failed. status=" + statusCode + " message=" + message);
                Toast.makeText(ServicePackageActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    @NonNull
    private static String text(@NonNull EditText editText) {
        return editText.getText() != null ? editText.getText().toString().trim() : "";
    }

    private static int parseDuration(@NonNull String value) {
        if (value.trim().isEmpty()) return 0;
        try {
            return Math.max(0, Integer.parseInt(value.trim()));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static boolean isValidPrice(@NonNull String value) {
        try {
            return new BigDecimal(value.trim()).compareTo(BigDecimal.ZERO) >= 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    @NonNull
    private static String normalizePrice(@NonNull String value) {
        return new BigDecimal(value.trim()).toPlainString();
    }
}
