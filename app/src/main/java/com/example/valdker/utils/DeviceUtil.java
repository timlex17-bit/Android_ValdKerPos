package com.example.valdker.utils;

import android.content.Context;

public class DeviceUtil {
    public static boolean isTablet(Context ctx) {
        return ctx.getResources().getConfiguration().smallestScreenWidthDp >= 600;
    }
}