package com.example.valdker.offline.db.entities;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "orders",
        indices = {
                @Index(value = {"synced"}),
                @Index(value = {"server_id"}),
                @Index(value = {"invoice_number"}, unique = true)
        }
)
public class OrderEntity {

    @PrimaryKey(autoGenerate = true)
    public long id; // local id

    public Long shift_local_id;     // nullable kalau belum shift
    public String shift_server_id;  // nullable

    public String server_id; // nullable

    @NonNull
    public String invoice_number;

    @NonNull
    public String created_at_iso; // ISO8601 string

    public String cashier_username;

    // header/meta
    public String order_type;         // DINE_IN / TAKE_OUT / DELIVERY / GENERAL
    public String table_number;       // nullable
    public String delivery_address;   // nullable

    // payment
    public String payment_method;     // CASH / TRANSFER / QRIS
    public double cash_received;      // for CASH (optional)
    public double change_amount;      // for CASH (optional)

    // totals
    public double subtotal;
    public double discount;
    public double tax;
    public double delivery_fee;
    public double total;

    // sync flags
    public boolean synced;
    public String synced_at_iso; // nullable

    public OrderEntity(@NonNull String invoice_number, @NonNull String created_at_iso) {
        this.invoice_number = invoice_number;
        this.created_at_iso = created_at_iso;
        this.synced = false;
    }
}