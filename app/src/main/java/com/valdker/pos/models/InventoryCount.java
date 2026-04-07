package com.valdker.pos.models;

import java.util.ArrayList;
import java.util.List;

public class InventoryCount {
    public int id;
    public String title;
    public String note;
    public String status;
    public String counted_at;
    public UserLite counted_by;

    public static class UserLite {
        public int id;
        public String username;
        public String display_name;
    }

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