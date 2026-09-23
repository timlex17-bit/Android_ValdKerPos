package com.valdker.pos.ui.workshop;

import androidx.annotation.NonNull;

import com.valdker.pos.ModuleRegistry;
import com.valdker.pos.R;

import org.json.JSONObject;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class WorkOrderListActivity extends WorkshopSimpleListActivity {

    private static final String[] STATUS_VALUES = {"OPEN", "IN_PROGRESS", "DONE", "CANCELLED"};

    @NonNull
    @Override
    protected String moduleKey() {
        return ModuleRegistry.WORK_ORDERS;
    }

    @NonNull
    @Override
    protected String endpoint() {
        return "api/workshop/work-orders/";
    }

    @NonNull
    @Override
    protected String screenTitle() {
        return "Work Orders";
    }

    @NonNull
    @Override
    protected String emptyMessage() {
        return "No work orders found";
    }

    @NonNull
    @Override
    protected List<FormField> formFields() {
        String[] statusLabels = {
                getString(R.string.workshop_status_open),
                getString(R.string.workshop_status_in_progress),
                getString(R.string.workshop_status_done),
                getString(R.string.workshop_status_cancelled)
        };
        return Arrays.asList(
                FormField.relation("customer", getString(R.string.workshop_label_customer), true, "customer"),
                FormField.relation("vehicle", getString(R.string.workshop_label_vehicle), true,
                        "vehicle", "api/workshop/vehicles/"),
                FormField.relation("mechanic", getString(R.string.workshop_label_mechanic), false,
                        "mechanic", "api/workshop/mechanics/"),
                FormField.multiline("complaint", "Complaint", true),
                FormField.multiline("diagnosis", "Diagnosis", false),
                FormField.choice("status", getString(R.string.workshop_label_status), false,
                        STATUS_VALUES, statusLabels, "OPEN"),
                FormField.decimal("total_amount", "Total Amount", false, "0")
        );
    }

    @NonNull
    @Override
    protected String describeRow(@NonNull JSONObject item) {
        StringBuilder sb = new StringBuilder(statusLabel(item.optString("status", "")));
        String plate = item.optString("vehicle_plate_number", "").trim();
        if (plate.isEmpty()) plate = item.optString("customer_name", "").trim();
        if (!plate.isEmpty()) {
            if (sb.length() > 0) sb.append(" - ");
            sb.append(plate);
        }
        String mechanic = item.optString("mechanic_name", "").trim();
        if (!mechanic.isEmpty()) {
            if (sb.length() > 0) sb.append(" - ");
            sb.append(mechanic);
        }
        return sb.toString();
    }

    @NonNull
    private String statusLabel(@NonNull String raw) {
        switch (raw.trim().toUpperCase(Locale.US)) {
            case "OPEN":
                return getString(R.string.workshop_status_open);
            case "IN_PROGRESS":
                return getString(R.string.workshop_status_in_progress);
            case "DONE":
                return getString(R.string.workshop_status_done);
            case "CANCELLED":
                return getString(R.string.workshop_status_cancelled);
            default:
                return raw.trim();
        }
    }
}
