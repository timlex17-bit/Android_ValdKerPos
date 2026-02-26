package com.example.valdker.models;

import java.util.ArrayList;
import java.util.List;

public class InventoryCount {
    public int id;
    public String title;
    public String note;
    public String counted_at;   // ISO string
    public int counted_by;      // user id

    public List<InventoryCountItem> items = new ArrayList<>();

    // helper
    public int totalItems() {
        return items == null ? 0 : items.size();
    }

    public int totalDifference() {
        if (items == null) return 0;
        int sum = 0;
        for (InventoryCountItem it : items) sum += it.difference;
        return sum;
    }
}