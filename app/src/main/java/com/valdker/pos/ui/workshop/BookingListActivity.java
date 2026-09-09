package com.valdker.pos.ui.workshop;

import androidx.annotation.NonNull;

import com.valdker.pos.ModuleRegistry;

import java.util.Arrays;
import java.util.List;

public class BookingListActivity extends WorkshopSimpleListActivity {
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
        return Arrays.asList(
                FormField.relation("customer", "Customer", true, "customer"),
                FormField.relation("vehicle", "Vehicle ID", false, "vehicle"),
                FormField.date("booking_date", "Booking Date (YYYY-MM-DD)", true),
                FormField.time("booking_time", "Booking Time (HH:MM)", true),
                FormField.text("status", "Status", false).defaultValue("PENDING"),
                FormField.multiline("notes", "Notes", false)
        );
    }
}
