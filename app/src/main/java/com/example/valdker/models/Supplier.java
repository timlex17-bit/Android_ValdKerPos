package com.example.valdker.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class Supplier {

    public final int id;

    @NonNull public final String name;
    @NonNull public final String contactPerson;
    @NonNull public final String cell;

    @Nullable public final String email;
    @Nullable public final String address;

    public Supplier(int id,
            @NonNull String name,
            @NonNull String contactPerson,
            @NonNull String cell,
            @Nullable String email,
            @Nullable String address) {

        this.id = id;
        this.name = name;
        this.contactPerson = contactPerson;
        this.cell = cell;
        this.email = email;
        this.address = address;
    }
}
