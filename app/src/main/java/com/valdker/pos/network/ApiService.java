package com.valdker.pos.network;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class ApiService {

    public static final String REPORT_DASHBOARD_SUMMARY = "api/reports/dashboard-summary/";
    public static final String REPORT_SALES = "api/reports/sales/";
    public static final String REPORT_SALES_ITEMS = "api/reports/sales-items/";
    public static final String REPORT_PAYMENTS = "api/reports/payments/";
    public static final String REPORT_EXPENSES = "api/reports/expenses/";
    public static final String REPORT_STOCK = "api/reports/stock/";
    public static final String REPORT_LOW_STOCK = "api/reports/low-stock/";
    public static final String REPORT_SHIFTS = "api/reports/shifts/";
    public static final String REPORT_SALES_EXPORT = "api/reports/sales/export/";

    private static final Map<String, String> REPORT_ENDPOINTS = buildReportEndpoints();

    private ApiService() {
    }

    @NonNull
    public static String reportEndpoint(@NonNull String reportType) {
        String endpoint = REPORT_ENDPOINTS.get(reportType);
        return endpoint != null ? endpoint : REPORT_SALES;
    }

    @NonNull
    private static Map<String, String> buildReportEndpoints() {
        Map<String, String> out = new HashMap<>();
        out.put("daily", REPORT_DASHBOARD_SUMMARY);
        out.put("sales", REPORT_SALES);
        out.put("items", REPORT_SALES_ITEMS);
        out.put("payments", REPORT_PAYMENTS);
        out.put("expenses", REPORT_EXPENSES);
        out.put("stock", REPORT_STOCK);
        out.put("low_stock", REPORT_LOW_STOCK);
        out.put("shifts", REPORT_SHIFTS);
        return Collections.unmodifiableMap(out);
    }
}
