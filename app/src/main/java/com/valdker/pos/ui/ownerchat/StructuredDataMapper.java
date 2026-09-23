package com.valdker.pos.ui.ownerchat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Maps the raw {@code structured_data} value from
 * {@code POST /api/ai/assistant/chat/} into a {@link StructuredDataView}
 * the UI can render directly.
 *
 * <p>The actual contract ({@code ai_assistant/tools/registry.py},
 * {@code docs/api/AI_ASSISTANT_API.md}) always sends a flat JSON object
 * whose fields are either scalars (integers, or money/currency strings
 * such as {@code "245.00"}) or a list of row objects under a well-known
 * key - {@code "products"}, {@code "top_expenses"}, {@code "top_products"}
 * - never a bare top-level array, never a nested object. The
 * {@code ARRAY} and {@code UNSUPPORTED} categories exist purely as
 * defensive handling for shapes the backend does not send today, not
 * because either is expected in practice - this class must never crash or
 * throw on a shape it wasn't written for, only degrade to "nothing to
 * show here".
 *
 * <p>Pure JVM logic (no Android classes) so it can be unit tested with
 * plain {@code org.json} directly.
 */
public final class StructuredDataMapper {

    private StructuredDataMapper() {
    }

    @NonNull
    public static StructuredDataView map(@Nullable Object raw) {
        if (raw == null || raw == JSONObject.NULL) {
            return StructuredDataView.none();
        }

        if (raw instanceof JSONObject) {
            return mapObject((JSONObject) raw);
        }

        if (raw instanceof JSONArray) {
            JSONArray array = (JSONArray) raw;
            Table table = arrayToTable("", array);
            if (table == null) {
                return StructuredDataView.empty();
            }
            List<Table> tables = new ArrayList<>();
            tables.add(table);
            return new StructuredDataView(StructuredDataView.Category.ARRAY, new ArrayList<>(), toViewTables(tables));
        }

        return StructuredDataView.unsupported();
    }

    @NonNull
    private static StructuredDataView mapObject(@NonNull JSONObject obj) {
        if (obj.length() == 0) {
            return StructuredDataView.empty();
        }

        List<StructuredDataView.Metric> metrics = new ArrayList<>();
        List<Table> tables = new ArrayList<>();

        Iterator<String> keys = obj.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (obj.isNull(key)) continue;
            Object value = obj.opt(key);

            if (value instanceof JSONArray) {
                Table table = arrayToTable(humanize(key), (JSONArray) value);
                if (table != null) tables.add(table);
            } else if (value instanceof JSONObject) {
                // A nested object isn't part of any current tool's shape -
                // skipped rather than guessed at, so a future backend
                // change degrades to "this one field is missing" instead
                // of a crash or an invented rendering.
                continue;
            } else {
                metrics.add(new StructuredDataView.Metric(humanize(key), String.valueOf(value)));
            }
        }

        if (metrics.isEmpty() && tables.isEmpty()) {
            return StructuredDataView.empty();
        }
        return new StructuredDataView(StructuredDataView.Category.OBJECT, metrics, toViewTables(tables));
    }

    /** Internal row-building representation, converted to StructuredDataView.Table at the end. */
    private static final class Table {
        final String title;
        final List<LinkedHashMap<String, String>> rows;

        Table(String title, List<LinkedHashMap<String, String>> rows) {
            this.title = title;
            this.rows = rows;
        }
    }

    @NonNull
    private static List<StructuredDataView.Table> toViewTables(@NonNull List<Table> tables) {
        List<StructuredDataView.Table> result = new ArrayList<>();
        for (Table t : tables) {
            result.add(new StructuredDataView.Table(t.title, t.rows));
        }
        return result;
    }

    @Nullable
    private static Table arrayToTable(@NonNull String title, @NonNull JSONArray array) {
        if (array.length() == 0) return null;

        List<LinkedHashMap<String, String>> rows = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject rowObject = array.optJSONObject(i);
            LinkedHashMap<String, String> row = new LinkedHashMap<>();

            if (rowObject != null) {
                Iterator<String> keys = rowObject.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    if (rowObject.isNull(key)) continue;
                    // Internal identifiers (product_id, warehouse_id, ...)
                    // are never meant for display.
                    if (key.endsWith("_id")) continue;
                    Object value = rowObject.opt(key);
                    if (value instanceof JSONObject || value instanceof JSONArray) continue;
                    row.put(humanize(key), String.valueOf(value));
                }
            } else {
                Object primitive = array.opt(i);
                if (primitive == null || primitive == JSONObject.NULL) continue;
                row.put("Value", String.valueOf(primitive));
            }

            if (!row.isEmpty()) rows.add(row);
        }

        if (rows.isEmpty()) return null;
        return new Table(title, rows);
    }

    @NonNull
    private static String humanize(@NonNull String key) {
        String spaced = key.replace('_', ' ').trim();
        if (spaced.isEmpty()) return spaced;

        StringBuilder result = new StringBuilder(spaced.length());
        boolean capitalizeNext = true;
        for (int i = 0; i < spaced.length(); i++) {
            char c = spaced.charAt(i);
            if (c == ' ') {
                capitalizeNext = true;
                result.append(c);
                continue;
            }
            result.append(capitalizeNext ? Character.toUpperCase(c) : c);
            capitalizeNext = false;
        }
        return result.toString();
    }
}
