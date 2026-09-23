package com.valdker.pos.ui.workshop;

import androidx.annotation.NonNull;

import com.valdker.pos.ModuleRegistry;
import com.valdker.pos.R;

import org.json.JSONObject;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class VehicleListActivity extends WorkshopSimpleListActivity {

    /** Sama persis dengan pilihan di model Django; jangan diketik bebas. */
    private static final String[] TYPE_VALUES = {"CAR", "MOTORCYCLE"};

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
        String[] typeLabels = {
                getString(R.string.workshop_type_car),
                getString(R.string.workshop_type_motorcycle)
        };
        return Arrays.asList(
                FormField.relation("customer", getString(R.string.workshop_label_customer), false, "customer"),
                FormField.text("plate_number", "Plate Number", true),
                FormField.choice("vehicle_type", getString(R.string.workshop_label_vehicle_type), true,
                        TYPE_VALUES, typeLabels, "CAR"),
                FormField.text("brand", "Brand", false),
                FormField.text("model", "Model", false),
                FormField.integer("year", "Year", false, ""),
                FormField.text("color", "Color", false)
        );
    }

    /**
     * Kartu kendaraan dulu menampilkan seluruh objek JSON dari Django, karena
     * serializer kendaraan tidak memuat satu pun field yang ditebak subjudul
     * baku. Di sini isinya ditulis sebagai kalimat: jenis, merek, model, tahun,
     * warna, lalu pemiliknya.
     */
    @NonNull
    @Override
    protected String describeRow(@NonNull JSONObject item) {
        StringBuilder spec = new StringBuilder();
        append(spec, typeLabel(item.optString("vehicle_type", "")));
        String make = (item.optString("brand", "").trim() + " "
                + item.optString("model", "").trim()).trim();
        append(spec, make);
        int year = item.optInt("year", 0);
        if (year > 0) append(spec, String.valueOf(year));
        append(spec, item.optString("color", "").trim());

        String owner = item.optString("customer_name", "").trim();
        if (owner.isEmpty()) owner = getString(R.string.workshop_vehicle_no_owner);

        return spec.length() == 0 ? owner : spec + " - " + owner;
    }

    @NonNull
    private String typeLabel(@NonNull String raw) {
        switch (raw.trim().toUpperCase(Locale.US)) {
            case "CAR":
                return getString(R.string.workshop_type_car);
            case "MOTORCYCLE":
                return getString(R.string.workshop_type_motorcycle);
            default:
                return raw.trim();
        }
    }

    private static void append(@NonNull StringBuilder sb, @NonNull String part) {
        if (part.trim().isEmpty()) return;
        if (sb.length() > 0) sb.append(" - ");
        sb.append(part.trim());
    }
}
