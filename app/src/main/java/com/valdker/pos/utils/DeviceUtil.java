package com.valdker.pos.utils;

import android.content.Context;

public class DeviceUtil {
    public static boolean isTablet(Context ctx) {
        return ctx.getResources().getConfiguration().smallestScreenWidthDp >= 600;
    }
}