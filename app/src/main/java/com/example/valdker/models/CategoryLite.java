package com.example.valdker.models;

import androidx.annotation.NonNull;

public class CategoryLite {
    public final int id;
    @NonNull public final String name;

    public CategoryLite(int id, @NonNull String name) {
        this.id = id;
        this.name = name;
    }

    @NonNull
    @Override
    public String toString() {
        return name;
    }
}
