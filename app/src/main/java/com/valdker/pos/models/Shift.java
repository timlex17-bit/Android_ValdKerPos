package com.valdker.pos.models;

import org.json.JSONObject;

public class Shift {
    public int id;
    public String status;
    public String opened_at;
    public String closed_at;

    public String opening_cash;
    public String closing_cash;

    public String total_sales;
    public String total_refunds;
    public String total_expenses;

    public String expected_cash;
    public String cash_difference;

    public String note;

    public static Shift fromJson(JSONObject o) {
        Shift s = new Shift();
        if (o == null) return s;

        s.id = o.optInt("id");
        s.status = o.optString("status", "");
        s.opened_at = o.optString("opened_at", "");
        s.closed_at = o.optString("closed_at", "");

        s.opening_cash = o.optString("opening_cash", "0.00");
        s.closing_cash = o.optString("closing_cash", "");

        s.total_sales = o.optString("total_sales", "0.00");
        s.total_refunds = o.optString("total_refunds", "0.00");
        s.total_expenses = o.optString("total_expenses", "0.00");

        s.expected_cash = o.optString("expected_cash", "0.00");
        s.cash_difference = o.optString("cash_difference", "0.00");

        s.note = o.optString("note", "");
        return s;
    }
}