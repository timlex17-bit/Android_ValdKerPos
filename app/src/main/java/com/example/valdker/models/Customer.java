package com.example.valdker.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class Customer {
    public final int id;
    @NonNull public final String name;
    @NonNull public final String cell;
    @Nullable public final String email;
    @Nullable public final String address;
    public final long points;

    public Customer(int id,
                    @NonNull String name,
                    @NonNull String cell,
                    @Nullable String email,
                    @Nullable String address,
                    long points) {
        this.id = id;
        this.name = name;
        this.cell = cell;
        this.email = email;
        this.address = address;
        this.points = points;
    }
}