package com.valdker.pos.ui.workshop;

import androidx.annotation.NonNull;

import com.valdker.pos.ModuleRegistry;
import com.valdker.pos.R;

import org.json.JSONObject;

import java.util.Arrays;
import java.util.List;

public class ServiceHistoryActivity extends WorkshopSimpleListActivity {
    @NonNull
    @Override
    protected String moduleKey() {
        return ModuleRegistry.SERVICE_HISTORY;
    }

    @NonNull
    @Override
    protected String endpoint() {
        return "api/workshop/service-history/";
    }

    @NonNull
    @Override
    protected String screenTitle() {
        return "Service History";
    }

    @NonNull
    @Override
    protected String emptyMessage() {
        return "No service history found";
    }

    @NonNull
    @Override
    protected List<FormField> formFields() {
        return Arrays.asList(
                FormField.relation("work_order", getString(R.string.workshop_label_work_order), true,
                        "work order", "api/workshop/work-orders/"),
                FormField.relation("vehicle", getString(R.string.workshop_label_vehicle), true,
                        "vehicle", "api/workshop/vehicles/"),
                FormField.relation("customer", getString(R.string.workshop_label_customer), true, "customer"),
                FormField.multiline("notes", "Notes", true),
                FormField.date("service_date", getString(R.string.workshop_label_service_date), true),
                FormField.decimal("total_amount", "Total Amount", false, "0")
        );
    }

    @NonNull
    @Override
    protected String describeRow(@NonNull JSONObject item) {
        StringBuilder sb = new StringBuilder(item.optString("service_date", "").trim());
        String vehicle = item.optString("vehicle_plate_number", "").trim();
        if (vehicle.isEmpty()) vehicle = item.optString("customer_name", "").trim();
        if (!vehicle.isEmpty()) {
            if (sb.length() > 0) sb.append(" - ");
            sb.append(vehicle);
        }
        String notes = item.optString("notes", "").trim();
        if (!notes.isEmpty()) {
            if (notes.length() > 60) notes = notes.substring(0, 60) + "...";
            if (sb.length() > 0) sb.append(" - ");
            sb.append(notes);
        }
        return sb.toString();
    }
}
