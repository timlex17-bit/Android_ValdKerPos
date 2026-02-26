package com.example.valdker;

import android.content.Intent;
import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.example.valdker.network.ApiClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LOGIN";

    private EditText etUsername, etPassword;
    private TextView btnTogglePwd;
    private Button btnLogin, btnDemo;

    private boolean pwdVisible = false;

    private static final String LOGIN_URL = "https://valdker.onrender.com/api/auth/login/";

    private static final int TIMEOUT_MS = 20000;
    private static final int MAX_RETRIES = 0;
    private static final float BACKOFF_MULT = 1.0f;

    private SessionManager sm;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        sm = new SessionManager(this);

        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        btnTogglePwd = findViewById(R.id.btnTogglePwd);
        btnLogin = findViewById(R.id.btnLogin);
        btnDemo = findViewById(R.id.btnDemo);

        String token = sm.getToken();
        if (token != null && !token.trim().isEmpty()) {
            // If cashier => go POS, else dashboard
            String role = sm.getRole() == null ? "" : sm.getRole().trim().toLowerCase();
            if ("cashier".equals(role)) {
                goToPOS();
            } else {
                goToDashboard();
            }
            return;
        } else {
            sm.clear();
        }

        btnDemo.setOnClickListener(v -> {
            etUsername.setText("admin");
            etPassword.setText("admin");
        });

        btnTogglePwd.setOnClickListener(v -> togglePassword());
        btnLogin.setOnClickListener(v -> doLogin());
    }

    private void doLogin() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString();

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Username and password are required", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);

        try {
            JSONObject body = new JSONObject();
            body.put("username", username);
            body.put("password", password);

            Log.i(TAG, "POST " + LOGIN_URL + " username=" + username);

            JsonObjectRequest req = new JsonObjectRequest(
                    Request.Method.POST,
                    LOGIN_URL,
                    body,
                    response -> {
                        setLoading(false);

                        try {
                            String newToken = response.optString("token", "");
                            JSONObject user = response.optJSONObject("user");
                            JSONArray perms = response.optJSONArray("permissions");

                            String u = (user != null)
                                    ? user.optString("username", username)
                                    : username;

                            String role = (user != null)
                                    ? user.optString("role", "cashier")
                                    : "cashier";

                            role = role == null ? "cashier" : role.trim().toLowerCase();

                            Log.i(TAG, "Login ok tokenLen=" + (newToken != null ? newToken.length() : 0));
                            Log.i(TAG, "Login ok user=" + u + " role=" + role + " perms=" + (perms != null ? perms.length() : 0));

                            if (newToken == null || newToken.trim().isEmpty()) {
                                Toast.makeText(this, "Token missing from server response", Toast.LENGTH_LONG).show();
                                return;
                            }

                            sm.clear();
                            sm.saveSession(newToken, u, role, perms);

                            // ✅ Cashier langsung POS
                            if ("cashier".equals(role)) {
                                goToPOS();
                            } else {
                                goToDashboard();
                            }

                        } catch (Exception e) {
                            Log.e(TAG, "Response parse error", e);
                            Toast.makeText(this, "Parse error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    },
                    error -> {
                        setLoading(false);

                        int code = (error.networkResponse != null) ? error.networkResponse.statusCode : -1;
                        String serverBody = extractErrorBody(error.networkResponse);

                        Log.e(TAG, "Login failed HTTP " + code + " body=" + serverBody, error);

                        if (code == 401 || code == 403) {
                            sm.clear();
                        }

                        Toast.makeText(this, "Login failed (HTTP " + code + ")", Toast.LENGTH_LONG).show();
                    }
            ) {
                @Override
                public Map<String, String> getHeaders() {
                    Map<String, String> h = new HashMap<>();
                    h.put("Accept", "application/json");
                    return h;
                }
            };

            req.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, MAX_RETRIES, BACKOFF_MULT));
            req.setShouldCache(false);

            ApiClient.getInstance(this).add(req);

        } catch (Exception e) {
            setLoading(false);
            Log.e(TAG, "Login error", e);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void goToDashboard() {
        Intent i = new Intent(this, com.example.valdker.ui.dashboard.HomeDashboardActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private void goToPOS() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private void setLoading(boolean loading) {
        btnLogin.setEnabled(!loading);
        btnLogin.setText(loading ? "Loading..." : "Login");
    }

    private void togglePassword() {
        pwdVisible = !pwdVisible;

        if (pwdVisible) {
            etPassword.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
            btnTogglePwd.setText("🙈");
        } else {
            etPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
            btnTogglePwd.setText("👁");
        }

        etPassword.setSelection(etPassword.getText().length());
    }

    private String extractErrorBody(@Nullable NetworkResponse nr) {
        if (nr == null || nr.data == null || nr.data.length == 0) return "";
        try {
            String s = new String(nr.data, StandardCharsets.UTF_8).trim();
            if (s.length() > 300) s = s.substring(0, 300) + "...";
            return s;
        } catch (Exception ignored) {
            return "";
        }
    }
}