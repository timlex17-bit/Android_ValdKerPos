package com.valdker.pos;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    public static final String PREFS_APP_SETTINGS = "app_settings";
    public static final String HAS_SEEN_SPLASH = "HAS_SEEN_SPLASH";

    private int pageIndex = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (hasSeenSplash()) {
            openLogin();
            return;
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (pageIndex > 0) {
                    showPage(pageIndex - 1);
                    return;
                }
                finish();
            }
        });

        showPage(0);
    }

    private boolean hasSeenSplash() {
        SharedPreferences prefs = getSharedPreferences(PREFS_APP_SETTINGS, MODE_PRIVATE);
        return prefs.getBoolean(HAS_SEEN_SPLASH, false);
    }

    private void showPage(int index) {
        pageIndex = index;
        setContentView(layoutForPage(index));
        bindActions();
    }

    @LayoutRes
    private int layoutForPage(int index) {
        if (index == 1) return R.layout.activity_splash_screen_2;
        if (index == 2) return R.layout.activity_splash_screen_3;
        return R.layout.activity_splash_screen_1;
    }

    private void bindActions() {
        Button btnNext = findViewById(R.id.btnSplashNext);
        Button btnBack = findViewById(R.id.btnSplashBack);
        Button btnGetStarted = findViewById(R.id.btnSplashGetStarted);

        if (btnNext != null) {
            btnNext.setOnClickListener(v -> showPage(Math.min(pageIndex + 1, 2)));
        }

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                if (pageIndex > 0) {
                    showPage(pageIndex - 1);
                }
            });
        }

        if (btnGetStarted != null) {
            btnGetStarted.setOnClickListener(v -> finishSplash());
        }
    }

    private void finishSplash() {
        getSharedPreferences(PREFS_APP_SETTINGS, MODE_PRIVATE)
                .edit()
                .putBoolean(HAS_SEEN_SPLASH, true)
                .apply();
        openLogin();
    }

    private void openLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
