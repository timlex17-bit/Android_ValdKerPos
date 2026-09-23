package com.valdker.pos.ui.ownerchat;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Business data from a tool-backed Owner Chat answer, already mapped into
 * a shape the UI can render directly - metric/value pairs for scalar
 * fields, named tables for any list-valued field. See
 * {@link StructuredDataMapper} for the exact mapping rules.
 *
 * <p>Built once per response; never holds raw JSON, and the UI never has
 * to re-interpret anything - it only iterates {@link #metrics} and
 * {@link #tables}.
 */
public class StructuredDataView {

    public enum Category {
        /** No {@code structured_data} at all - a plain conversational answer. */
        NONE,
        /** A JSON object, mapped into metrics/tables below. */
        OBJECT,
        /**
         * A bare JSON array. Not part of the current backend contract
         * (see StructuredDataMapper's own docstring) - handled defensively
         * as a single unnamed table rather than dropped.
         */
        ARRAY,
        /** Present but carries no fields/elements worth showing. */
        EMPTY,
        /**
         * Present but not an object or array (e.g. a bare string/number).
         * Not part of the current contract; never rendered as data - the
         * text answer alone is shown.
         */
        UNSUPPORTED,
    }

    public static class Metric {
        @NonNull public final String label;
        @NonNull public final String value;

        Metric(@NonNull String label, @NonNull String value) {
            this.label = label;
            this.value = value;
        }
    }

    public static class Table {
        /**
         * Section heading derived from the JSON field name (e.g.
         * "top_expenses" -> "Top Expenses"). Empty for a bare top-level
         * array, which has no field name to derive one from.
         */
        @NonNull public final String title;
        /** Each row's column-label -> value, in source field order, with
         *  any {@code *_id} column already excluded (see StructuredDataMapper). */
        @NonNull public final List<LinkedHashMap<String, String>> rows;

        Table(@NonNull String title, @NonNull List<LinkedHashMap<String, String>> rows) {
            this.title = title;
            this.rows = rows;
        }
    }

    @NonNull public final Category category;
    @NonNull public final List<Metric> metrics;
    @NonNull public final List<Table> tables;

    StructuredDataView(@NonNull Category category, @NonNull List<Metric> metrics, @NonNull List<Table> tables) {
        this.category = category;
        this.metrics = metrics;
        this.tables = tables;
    }

    static StructuredDataView none() {
        return new StructuredDataView(Category.NONE, new ArrayList<>(), new ArrayList<>());
    }

    static StructuredDataView empty() {
        return new StructuredDataView(Category.EMPTY, new ArrayList<>(), new ArrayList<>());
    }

    static StructuredDataView unsupported() {
        return new StructuredDataView(Category.UNSUPPORTED, new ArrayList<>(), new ArrayList<>());
    }

    /** Whether there is anything at all worth rendering as a data section. */
    public boolean hasDisplayableData() {
        return !metrics.isEmpty() || !tables.isEmpty();
    }
}
