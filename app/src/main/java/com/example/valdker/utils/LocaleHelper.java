package com.example.valdker.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import java.util.Locale;

public class LocaleHelper {

    private static final String PREFS_APP_SETTINGS = "app_settings";
    private static final String KEY_APP_LANGUAGE = "app_language";
    private static final String DEFAULT_LANGUAGE_CODE = "id";

    public static Context applyAppLocale(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_APP_SETTINGS, Context.MODE_PRIVATE);
        String languageCode = prefs.getString(KEY_APP_LANGUAGE, DEFAULT_LANGUAGE_CODE);

        if (languageCode == null || languageCode.trim().isEmpty()) {
            languageCode = DEFAULT_LANGUAGE_CODE;
        }

        Locale locale = new Locale(languageCode);
        Locale.setDefault(locale);

        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(locale);
        config.setLayoutDirection(locale);

        return context.createConfigurationContext(config);
    }
}