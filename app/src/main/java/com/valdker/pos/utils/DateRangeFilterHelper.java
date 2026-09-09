package com.valdker.pos.utils;

import android.app.DatePickerDialog;
import android.graphics.Color;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.Calendar;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DateRangeFilterHelper {
    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");

    @NonNull
    private final Fragment fragment;
    @Nullable
    private final ImageView button;
    @NonNull
    private final Runnable onChanged;

    private String startDate = "";
    private String endDate = "";

    public DateRangeFilterHelper(@NonNull Fragment fragment,
                                 @Nullable ImageView button,
                                 @NonNull Runnable onChanged) {
        this.fragment = fragment;
        this.button = button;
        this.onChanged = onChanged;
        setupButton();
        updateIcon();
    }

    public boolean isActive() {
        return !startDate.isEmpty() || !endDate.isEmpty();
    }

    public boolean matches(@Nullable String... dateCandidates) {
        if (!isActive()) return true;

        String date = firstIsoDate(dateCandidates);
        if (date.isEmpty()) return false;

        if (!startDate.isEmpty() && date.compareTo(startDate) < 0) return false;
        return endDate.isEmpty() || date.compareTo(endDate) <= 0;
    }

    private void setupButton() {
        if (button == null) return;

        button.setOnClickListener(v -> showDateRangePicker());
        button.setOnLongClickListener(v -> {
            clear();
            return true;
        });
    }

    private void showDateRangePicker() {
        if (!fragment.isAdded()) return;

        Calendar initialStart = calendarFromDate(startDate);
        DatePickerDialog startDialog = new DatePickerDialog(
                fragment.requireContext(),
                (view, year, month, day) -> {
                    String selectedStart = formatDate(year, month, day);
                    Calendar initialEnd = calendarFromDate(endDate.isEmpty() ? selectedStart : endDate);
                    DatePickerDialog endDialog = new DatePickerDialog(
                            fragment.requireContext(),
                            (endView, endYear, endMonth, endDay) ->
                                    applyRange(selectedStart, formatDate(endYear, endMonth, endDay)),
                            initialEnd.get(Calendar.YEAR),
                            initialEnd.get(Calendar.MONTH),
                            initialEnd.get(Calendar.DAY_OF_MONTH)
                    );
                    endDialog.setTitle("Select end date");
                    endDialog.show();
                },
                initialStart.get(Calendar.YEAR),
                initialStart.get(Calendar.MONTH),
                initialStart.get(Calendar.DAY_OF_MONTH)
        );
        startDialog.setTitle("Select start date");
        startDialog.show();
    }

    private void applyRange(@NonNull String start, @NonNull String end) {
        if (start.compareTo(end) > 0) {
            startDate = end;
            endDate = start;
        } else {
            startDate = start;
            endDate = end;
        }

        updateIcon();
        onChanged.run();

        if (fragment.isAdded()) {
            Toast.makeText(
                    fragment.requireContext(),
                    "Date range: " + startDate + " - " + endDate,
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void clear() {
        boolean hadFilter = isActive();
        startDate = "";
        endDate = "";
        updateIcon();
        onChanged.run();

        if (hadFilter && fragment.isAdded()) {
            Toast.makeText(fragment.requireContext(), "Date range cleared", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateIcon() {
        if (button == null) return;
        boolean active = isActive();
        button.setColorFilter(Color.parseColor(active ? "#FACC15" : "#FFFFFF"));
        button.setAlpha(active ? 1f : 0.9f);
    }

    @NonNull
    private static Calendar calendarFromDate(@Nullable String date) {
        Calendar calendar = Calendar.getInstance();
        if (date == null || !date.matches("^\\d{4}-\\d{2}-\\d{2}$")) return calendar;

        try {
            calendar.set(Calendar.YEAR, Integer.parseInt(date.substring(0, 4)));
            calendar.set(Calendar.MONTH, Integer.parseInt(date.substring(5, 7)) - 1);
            calendar.set(Calendar.DAY_OF_MONTH, Integer.parseInt(date.substring(8, 10)));
        } catch (Exception ignored) {
        }
        return calendar;
    }

    @NonNull
    private static String formatDate(int year, int month, int day) {
        return String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day);
    }

    @NonNull
    private static String firstIsoDate(@Nullable String... values) {
        if (values == null) return "";

        for (String value : values) {
            if (value == null) continue;
            Matcher matcher = ISO_DATE_PATTERN.matcher(value);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return "";
    }
}
