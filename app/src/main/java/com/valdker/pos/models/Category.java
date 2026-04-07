package com.valdker.pos.models;

public class Category {

    public int id;
    public String name;
    public String iconUrl;

    // ✅ constructor kosong (WAJIB untuk parsing & Room mapping)
    public Category() {
    }

    // ✅ constructor lengkap
    public Category(int id, String name, String iconUrl) {
        this.id = id;
        this.name = name;
        this.iconUrl = iconUrl;
    }
}