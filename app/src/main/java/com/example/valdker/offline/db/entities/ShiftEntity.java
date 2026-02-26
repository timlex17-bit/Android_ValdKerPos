package com.example.valdker.offline.db.entities;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "shifts",
        indices = {
                @Index(value = {"closed"}),
                @Index(value = {"synced"})
        }
)
public class ShiftEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public String server_id;
    public String cashier_username;

    public String opened_at_iso;
    public String closed_at_iso;

    public double opening_cash;
    public double closing_cash;

    public double expected_cash;

    public double cash_sales;
    public double non_cash_sales;
    public int orders_count;

    public boolean closed;
    public boolean synced;

    public String sync_error;

    public ShiftEntity(String cashier_username, String opened_at_iso, double opening_cash) {
        this.cashier_username = cashier_username;
        this.opened_at_iso = opened_at_iso;
        this.opening_cash = opening_cash;
        this.closed = false;
        this.synced = false;
    }
}