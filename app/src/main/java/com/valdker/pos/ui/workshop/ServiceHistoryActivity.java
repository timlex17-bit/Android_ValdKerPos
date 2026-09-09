package com.valdker.pos.ui.workshop;

import androidx.annotation.NonNull;

import com.valdker.pos.ModuleRegistry;

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
                FormField.relation("work_order", "Work Order ID", true, "work order"),
                FormField.relation("vehicle", "Vehicle ID", true, "vehicle"),
                FormField.relation("customer", "Customer ID", true, "customer"),
                FormField.multiline("notes", "Notes", true),
                FormField.date("service_date", "Service Date (YYYY-MM-DD)", true),
                FormField.decimal("total_amount", "Total Amount", false, "0")
        );
    }
}
