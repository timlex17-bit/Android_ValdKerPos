package com.example.valdker.ui.settings;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.example.valdker.R;
import com.example.valdker.SessionManager;
import com.example.valdker.models.Shop;
import com.example.valdker.repositories.ShopRepository;
import com.example.valdker.shop.ShopEvents;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.HashMap;
import java.util.Map;

public class SettingsFragment extends Fragment {

    private ProgressBar progress;

    private EditText etName, etAddress, etPhone, etEmail;
    private ImageView imgLogoPreview, imgAllIconPreview;
    private MaterialButton btnPickLogo, btnPickAllIcon, btnSave;

    // ✅ Printer enterprise
    private View dot;
    private TextView tvPrinterStatus;
    private TextView tvPrinterSelected;
    private MaterialButton btnOpenPrinterSettings;
    private MaterialButton btnTestPrint;
    private MaterialSwitch switchAutoPrint;
    private ChipGroup chipGroupPaperWidth;
    private Chip chipPaper58, chipPaper80;

    private Uri pickedLogoUri;
    private Uri pickedAllIconUri;

    private Shop currentShop;

    public SettingsFragment() {
        super(R.layout.fragment_settings);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        progress = view.findViewById(R.id.progressSettings);

        etName = view.findViewById(R.id.etShopName);
        etAddress = view.findViewById(R.id.etShopAddress);
        etPhone = view.findViewById(R.id.etShopPhone);
        etEmail = view.findViewById(R.id.etShopEmail);

        imgLogoPreview = view.findViewById(R.id.imgLogoPreview);
        imgAllIconPreview = view.findViewById(R.id.imgAllIconPreview);

        btnPickLogo = view.findViewById(R.id.btnPickLogo);
        btnPickAllIcon = view.findViewById(R.id.btnPickAllIcon);
        btnSave = view.findViewById(R.id.btnSaveShop);

        // ✅ printer views
        dot = view.findViewById(R.id.viewPrinterDot);
        tvPrinterStatus = view.findViewById(R.id.tvPrinterStatus);
        tvPrinterSelected = view.findViewById(R.id.tvPrinterSelected);
        btnOpenPrinterSettings = view.findViewById(R.id.btnOpenPrinterSettings);
        btnTestPrint = view.findViewById(R.id.btnTestPrint);
        switchAutoPrint = view.findViewById(R.id.switchAutoPrint);
        chipGroupPaperWidth = view.findViewById(R.id.chipGroupPaperWidth);
        chipPaper58 = view.findViewById(R.id.chipPaper58);
        chipPaper80 = view.findViewById(R.id.chipPaper80);

        // Existing actions tetap
        btnPickLogo.setOnClickListener(v -> pickLogo.launch("image/*"));
        btnPickAllIcon.setOnClickListener(v -> pickAllIcon.launch("image/*"));
        btnSave.setOnClickListener(v -> save());

        setupPrinterUi();

        loadShop();
    }

    @Override
    public void onResume() {
        super.onResume();
        // refresh printer state after returning from PrinterSettingsActivity
        setupPrinterUi();
    }

    // =========================================================
    // ✅ PRINTER ENTERPRISE UI
    // =========================================================

    private void setupPrinterUi() {
        if (!isAdded()) return;

        String name = com.example.valdker.print.PrinterPrefs.getName(requireContext());
        String mac = com.example.valdker.print.PrinterPrefs.getMac(requireContext());

        if (name != null && mac != null) {
            tvPrinterSelected.setText("Selected printer: " + name + " (" + mac + ")");
        } else {
            tvPrinterSelected.setText("Selected printer: -");
        }

        // Auto print toggle
        boolean auto = com.example.valdker.print.PrinterPrefs.isAutoPrintEnabled(requireContext());
        switchAutoPrint.setChecked(auto);
        switchAutoPrint.setOnCheckedChangeListener((buttonView, isChecked) ->
                com.example.valdker.print.PrinterPrefs.setAutoPrint(requireContext(), isChecked)
        );

        // Paper width chips
        int mm = com.example.valdker.print.PrinterPrefs.getPaperWidthMm(requireContext());
        if (mm >= 80) chipPaper80.setChecked(true);
        else chipPaper58.setChecked(true);

        chipGroupPaperWidth.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.chipPaper80) {
                com.example.valdker.print.PrinterPrefs.setPaperWidthMm(requireContext(), 80);
            } else {
                com.example.valdker.print.PrinterPrefs.setPaperWidthMm(requireContext(), 58);
            }
        });

        // Status (reachable = paired & selected)
        boolean connectedLike = false;
        try {
            connectedLike = com.example.valdker.print.PrinterService.isPrinterReachable(requireContext());
        } catch (Exception ignored) {}

        if (connectedLike) {
            dot.setBackgroundColor(0xFF16A34A); // green
            tvPrinterStatus.setText("Connected");
        } else {
            dot.setBackgroundColor(0xFFDC2626); // red
            tvPrinterStatus.setText("Not connected");
        }

        btnOpenPrinterSettings.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(),
                    com.example.valdker.print.PrinterSettingsActivity.class));
        });

        btnTestPrint.setOnClickListener(v -> doTestPrint());
    }

    private void doTestPrint() {
        if (!isAdded()) return;

        // Permission check handled in CartFragment normally,
        // tapi untuk test print kita kasih pesan jelas.
        if (!com.example.valdker.print.PrinterService.hasBtPermission(requireContext())) {
            Toast.makeText(requireContext(),
                    "Bluetooth permission belum diizinkan. Buka transaksi/checkout dulu atau izinkan permission Bluetooth.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        String demo =
                "[C]<b>VALDKER POS</b>\n" +
                        "[C]-------------------------------\n" +
                        "[L]TEST PRINT\n" +
                        "[L]Printer OK ✅\n" +
                        "[C]-------------------------------\n" +
                        "[L]Paper Width: " + com.example.valdker.print.PrinterPrefs.getPaperWidthMm(requireContext()) + "mm\n" +
                        "[C]\n\n\n";

        try {
            com.example.valdker.print.PrinterService.printText(requireContext(), demo);
            Toast.makeText(requireContext(), "✅ Test printed", Toast.LENGTH_SHORT).show();
            setupPrinterUi(); // refresh status
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Test print gagal: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // =========================================================
    // EXISTING SHOP LOGIC (UNCHANGED)
    // =========================================================

    private final androidx.activity.result.ActivityResultLauncher<String> pickLogo =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) return;
                pickedLogoUri = uri;
                Glide.with(this).load(uri).circleCrop().into(imgLogoPreview);
            });

    private final androidx.activity.result.ActivityResultLauncher<String> pickAllIcon =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) return;
                pickedAllIconUri = uri;
                Glide.with(this).load(uri).into(imgAllIconPreview);
            });

    private void loadShop() {
        SessionManager sm = new SessionManager(requireContext());
        String token = sm.getToken();

        if (token == null || token.trim().isEmpty()) {
            Toast.makeText(requireContext(), "Token kosong. Silakan login ulang.", Toast.LENGTH_LONG).show();
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
                Toast.makeText(requireContext(), "Shop belum ada di server.", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(@NonNull String message) {
                if (!isAdded()) return;
                setLoading(false);
                Toast.makeText(requireContext(), "Failed load shop: " + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void bind(@NonNull Shop s) {
        etName.setText(s.name);
        etAddress.setText(s.address);
        etPhone.setText(s.phone);
        etEmail.setText(s.email);

        if (s.logoUrl != null && !s.logoUrl.trim().isEmpty()) {
            Glide.with(this).load(s.logoUrl).circleCrop().into(imgLogoPreview);
        } else {
            imgLogoPreview.setImageResource(R.drawable.bg_logo_circle);
        }

        if (s.allCategoryIconUrl != null && !s.allCategoryIconUrl.trim().isEmpty()) {
            Glide.with(this).load(s.allCategoryIconUrl).into(imgAllIconPreview);
        } else {
            imgAllIconPreview.setImageResource(R.drawable.ic_launcher_background);
        }
    }

    private void save() {
        if (currentShop == null) {
            Toast.makeText(requireContext(), "Shop belum loaded.", Toast.LENGTH_SHORT).show();
            return;
        }

        String name = etName.getText().toString().trim();
        String address = etAddress.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String email = etEmail.getText().toString().trim();

        if (name.isEmpty() || address.isEmpty() || phone.isEmpty()) {
            Toast.makeText(requireContext(), "Name, Address, Phone wajib diisi.", Toast.LENGTH_LONG).show();
            return;
        }

        SessionManager sm = new SessionManager(requireContext());
        String token = sm.getToken();

        if (token == null || token.trim().isEmpty()) {
            Toast.makeText(requireContext(), "Token kosong. Silakan login ulang.", Toast.LENGTH_LONG).show();
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
                pickedAllIconUri,
                new ShopRepository.UpdateCallback() {
                    @Override
                    public void onSuccess(@NonNull Shop updatedShop) {
                        if (!isAdded()) return;
                        setLoading(false);

                        currentShop = updatedShop;
                        pickedLogoUri = null;
                        pickedAllIconUri = null;

                        bind(updatedShop);

                        Toast.makeText(requireContext(), "✅ Shop saved", Toast.LENGTH_SHORT).show();
                        sendShopUpdatedBroadcast(requireContext());
                    }

                    @Override
                    public void onError(int statusCode, @NonNull String message) {
                        if (!isAdded()) return;
                        setLoading(false);
                        Toast.makeText(requireContext(),
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
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnSave.setEnabled(!loading);
        btnPickLogo.setEnabled(!loading);
        btnPickAllIcon.setEnabled(!loading);
        btnOpenPrinterSettings.setEnabled(!loading);
        btnTestPrint.setEnabled(!loading);
    }
}
