package com.valdker.pos.ui.workshop;

import androidx.annotation.NonNull;

import com.valdker.pos.ModuleRegistry;

import java.util.Arrays;
import java.util.List;

public class VehicleListActivity extends WorkshopSimpleListActivity {
    @NonNull
    @Override
    protected String moduleKey() {
        return ModuleRegistry.VEHICLES;
    }

    @NonNull
    @Override
    protected String endpoint() {
        return "api/workshop/vehicles/";
    }

    @NonNull
    @Override
    protected String screenTitle() {
        return "Vehicles";
    }

    @NonNull
    @Override
    protected String emptyMessage() {
        return "No vehicles found";
    }

    @NonNull
    @Override
    protected List<FormField> formFields() {
        return Arrays.asList(
                FormField.relation("customer", "Customer", false, "customer"),
                FormField.text("plate_number", "Plate Number", true),
                FormField.text("vehicle_type", "Vehicle Type (CAR or MOTORCYCLE)", true).defaultValue("CAR"),
                FormField.text("brand", "Brand", false),
                FormField.text("model", "Model", false),
                FormField.integer("year", "Year", false, ""),
                FormField.text("color", "Color", false)
        );
    }
}
