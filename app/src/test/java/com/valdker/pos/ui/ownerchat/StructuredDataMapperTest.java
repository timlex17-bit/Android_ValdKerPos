package com.valdker.pos.ui.ownerchat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.LinkedHashMap;

/*
 * Catatan urutan: org.json.JSONObject di JVM host (dipakai testImplementation
 * di sini) tidak menjamin urutan kunci sama seperti teks sumbernya - berbeda
 * dari implementasi bawaan Android di perangkat sungguhan, yang memang
 * menjaga urutan. Karena itu uji di bawah mencari metrik lewat label
 * (helper metricByLabel), bukan lewat indeks larik.
 */

/**
 * Pemetaan {@code structured_data} menjadi sesuatu yang bisa dirender.
 *
 * <p>Bentuk JSON di bawah disalin dari {@code ai_assistant/tools/registry.py}
 * dan {@code docs/api/AI_ASSISTANT_API.md} - sales_summary/todays_sales
 * mengembalikan objek datar berisi angka, top_selling_products/
 * low_stock_products membungkus larik lewat kunci "products", dan
 * expense_summary/daily_business_summary mencampur keduanya dalam satu
 * objek yang sama.
 */
public class StructuredDataMapperTest {

    // ------------------------------------------------- objek (kasus nyata)

    @Test
    public void flatObjectFromSalesSummaryBecomesMetricsOnly() throws Exception {
        // Bentuk sungguhan tool_sales_summary/tool_todays_sales.
        JSONObject raw = new JSONObject()
                .put("orders", 3)
                .put("net_sales", "245.00")
                .put("average_order_value", "81.67")
                .put("currency", "USD");

        StructuredDataView view = StructuredDataMapper.map(raw);

        assertEquals(StructuredDataView.Category.OBJECT, view.category);
        assertTrue(view.tables.isEmpty());
        assertEquals(4, view.metrics.size());
        assertTrue(view.hasDisplayableData());

        assertEquals("3", metricByLabel(view, "Orders"));
        assertEquals("245.00", metricByLabel(view, "Net Sales"));
    }

    @Test
    public void objectWithAProductsArrayBecomesATableNamedAfterTheKey() throws Exception {
        // Bentuk sungguhan tool_top_selling_products/tool_low_stock_products.
        JSONArray products = new JSONArray()
                .put(new JSONObject().put("product_id", 1).put("name", "Aqua")
                        .put("quantity_sold", 10).put("revenue", "20.00"));
        JSONObject raw = new JSONObject().put("products", products);

        StructuredDataView view = StructuredDataMapper.map(raw);

        assertEquals(StructuredDataView.Category.OBJECT, view.category);
        assertTrue(view.metrics.isEmpty());
        assertEquals(1, view.tables.size());

        StructuredDataView.Table table = view.tables.get(0);
        assertEquals("Products", table.title);
        assertEquals(1, table.rows.size());

        LinkedHashMap<String, String> row = table.rows.get(0);
        // product_id ends with "_id" - excluded from display.
        assertFalse(row.containsKey("Product Id"));
        assertEquals("Aqua", row.get("Name"));
        assertEquals("10", row.get("Quantity Sold"));
        assertEquals("20.00", row.get("Revenue"));
    }

    @Test
    public void objectMixingScalarsAndAListBecomesBothMetricsAndATable() throws Exception {
        // Bentuk sungguhan tool_expense_summary.
        JSONArray topExpenses = new JSONArray()
                .put(new JSONObject().put("name", "Water").put("amount", "12.00"));
        JSONObject raw = new JSONObject()
                .put("total", "12.00")
                .put("top_expenses", topExpenses)
                .put("currency", "USD");

        StructuredDataView view = StructuredDataMapper.map(raw);

        assertEquals(StructuredDataView.Category.OBJECT, view.category);
        assertEquals(2, view.metrics.size()); // total, currency
        assertEquals(1, view.tables.size());
        assertEquals("Top Expenses", view.tables.get(0).title);
    }

    // ------------------------------------------------------------- larik

    @Test
    public void bareTopLevelArrayIsHandledDefensivelyAsAnUnnamedTable() throws Exception {
        // Bukan bentuk backend sungguhan hari ini - jaga-jaga saja.
        JSONArray raw = new JSONArray()
                .put(new JSONObject().put("name", "A"))
                .put(new JSONObject().put("name", "B"));

        StructuredDataView view = StructuredDataMapper.map(raw);

        assertEquals(StructuredDataView.Category.ARRAY, view.category);
        assertEquals(1, view.tables.size());
        assertEquals("", view.tables.get(0).title);
        assertEquals(2, view.tables.get(0).rows.size());
    }

    @Test
    public void emptyArrayIsEmptyCategoryNotACrash() {
        StructuredDataView view = StructuredDataMapper.map(new JSONArray());
        assertEquals(StructuredDataView.Category.EMPTY, view.category);
        assertFalse(view.hasDisplayableData());
    }

    // -------------------------------------------------------------- null

    @Test
    public void nullStructuredDataIsCategoryNone() {
        assertEquals(StructuredDataView.Category.NONE, StructuredDataMapper.map(null).category);
        assertEquals(StructuredDataView.Category.NONE, StructuredDataMapper.map(JSONObject.NULL).category);
    }

    @Test
    public void emptyObjectIsEmptyCategoryNotNone() {
        StructuredDataView view = StructuredDataMapper.map(new JSONObject());
        assertEquals(StructuredDataView.Category.EMPTY, view.category);
        assertFalse(view.hasDisplayableData());
    }

    // ------------------------------------------------- bentuk tak didukung

    @Test
    public void aBarePrimitiveIsUnsupportedNeverRenderedAsData() {
        assertEquals(StructuredDataView.Category.UNSUPPORTED, StructuredDataMapper.map("just a string").category);
        assertEquals(StructuredDataView.Category.UNSUPPORTED, StructuredDataMapper.map(42).category);
    }

    @Test
    public void aNestedObjectFieldIsSkippedNotGuessedAt() throws Exception {
        JSONObject raw = new JSONObject()
                .put("orders", 3)
                .put("meta", new JSONObject().put("shop_id", 1)); // bentuk yang tidak ada di kontrak manapun

        StructuredDataView view = StructuredDataMapper.map(raw);

        assertEquals(1, view.metrics.size());
        assertEquals("Orders", view.metrics.get(0).label);
    }

    @Test
    public void nullValuedFieldsInsideAnObjectAreSkipped() throws Exception {
        JSONObject raw = new JSONObject().put("orders", 3).put("note", JSONObject.NULL);
        StructuredDataView view = StructuredDataMapper.map(raw);
        assertEquals(1, view.metrics.size());
    }

    private static String metricByLabel(StructuredDataView view, String label) {
        for (StructuredDataView.Metric metric : view.metrics) {
            if (metric.label.equals(label)) return metric.value;
        }
        throw new AssertionError("No metric labelled \"" + label + "\" in " + view.metrics.size() + " metrics");
    }
}
