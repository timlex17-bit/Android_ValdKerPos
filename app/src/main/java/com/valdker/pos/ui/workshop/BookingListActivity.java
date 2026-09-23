package com.valdker.pos.ui.workshop;

import androidx.annotation.NonNull;

import com.valdker.pos.ModuleRegistry;
import com.valdker.pos.R;

import org.json.JSONObject;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class BookingListActivity extends WorkshopSimpleListActivity {

    private static final String[] STATUS_VALUES = {"PENDING", "CONFIRMED", "DONE", "CANCELLED"};

    @NonNull
    @Override
    protected String moduleKey() {
        return ModuleRegistry.BOOKINGS;
    }

    @NonNull
    @Override
    protected String endpoint() {
        return "api/workshop/bookings/";
    }

    @NonNull
    @Override
    protected String screenTitle() {
        return "Bookings";
    }

    @NonNull
    @Override
    protected String emptyMessage() {
        return "No bookings found";
    }

    @NonNull
    @Override
    protected List<FormField> formFields() {
        String[] statusLabels = {
                getString(R.string.workshop_status_pending),
                getString(R.string.workshop_status_confirmed),
                getString(R.string.workshop_status_done),
                getString(R.string.workshop_status_cancelled)
        };
        return Arrays.asList(
                FormField.relation("customer", getString(R.string.workshop_label_customer), true, "customer"),
                FormField.relation("vehicle", getString(R.string.workshop_label_vehicle), false,
                        "vehicle", "api/workshop/vehicles/"),
                FormField.date("booking_date", getString(R.string.workshop_label_booking_date), true),
                FormField.time("booking_time", getString(R.string.workshop_label_booking_time), true),
                FormField.choice("status", getString(R.string.workshop_label_status), false,
                        STATUS_VALUES, statusLabels, "PENDING"),
                FormField.multiline("notes", "Notes", false)
        );
    }

    @NonNull
    @Override
    protected String describeRow(@NonNull JSONObject item) {
        String when = (item.optString("booking_date", "").trim() + " "
                + shortTime(item.optString("booking_time", ""))).trim();
        StringBuilder sb = new StringBuilder(when);
        String status = statusLabel(item.optString("status", ""));
        if (!status.isEmpty()) {
            if (sb.length() > 0) sb.append(" - ");
            sb.append(status);
        }
        String vehicle = item.optString("vehicle_plate_number", "").trim();
        if (vehicle.isEmpty()) vehicle = item.optString("customer_name", "").trim();
        if (!vehicle.isEmpty()) {
            if (sb.length() > 0) sb.append(" - ");
            sb.append(vehicle);
        }
        return sb.toString();
    }

    /** Server mengirim HH:MM:SS; detiknya tidak berguna bagi pembaca daftar. */
    @NonNull
    private static String shortTime(@NonNull String raw) {
        String[] parts = raw.trim().split(":");
        return parts.length >= 2 ? parts[0] + ":" + parts[1] : raw.trim();
    }

    @NonNull
    private String statusLabel(@NonNull String raw) {
        switch (raw.trim().toUpperCase(Locale.US)) {
            case "PENDING":
                return getString(R.string.workshop_status_pending);
            case "CONFIRMED":
                return getString(R.string.workshop_status_confirmed);
            case "DONE":
                return getString(R.string.workshop_status_done);
            case "CANCELLED":
                return getString(R.string.workshop_status_cancelled);
            default:
                return raw.trim();
        }
    }
}
