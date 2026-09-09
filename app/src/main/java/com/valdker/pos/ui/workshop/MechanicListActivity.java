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
import com.valdker.pos.models.MechanicRequest;
import com.valdker.pos.models.MechanicResponse;
import com.valdker.pos.repositories.MechanicRepository;
import com.valdker.pos.utils.Toast;

import java.util.ArrayList;
import java.util.List;

public class MechanicListActivity extends AppCompatActivity implements MechanicAdapter.Listener {
    private static final String TAG = "MechanicsActivity";

    private SessionManager session;
    private MechanicRepository repository;
    private MechanicAdapter adapter;

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private TextView tvError;
    private FloatingActionButton fabAdd;

    private boolean loading = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "Opening MechanicsActivity");

        session = new SessionManager(this);
        if (!session.isWorkshop() || !session.canAccessModule(ModuleRegistry.MECHANICS)) {
            Log.w(TAG, "Access denied. businessType=" + session.getBusinessType());
            Toast.makeText(this, getString(R.string.msg_permission_denied), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        repository = new MechanicRepository(this);
        setContentView(R.layout.activity_workshop_module_list);
        bindViews();
        setupViews();
        loadMechanics();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (repository != null && !loading) {
            loadMechanics();
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
        if (tvTitle != null) tvTitle.setText("Mechanics");
        if (tvSubtitle != null) tvSubtitle.setText("Workshop mechanic directory");
    }

    private void setupViews() {
        adapter = new MechanicAdapter(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        fabAdd.setOnClickListener(v -> showMechanicDialog(null));
    }

    private void loadMechanics() {
        loading = true;
        showLoading();
        repository.fetchMechanics(new MechanicRepository.ListCallback() {
            @Override
            public void onSuccess(@NonNull List<MechanicResponse> mechanics) {
                loading = false;
                Log.i(TAG, "Mechanics API success. count=" + mechanics.size());
                adapter.submit(mechanics);
                if (mechanics.isEmpty()) {
                    showEmpty();
                } else {
                    showList();
                }
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                loading = false;
                Log.w(TAG, "Mechanics API failed. status=" + statusCode + " message=" + message);
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
        Log.i(TAG, "Showing empty state: No mechanics found");
        progressBar.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
        tvError.setVisibility(View.GONE);
        tvEmpty.setText("No mechanics found");
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
    public void onEdit(@NonNull MechanicResponse mechanic) {
        showMechanicDialog(mechanic);
    }

    @Override
    public void onDelete(@NonNull MechanicResponse mechanic) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Mechanic")
                .setMessage("Delete \"" + mechanic.name + "\"?")
                .setPositiveButton(getString(R.string.action_delete), (dialog, which) -> deleteMechanic(mechanic))
                .setNegativeButton(getString(R.string.action_cancel), null)
                .show();
    }

    private void showMechanicDialog(@Nullable MechanicResponse mechanic) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_mechanic_form, null, false);
        TextView title = view.findViewById(R.id.tvDialogTitle);
        EditText etName = view.findViewById(R.id.etName);
        EditText etPhone = view.findViewById(R.id.etPhone);
        EditText etSpecialty = view.findViewById(R.id.etSpecialty);
        Switch swActive = view.findViewById(R.id.swActive);

        boolean isEdit = mechanic != null;
        title.setText(isEdit ? "Edit Mechanic" : "Add Mechanic");
        if (isEdit) {
            etName.setText(mechanic.name);
            etPhone.setText(mechanic.phone);
            etSpecialty.setText(mechanic.specialty);
            swActive.setChecked(mechanic.isActive);
        } else {
            swActive.setChecked(true);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .setPositiveButton(isEdit ? getString(R.string.action_update) : getString(R.string.action_create), null)
                .setNegativeButton(getString(R.string.action_cancel), null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = etName.getText() != null ? etName.getText().toString().trim() : "";
            String phone = etPhone.getText() != null ? etPhone.getText().toString().trim() : "";
            String specialty = etSpecialty.getText() != null ? etSpecialty.getText().toString().trim() : "";

            if (TextUtils.isEmpty(name)) {
                etName.setError(getString(R.string.error_required));
                etName.requestFocus();
                return;
            }

            MechanicRequest request = new MechanicRequest(name, phone, specialty, swActive.isChecked());
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            if (isEdit) {
                updateMechanic(mechanic.id, request, dialog);
            } else {
                createMechanic(request, dialog);
            }
        }));

        dialog.show();
    }

    private void createMechanic(@NonNull MechanicRequest request, @NonNull AlertDialog dialog) {
        repository.create(request, new MechanicRepository.ItemCallback() {
            @Override
            public void onSuccess(@NonNull MechanicResponse mechanic) {
                Log.i(TAG, "Mechanic created id=" + mechanic.id);
                dialog.dismiss();
                Toast.makeText(MechanicListActivity.this, "Mechanic created", Toast.LENGTH_SHORT).show();
                loadMechanics();
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                Log.w(TAG, "Create mechanic failed. status=" + statusCode + " message=" + message);
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                Toast.makeText(MechanicListActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void updateMechanic(int id, @NonNull MechanicRequest request, @NonNull AlertDialog dialog) {
        repository.update(id, request, new MechanicRepository.ItemCallback() {
            @Override
            public void onSuccess(@NonNull MechanicResponse mechanic) {
                Log.i(TAG, "Mechanic updated id=" + mechanic.id);
                dialog.dismiss();
                Toast.makeText(MechanicListActivity.this, "Mechanic updated", Toast.LENGTH_SHORT).show();
                loadMechanics();
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                Log.w(TAG, "Update mechanic failed. status=" + statusCode + " message=" + message);
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                Toast.makeText(MechanicListActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void deleteMechanic(@NonNull MechanicResponse mechanic) {
        repository.delete(mechanic.id, new MechanicRepository.DeleteCallback() {
            @Override
            public void onSuccess() {
                Log.i(TAG, "Mechanic deleted id=" + mechanic.id);
                Toast.makeText(MechanicListActivity.this, "Mechanic deleted", Toast.LENGTH_SHORT).show();
                loadMechanics();
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                Log.w(TAG, "Delete mechanic failed. status=" + statusCode + " message=" + message);
                Toast.makeText(MechanicListActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }
}
