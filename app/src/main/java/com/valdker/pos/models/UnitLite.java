package com.valdker.pos.models;

import androidx.annotation.NonNull;

public class UnitLite {
    public final int id;
    @NonNull public final String name;

    public UnitLite(int id, @NonNull String name) {
        this.id = id;
        this.name = name;
    }

    @NonNull
    @Override
    public String toString() {
        return name;
    }
}
