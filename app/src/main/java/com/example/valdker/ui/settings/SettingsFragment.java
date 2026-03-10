package com.example.valdker.ui.settings;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.example.valdker.LoginActivity;
import com.example.valdker.R;
import com.example.valdker.SessionManager;
import com.example.valdker.models.Shop;
import com.example.valdker.network.ApiClient;
import com.example.valdker.repositories.ShopRepository;
import com.example.valdker.shop.ShopEvents;
import com.example.valdker.utils.InsetsHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.HashMap;
import java.util.Map;

public class SettingsFragment extends Fragment {

    private static final String TAG = "SettingsFragment";

    private ProgressBar progress;

    private EditText etName, etAddress, etPhone, etEmail;
    private EditText etBaseUrl;
    private MaterialButton btnSaveBaseUrl;

    private ImageView imgLogoPreview;
    private MaterialButton btnPickLogo, btnSave;

    private View dot;
    private TextView tvPrinterStatus;
    private TextView tvPrinterSelected;
    private MaterialButton btnOpenPrinterSettings;
    private MaterialButton btnTestPrint;
    private MaterialSwitch switchAutoPrint;
    private ChipGroup chipGroupPaperWidth;
    private Chip chipPaper58, chipPaper80;

    private Uri pickedLogoUri;
    private Shop currentShop;
    private View rootSettings;

    public SettingsFragment() {
        super(R.layout.fragment_settings);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rootSettings = view.findViewById(R.id.rootSettings);
        if (rootSettings != null) {
            InsetsHelper.applyScrollInsets(rootSettings);
        }

        progress = view.findViewById(R.id.progressSettings);

        etName = view.findViewById(R.id.etShopName);
        etAddress = view.findViewById(R.id.etShopAddress);
        etPhone = view.findViewById(R.id.etShopPhone);
        etEmail = view.findViewById(R.id.etShopEmail);

        etBaseUrl = view.findViewById(R.id.etBaseUrl);
        btnSaveBaseUrl = view.findViewById(R.id.btnSaveBaseUrl);

        SessionManager smBase = new SessionManager(requireContext());
        if (etBaseUrl != null) {
            etBaseUrl.setText(smBase.getBaseUrl());
        }

        imgLogoPreview = view.findViewById(R.id.imgLogoPreview);
        btnPickLogo = view.findViewById(R.id.btnPickLogo);
        btnSave = view.findViewById(R.id.btnSaveShop);

        dot = view.findViewById(R.id.viewPrinterDot);
        tvPrinterStatus = view.findViewById(R.id.tvPrinterStatus);
        tvPrinterSelected = view.findViewById(R.id.tvPrinterSelected);
        btnOpenPrinterSettings = view.findViewById(R.id.btnOpenPrinterSettings);
        btnTestPrint = view.findViewById(R.id.btnTestPrint);
        switchAutoPrint = view.findViewById(R.id.switchAutoPrint);
        chipGroupPaperWidth = view.findViewById(R.id.chipGroupPaperWidth);
        chipPaper58 = view.findViewById(R.id.chipPaper58);
        chipPaper80 = view.findViewById(R.id.chipPaper80);

        if (btnPickLogo != null) btnPickLogo.setOnClickListener(v -> pickLogo.launch("image/*"));
        if (btnSave != null) btnSave.setOnClickListener(v -> save());
        if (btnSaveBaseUrl != null) btnSaveBaseUrl.setOnClickListener(v -> saveBaseUrl());

        setupPrinterUi();
        loadShop();
    }

    private void saveBaseUrl() {
        if (!isAdded()) return;

        String newUrl = (etBaseUrl != null && etBaseUrl.getText() != null)
                ? etBaseUrl.getText().toString().trim()
                : "";

        if (newUrl.isEmpty()) {
            Toast.makeText(requireContext(), "Base URL cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!newUrl.startsWith("http://") && !newUrl.startsWith("https://")) {
            Toast.makeText(requireContext(), "URL must start with http:// or https://", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!newUrl.endsWith("/")) {
            newUrl = newUrl + "/";
        }

        SessionManager sm = new SessionManager(requireContext());
        sm.setBaseUrl(newUrl);
        sm.clearAuth();
        sm.clearShift();

        ApiClient.getInstance(requireContext()).cancelAll("DASHBOARD");
        ApiClient.getInstance(requireContext()).cancelAll("ShopRepository");
        ApiClient.getInstance(requireContext()).cancelAll("ApiClient");
        ApiClient.getInstance(requireContext()).clearCache();

        Log.w("BASE_URL", "Settings saved=" + newUrl);
        Log.w("BASE_URL", "Settings readback=" + sm.getBaseUrl());

        Intent i = new Intent(requireContext(), LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        requireActivity().finish();
    }

    @Override
    public void onResume() {
        super.onResume();
        setupPrinterUi();
    }

    private void setupPrinterUi() {
        if (!isAdded()) return;

        String name = com.example.valdker.print.PrinterPrefs.getName(requireContext());
        String mac = com.example.valdker.print.PrinterPrefs.getMac(requireContext());

        if (tvPrinterSelected != null) {
            if (name != null && mac != null) {
                tvPrinterSelected.setText("Selected printer: " + name + " (" + mac + ")");
            } else {
                tvPrinterSelected.setText("Selected printer: -");
            }
        }

        if (switchAutoPrint != null) {
            boolean auto = com.example.valdker.print.PrinterPrefs.isAutoPrintEnabled(requireContext());
            switchAutoPrint.setOnCheckedChangeListener(null);
            switchAutoPrint.setChecked(auto);
            switchAutoPrint.setOnCheckedChangeListener((buttonView, isChecked) ->
                    com.example.valdker.print.PrinterPrefs.setAutoPrint(requireContext(), isChecked)
            );
        }

        int mm = com.example.valdker.print.PrinterPrefs.getPaperWidthMm(requireContext());
        if (chipPaper80 != null && chipPaper58 != null) {
            if (mm >= 80) chipPaper80.setChecked(true);
            else chipPaper58.setChecked(true);
        }

        if (chipGroupPaperWidth != null) {
            chipGroupPaperWidth.setOnCheckedChangeListener((group, checkedId) -> {
                if (!isAdded()) return;
                if (checkedId == R.id.chipPaper80) {
                    com.example.valdker.print.PrinterPrefs.setPaperWidthMm(requireContext(), 80);
                } else if (checkedId == R.id.chipPaper58) {
                    com.example.valdker.print.PrinterPrefs.setPaperWidthMm(requireContext(), 58);
                }
            });
        }

        boolean connectedLike = false;
        try {
            connectedLike = com.example.valdker.print.PrinterService.isPrinterReachable(requireContext());
        } catch (Exception ignored) {
        }

        if (dot != null && tvPrinterStatus != null) {
            dot.setBackgroundResource(connectedLike ? R.drawable.bg_status_dot_green : R.drawable.bg_status_dot_red);
            tvPrinterStatus.setText(connectedLike ? "Connected" : "Not connected");
        }

        if (btnOpenPrinterSettings != null) {
            btnOpenPrinterSettings.setOnClickListener(v -> {
                if (!isAdded()) return;
                startActivity(new Intent(requireContext(),
                        com.example.valdker.print.PrinterSettingsActivity.class));
            });
        }

        if (btnTestPrint != null) {
            btnTestPrint.setOnClickListener(v -> doTestPrint());
        }
    }

    private void doTestPrint() {
        if (!isAdded()) return;

        if (!com.example.valdker.print.PrinterService.hasBtPermission(requireContext())) {
            Toast.makeText(
                    requireContext(),
                    "Bluetooth permission is not granted yet. Please allow Bluetooth permission first.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        String demo =
                "[C]<b>VALDKER POS</b>\n" +
                        "[C]-------------------------------\n" +
                        "[L]TEST PRINT\n" +
                        "[L]Printer OK ✅\n" +
                        "[C]-------------------------------\n" +
                        "[L]Paper Width: " +
                        com.example.valdker.print.PrinterPrefs.getPaperWidthMm(requireContext()) +
                        "mm\n" +
                        "[C]\n\n\n";

        try {
            com.example.valdker.print.PrinterService.printText(requireContext(), demo);
            Toast.makeText(requireContext(), "✅ Test printed", Toast.LENGTH_SHORT).show();
            setupPrinterUi();
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Test print failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private final ActivityResultLauncher<String> pickLogo =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) return;
                pickedLogoUri = uri;

                if (imgLogoPreview != null) {
                    Glide.with(requireContext())
                            .load(uri)
                            .placeholder(R.drawable.ic_store)
                            .error(R.drawable.ic_store)
                            .diskCacheStrategy(DiskCacheStrategy.NONE)
                            .skipMemoryCache(true)
                            .circleCrop()
                            .into(imgLogoPreview);
                }
            });

    private void loadShop() {
        if (!isAdded()) return;

        SessionManager sm = new SessionManager(requireContext());
        String token = sm.getToken();

        if (token == null || token.trim().isEmpty()) {
            Toast.makeText(requireContext(), "Missing token. Please login again.", Toast.LENGTH_LONG).show();
            return;
        }

        setLoading(true);

        ShopRepository.fetchFirstShop(requireContext(), token, new ShopRepository.Callback() {
            @Override
            public void onSuccess(@NonNull Shop shop) {
                if (!isAdded()) return;
                currentShop = shop;
                bind(shop);
                setLoading(false);
            }

            @Override
            public void onEmpty() {
                if (!isAdded()) return;
                setLoading(false);
                Toast.makeText(requireContext(), "No shop found on server.", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(@NonNull String message) {
                if (!isAdded()) return;
                setLoading(false);
                Toast.makeText(requireContext(), "Failed to load shop: " + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void bind(@NonNull Shop s) {
        if (etName != null) etName.setText(s.name);
        if (etAddress != null) etAddress.setText(s.address);
        if (etPhone != null) etPhone.setText(s.phone);
        if (etEmail != null) etEmail.setText(s.email);

        if (imgLogoPreview != null) {
            String logoUrl = s.logoUrl != null ? s.logoUrl.trim() : "";
            Log.d(TAG, "bind logoUrl=" + logoUrl);

            if (!logoUrl.isEmpty() && !"null".equalsIgnoreCase(logoUrl)) {
                Glide.with(requireContext())
                        .load(logoUrl)
                        .placeholder(R.drawable.ic_store)
                        .error(R.drawable.ic_store)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .skipMemoryCache(true)
                        .dontAnimate()
                        .circleCrop()
                        .into(imgLogoPreview);
            } else {
                Glide.with(requireContext()).clear(imgLogoPreview);
                imgLogoPreview.setImageResource(R.drawable.ic_store);
            }
        }
    }

    private void save() {
        if (!isAdded()) return;

        if (currentShop == null) {
            Toast.makeText(requireContext(), "Shop is not loaded yet.", Toast.LENGTH_SHORT).show();
            return;
        }

        String name = etName != null ? etName.getText().toString().trim() : "";
        String address = etAddress != null ? etAddress.getText().toString().trim() : "";
        String phone = etPhone != null ? etPhone.getText().toString().trim() : "";
        String email = etEmail != null ? etEmail.getText().toString().trim() : "";

        if (name.isEmpty() || address.isEmpty() || phone.isEmpty()) {
            Toast.makeText(requireContext(), "Name, Address, and Phone are required.", Toast.LENGTH_LONG).show();
            return;
        }

        SessionManager sm = new SessionManager(requireContext());
        String token = sm.getToken();

        if (token == null || token.trim().isEmpty()) {
            Toast.makeText(requireContext(), "Missing token. Please login again.", Toast.LENGTH_LONG).show();
            return;
        }

        Map<String, String> fields = new HashMap<>();
        fields.put("name", name);
        fields.put("address", address);
        fields.put("phone", phone);
        fields.put("email", email);

        setLoading(true);

        ShopRepository.updateShopMultipart(
                requireContext(),
                currentShop.id,
                token,
                fields,
                pickedLogoUri,
                null,
                new ShopRepository.UpdateCallback() {
                    @Override
                    public void onSuccess(@NonNull Shop updatedShop) {
                        if (!isAdded()) return;

                        setLoading(false);
                        currentShop = updatedShop;
                        pickedLogoUri = null;

                        bind(updatedShop);

                        Toast.makeText(requireContext(), "✅ Shop saved", Toast.LENGTH_SHORT).show();
                        sendShopUpdatedBroadcast(requireContext());
                    }

                    @Override
                    public void onError(int statusCode, @NonNull String message) {
                        if (!isAdded()) return;
                        setLoading(false);
                        Toast.makeText(
                                requireContext(),
                                "Save failed (" + statusCode + "): " + message,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
        );
    }

    private void sendShopUpdatedBroadcast(@NonNull Context ctx) {
        Intent i = new Intent(ShopEvents.ACTION_SHOP_UPDATED);
        ctx.sendBroadcast(i);
    }

    private void setLoading(boolean loading) {
        if (progress != null) progress.setVisibility(loading ? View.VISIBLE : View.GONE);

        if (btnSave != null) btnSave.setEnabled(!loading);
        if (btnPickLogo != null) btnPickLogo.setEnabled(!loading);
        if (btnSaveBaseUrl != null) btnSaveBaseUrl.setEnabled(!loading);
        if (btnOpenPrinterSettings != null) btnOpenPrinterSettings.setEnabled(!loading);
        if (btnTestPrint != null) btnTestPrint.setEnabled(!loading);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        rootSettings = null;
        progress = null;
        etName = etAddress = etPhone = etEmail = null;
        etBaseUrl = null;
        btnSaveBaseUrl = null;
        imgLogoPreview = null;
        btnPickLogo = btnSave = null;

        dot = null;
        tvPrinterStatus = null;
        tvPrinterSelected = null;
        btnOpenPrinterSettings = null;
        btnTestPrint = null;
        switchAutoPrint = null;
        chipGroupPaperWidth = null;
        chipPaper58 = chipPaper80 = null;
    }
}