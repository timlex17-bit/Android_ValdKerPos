package com.valdker.pos.ui.workshop;

import androidx.annotation.NonNull;

import com.valdker.pos.ModuleRegistry;

import java.util.Arrays;
import java.util.List;

public class WorkOrderListActivity extends WorkshopSimpleListActivity {
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
        return Arrays.asList(
                FormField.relation("customer", "Customer", true, "customer"),
                FormField.relation("vehicle", "Vehicle ID", true, "vehicle"),
                FormField.relation("mechanic", "Mechanic ID", false, "mechanic"),
                FormField.multiline("complaint", "Complaint", true),
                FormField.multiline("diagnosis", "Diagnosis", false),
                FormField.text("status", "Status", false).defaultValue("OPEN"),
                FormField.decimal("total_amount", "Total Amount", false, "0")
        );
    }
}
