package com.valdker.pos.models;

public class Expense {
    public int id;
    public String name;
    public String note;
    public String amount; // keep as string to avoid float issues (server returns "20.00")
    public String date;   // "YYYY-MM-DD"
    public String time;   // "HH:mm:ss"
}
