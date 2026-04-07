package com.valdker.pos.models;

import java.util.ArrayList;
import java.util.List;

public class DailyProfitReport {
    public String start;
    public String end;

    public double totalSales;
    public double totalExpense;
    public double totalProfit;

    public List<DailyProfitRow> rows = new ArrayList<>();
}
