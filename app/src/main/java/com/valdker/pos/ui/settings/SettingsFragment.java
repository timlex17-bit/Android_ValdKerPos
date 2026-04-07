package com.valdker.pos.ui.settings;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedDispatcher;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.valdker.pos.LoginActivity;
import com.valdker.pos.R;
import com.valdker.pos.SessionManager;
import com.valdker.pos.base.BaseFragment;
import com.valdker.pos.models.Shop;
import com.valdker.pos.network.ApiClient;
import com.valdker.pos.repositories.ShopRepository;
import com.valdker.pos.shop.ShopEvents;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.HashMap;
import java.util.Map;

public class SettingsFragment extends BaseFragment {

    private static final String TAG = "SettingsFragment";

    private static final String PREFS_APP_SETTINGS = "app_settings";
    private static final String KEY_APP_LANGUAGE = "app_language";
    private static final String DEFAULT_LANGUAGE_CODE = "id";

    private ProgressBar progress;
    private Spinner spinnerLanguage;
    private boolean isLanguageChanging = false;
    private boolean isBindingLanguageSpinner = false;

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

    private ImageView btnBack;
    private ImageView ivHeaderAction;
    private TextView tvTopTitle;

    public SettingsFragment() {
        super(R.layout.fragment_settings);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        applyTopInset(view.findViewById(R.id.topBar));

        rootSettings = view.findViewById(R.id.rootSettings);

        btnBack = view.findViewById(R.id.btnBack);
        ivHeaderAction = view.findViewById(R.id.ivHeaderAction);
        tvTopTitle = view.findViewById(R.id.tvTopTitle);

        if (tvTopTitle != null) {
            tvTopTitle.setText(R.string.title_settings);
        }

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                if (!isAdded()) return;
                OnBackPressedDispatcher dispatcher = requireActivity().getOnBackPressedDispatcher();
                dispatcher.onBackPressed();
            });
        }

        if (ivHeaderAction != null) {
            ivHeaderAction.setOnClickListener(v -> loadShop());
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

        spinnerLanguage = view.findViewById(R.id.spinnerLanguage);
        setupLanguageMenu();

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

    private void setupLanguageMenu() {
        if (!isAdded() || spinnerLanguage == null) return;

        String[] labels = new String[]{
                getString(R.string.language_tetun),
                getString(R.string.language_english),
                getString(R.string.language_indonesia)
        };

        String[] codes = new String[]{
                "tet",
                "en",
                "id"
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                labels
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerLanguage.setAdapter(adapter);

        String savedLang = getSavedLanguageCode();

        int selectedIndex = 0;
        for (int i = 0; i < codes.length; i++) {
            if (codes[i].equalsIgnoreCase(savedLang)) {
                selectedIndex = i;
                break;
            }
        }

        isBindingLanguageSpinner = true;
        spinnerLanguage.setSelection(selectedIndex, false);
        isBindingLanguageSpinner = false;

        spinnerLanguage.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!isAdded()) return;
                if (isBindingLanguageSpinner) return;
                if (isLanguageChanging) return;
                if (position < 0 || position >= codes.length) return;

                String selectedCode = codes[position];
                String currentCode = getSavedLanguageCode();

                if (selectedCode.equalsIgnoreCase(currentCode)) {
                    return;
                }

                applyLanguageChange(selectedCode);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        spinnerLanguage.setSelection(selectedIndex, false);

        spinnerLanguage.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            boolean firstLoad = true;

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (firstLoad) {
                    firstLoad = false;
                    return;
                }

                String selectedCode = codes[position];
                String currentCode = getSavedLanguageCode();

                if (!selectedCode.equalsIgnoreCase(currentCode)) {
                    saveLanguageCode(selectedCode);
                    applyLocale(selectedCode);

                    Intent intent = new Intent(requireActivity(), LoginActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    requireActivity().finish();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        isBindingLanguageSpinner = true;
        spinnerLanguage.setSelection(selectedIndex, false);
        isBindingLanguageSpinner = false;

        spinnerLanguage.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!isAdded()) return;
                if (isBindingLanguageSpinner) return;
                if (isLanguageChanging) return;
                if (position < 0 || position >= codes.length) return;

                String selectedCode = codes[position];
                String currentCode = getSavedLanguageCode();

                if (selectedCode.equalsIgnoreCase(currentCode)) {
                    return;
                }

                applyLanguageChange(selectedCode);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // no-op
            }
        });
    }

    private void applyLanguageChange(@NonNull String selectedCode) {
        if (!isAdded()) return;

        isLanguageChanging = true;

        try {
            saveLanguageCode(selectedCode);
            applyLocale(selectedCode);

            Toast.makeText(
                    requireContext(),
                    getString(R.string.msg_language_changed),
                    Toast.LENGTH_SHORT
            ).show();

            spinnerLanguage.postDelayed(() -> {
                if (!isAdded()) return;

                Intent intent = new Intent(requireContext(), LoginActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

                startActivity(intent);
                requireActivity().finish();

            }, 200);

        } catch (Exception e) {
            Log.e(TAG, "Failed to change language to: " + selectedCode, e);
            isLanguageChanging = false;

            Toast.makeText(
                    requireContext(),
                    "Failed to apply language: " + e.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void saveLanguageCode(@NonNull String code) {
        if (!isAdded()) return;

        SharedPreferences prefs = requireContext().getSharedPreferences(
                PREFS_APP_SETTINGS,
                Context.MODE_PRIVATE
        );

        prefs.edit()
                .putString(KEY_APP_LANGUAGE, code)
                .apply();
    }

    @NonNull
    private String getSavedLanguageCode() {
        if (!isAdded()) return DEFAULT_LANGUAGE_CODE;

        SharedPreferences prefs = requireContext().getSharedPreferences(
                PREFS_APP_SETTINGS,
                Context.MODE_PRIVATE
        );

        String code = prefs.getString(KEY_APP_LANGUAGE, DEFAULT_LANGUAGE_CODE);
        if (code == null || code.trim().isEmpty()) {
            return DEFAULT_LANGUAGE_CODE;
        }
        return code;
    }

    private void applyLocale(@NonNull String languageCode) {
        String languageTag = mapLanguageCodeToTag(languageCode);

        LocaleListCompat appLocale = LocaleListCompat.forLanguageTags(languageTag);
        AppCompatDelegate.setApplicationLocales(appLocale);

        Log.d(TAG, "applyLocale: code=" + languageCode + ", tag=" + languageTag);
    }

    @NonNull
    private String mapLanguageCodeToTag(@NonNull String languageCode) {
        switch (languageCode) {
            case "en":
                return "en";
            case "id":
                return "id";
            case "zh":
                return "zh";
            case "tet":
            default:
                return "tet";
        }
    }

    private void saveBaseUrl() {
        if (!isAdded()) return;

        String newUrl = (etBaseUrl != null && etBaseUrl.getText() != null)
                ? etBaseUrl.getText().toString().trim()
                : "";

        if (newUrl.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.msg_base_url_empty), Toast.LENGTH_SHORT).show();
            return;
        }

        if (!newUrl.startsWith("http://") && !newUrl.startsWith("https://")) {
            Toast.makeText(requireContext(), getString(R.string.msg_base_url_invalid_scheme), Toast.LENGTH_SHORT).show();
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

        String name = com.valdker.pos.print.PrinterPrefs.getName(requireContext());
        String mac = com.valdker.pos.print.PrinterPrefs.getMac(requireContext());

        if (tvPrinterSelected != null) {
            if (name != null && mac != null) {
                tvPrinterSelected.setText(getString(R.string.settings_selected_printer, name, mac));
            } else {
                tvPrinterSelected.setText(getString(R.string.settings_selected_printer_empty));
            }
        }

        if (switchAutoPrint != null) {
            boolean auto = com.valdker.pos.print.PrinterPrefs.isAutoPrintEnabled(requireContext());
            switchAutoPrint.setOnCheckedChangeListener(null);
            switchAutoPrint.setChecked(auto);
            switchAutoPrint.setOnCheckedChangeListener((buttonView, isChecked) ->
                    com.valdker.pos.print.PrinterPrefs.setAutoPrint(requireContext(), isChecked)
            );
        }

        int mm = com.valdker.pos.print.PrinterPrefs.getPaperWidthMm(requireContext());

        if (chipGroupPaperWidth != null) {
            chipGroupPaperWidth.setOnCheckedChangeListener(null);
        }

        if (chipPaper80 != null && chipPaper58 != null) {
            if (mm >= 80) {
                chipPaper80.setChecked(true);
            } else {
                chipPaper58.setChecked(true);
            }
        }

        if (chipGroupPaperWidth != null) {
            chipGroupPaperWidth.setOnCheckedChangeListener((group, checkedId) -> {
                if (!isAdded()) return;

                if (checkedId == R.id.chipPaper80) {
                    com.valdker.pos.print.PrinterPrefs.setPaperWidthMm(requireContext(), 80);
                    Toast.makeText(requireContext(), getString(R.string.msg_paper_width_set_80), Toast.LENGTH_SHORT).show();
                } else if (checkedId == R.id.chipPaper58) {
                    com.valdker.pos.print.PrinterPrefs.setPaperWidthMm(requireContext(), 58);
                    Toast.makeText(requireContext(), getString(R.string.msg_paper_width_set_58), Toast.LENGTH_SHORT).show();
                }
            });
        }

        boolean connectedLike = false;
        try {
            connectedLike = com.valdker.pos.print.PrinterService.isPrinterReachable(requireContext());
        } catch (Exception ignored) {
        }

        if (dot != null && tvPrinterStatus != null) {
            dot.setBackgroundResource(connectedLike ? R.drawable.bg_status_dot_green : R.drawable.bg_status_dot_red);
            tvPrinterStatus.setText(connectedLike
                    ? getString(R.string.settings_printer_connected)
                    : getString(R.string.settings_printer_not_connected));
        }

        if (btnOpenPrinterSettings != null) {
            btnOpenPrinterSettings.setOnClickListener(v -> {
                if (!isAdded()) return;
                startActivity(new Intent(requireContext(),
                        com.valdker.pos.print.PrinterSettingsActivity.class));
            });
        }

        if (btnTestPrint != null) {
            btnTestPrint.setOnClickListener(v -> doTestPrint());
        }
    }

    private void doTestPrint() {
        if (!isAdded()) return;

        if (!com.valdker.pos.print.PrinterService.hasBtPermission(requireContext())) {
            Toast.makeText(
                    requireContext(),
                    getString(R.string.msg_bluetooth_permission_required),
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        String demo =
                "[C]<b>" + getString(R.string.app_name) + "</b>\n" +
                        "[C]-------------------------------\n" +
                        "[L]" + getString(R.string.settings_test_print) + "\n" +
                        "[L]" + getString(R.string.settings_printer_ok) + " ✅\n" +
                        "[C]-------------------------------\n" +
                        "[L]" + getString(R.string.label_paper_width) + ": " +
                        com.valdker.pos.print.PrinterPrefs.getPaperWidthMm(requireContext()) +
                        "mm\n" +
                        "[C]\n\n\n";

        try {
            com.valdker.pos.print.PrinterService.printText(requireContext(), demo);
            Toast.makeText(requireContext(), getString(R.string.msg_test_print_success), Toast.LENGTH_SHORT).show();
            setupPrinterUi();
        } catch (Exception e) {
            Toast.makeText(requireContext(), getString(R.string.msg_test_print_failed, e.getMessage()), Toast.LENGTH_LONG).show();
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
            Toast.makeText(requireContext(), getString(R.string.msg_missing_token_login_again), Toast.LENGTH_LONG).show();
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
                Toast.makeText(requireContext(), getString(R.string.msg_no_shop_found_server), Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(@NonNull String message) {
                if (!isAdded()) return;
                setLoading(false);
                Toast.makeText(requireContext(), getString(R.string.msg_failed_load_shop, message), Toast.LENGTH_LONG).show();
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
            Toast.makeText(requireContext(), getString(R.string.msg_shop_not_loaded), Toast.LENGTH_SHORT).show();
            return;
        }

        String name = etName != null ? etName.getText().toString().trim() : "";
        String address = etAddress != null ? etAddress.getText().toString().trim() : "";
        String phone = etPhone != null ? etPhone.getText().toString().trim() : "";
        String email = etEmail != null ? etEmail.getText().toString().trim() : "";

        if (name.isEmpty() || address.isEmpty() || phone.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.msg_name_address_phone_required), Toast.LENGTH_LONG).show();
            return;
        }

        SessionManager sm = new SessionManager(requireContext());
        String token = sm.getToken();

        if (token == null || token.trim().isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.msg_missing_token_login_again), Toast.LENGTH_LONG).show();
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

                        Toast.makeText(requireContext(), getString(R.string.msg_shop_saved), Toast.LENGTH_SHORT).show();
                        sendShopUpdatedBroadcast(requireContext());
                    }

                    @Override
                    public void onError(int statusCode, @NonNull String message) {
                        if (!isAdded()) return;
                        setLoading(false);
                        Toast.makeText(
                                requireContext(),
                                getString(R.string.msg_save_failed_with_code, statusCode, message),
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
        if (ivHeaderAction != null) {
            ivHeaderAction.setEnabled(!loading);
            ivHeaderAction.setAlpha(loading ? 0.5f : 1f);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        isLanguageChanging = false;
        isBindingLanguageSpinner = false;
        spinnerLanguage = null;

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

        btnBack = null;
        ivHeaderAction = null;
        tvTopTitle = null;
    }
}