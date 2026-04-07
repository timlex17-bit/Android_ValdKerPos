package com.valdker.pos.ui.retail;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.toolbox.StringRequest;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.valdker.pos.R;
import com.valdker.pos.SessionManager;
import com.valdker.pos.models.Shop;
import com.valdker.pos.network.ApiClient;
import com.valdker.pos.network.ApiConfig;
import com.valdker.pos.repositories.ShopRepository;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RetailPOSFragment extends Fragment {

    public interface RetailHostActions {
        void onRetailBarcodeClick();
        void onRetailCartClick();
        void onRetailUserMenuClick(@NonNull View anchor);
        void onRetailAddToCartRequested(@NonNull RetailCartItem item);
    }

    private static final String ARG_BUSINESS_TYPE = "business_type";
    private static final String ARG_USE_GRID = "use_grid";
    private static final String ARG_SHOW_IMAGES = "show_images";

    private static final String TAG_PRODUCTS = "RETAIL_PRODUCTS";
    private static final String TAG = "RETAIL_POS";

    @Nullable
    private RetailHostActions host;

    private SessionManager session;

    private String businessType = "retail";
    private boolean useGridPosLayout = false;
    private boolean showProductImagesInPos = false;

    // Header
    private ImageView imgLogo;
    private TextView tvBrand;
    private TextView tvShopAddress;
    private TextView txtTransactionStatus;
    private ImageButton btnHeaderBarcode;
    private ImageButton btnHeaderUser;
    private EditText etSearchHint;

    // Content
    private RecyclerView rvProducts;
    private TextView tvEmptyState;
    private ProgressBar progressProducts;
    private TextView txtGrandTotal;
    private TextView txtItemCount;
    private TextView txtSectionSubtitle;
    private MaterialButton btnCheckout;

    private RetailProductAdapter productAdapter;

    /** Semua produk dari API, hanya untuk pencarian barcode */
    private final List<RetailProductItem> allProducts = new ArrayList<>();

    /** Hanya 1 baris per produk */
    private final List<RetailProductItem> scannedProducts = new ArrayList<>();

    /** key = productId, value = qty hasil scan */
    private final Map<Long, Integer> scannedQtyMap = new HashMap<>();

    /** simpan nama asli supaya bisa tampil "Nama x2" */
    private final Map<Long, String> baseNameMap = new HashMap<>();

    public static RetailPOSFragment newInstance(
            @NonNull String businessType,
            boolean useGridPosLayout,
            boolean showProductImagesInPos
    ) {
        RetailPOSFragment fragment = new RetailPOSFragment();
        Bundle args = new Bundle();
        args.putString(ARG_BUSINESS_TYPE, businessType);
        args.putBoolean(ARG_USE_GRID, useGridPosLayout);
        args.putBoolean(ARG_SHOW_IMAGES, showProductImagesInPos);
        fragment.setArguments(args);
        return fragment;
    }

    public void clearAfterCheckout() {
        try {
            scannedProducts.clear();
            scannedQtyMap.clear();
            baseNameMap.clear();

            if (productAdapter != null) {
                productAdapter.setData(scannedProducts);
            }

            updateSummary();
            showEmptyState("Scan barcode to add product");
        } catch (Exception e) {
            Log.e(TAG, "Failed to clear retail cart after checkout", e);
        }
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof RetailHostActions) {
            host = (RetailHostActions) context;
        } else {
            throw new IllegalStateException("Host activity must implement RetailHostActions");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        host = null;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        session = new SessionManager(requireContext());

        Bundle args = getArguments();
        if (args != null) {
            businessType = args.getString(ARG_BUSINESS_TYPE, "retail");
            useGridPosLayout = args.getBoolean(ARG_USE_GRID, false);
            showProductImagesInPos = args.getBoolean(ARG_SHOW_IMAGES, false);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_retail_pos, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        bindViews(view);
        setupHeaderActions();
        setupSearchBox();
        setupProductRecycler();
        setupCheckoutButton();
        loadShopHeader();
        loadProducts();

        showEmptyState("Scan barcode to add product");
        updateSummary();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ApiClient.getInstance(requireContext()).cancelAll(TAG_PRODUCTS);
    }

    private void bindViews(@NonNull View root) {
        imgLogo = root.findViewById(R.id.imgLogo);
        tvBrand = root.findViewById(R.id.tvBrand);
        tvShopAddress = root.findViewById(R.id.tvShopAddress);
        txtTransactionStatus = root.findViewById(R.id.txtTransactionStatus);
        btnHeaderBarcode = root.findViewById(R.id.btnHeaderBarcode);
        etSearchHint = root.findViewById(R.id.tvSearchHint);
        btnHeaderUser = root.findViewById(R.id.btnUser);

        rvProducts = root.findViewById(R.id.rvProducts);
        tvEmptyState = root.findViewById(R.id.tvEmptyState);
        progressProducts = root.findViewById(R.id.progressProducts);
        txtGrandTotal = root.findViewById(R.id.txtGrandTotal);
        txtItemCount = root.findViewById(R.id.txtItemCount);
        txtSectionSubtitle = root.findViewById(R.id.txtSectionSubtitle);
        btnCheckout = root.findViewById(R.id.btnCheckout);
    }

    private void setupHeaderActions() {
        if (btnHeaderBarcode != null) {
            btnHeaderBarcode.setOnClickListener(v -> {
                if (host != null) {
                    host.onRetailBarcodeClick();
                }
            });
        }

        if (btnHeaderUser != null) {
            btnHeaderUser.setOnClickListener(v -> {
                if (host != null) {
                    host.onRetailUserMenuClick(v);
                }
            });
        }
    }

    private void setupSearchBox() {
        if (etSearchHint == null) return;

        etSearchHint.setOnEditorActionListener((v, actionId, event) -> {
            boolean isEnterKey = event != null
                    && event.getAction() == KeyEvent.ACTION_DOWN
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER;

            boolean isSearchAction = actionId == EditorInfo.IME_ACTION_SEARCH
                    || actionId == EditorInfo.IME_ACTION_DONE;

            if (!isEnterKey && !isSearchAction) {
                return false;
            }

            String keyword = v.getText() != null ? v.getText().toString().trim() : "";
            if (keyword.isEmpty()) return true;

            onManualSearch(keyword);
            return true;
        });
    }

    private void setupProductRecycler() {
        if (rvProducts == null) return;

        rvProducts.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvProducts.setHasFixedSize(true);

        productAdapter = new RetailProductAdapter();
        productAdapter.setShowImages(false);
        productAdapter.setListener(new RetailProductAdapter.Listener() {
            @Override
            public void onProductClick(@NonNull RetailProductItem item) {
                addProductToCart(item);
            }

            @Override
            public void onAddToCartClick(@NonNull RetailProductItem item) {
                addProductToCart(item);
            }
        });

        rvProducts.setAdapter(productAdapter);
    }

    private void setupCheckoutButton() {
        if (btnCheckout == null) return;

        btnCheckout.setEnabled(false);
        btnCheckout.setOnClickListener(v -> {
            if (host != null) {
                host.onRetailCartClick();
            }
        });
    }

    private void loadShopHeader() {
        final String token = session != null ? session.getToken() : null;

        if (token == null || token.trim().isEmpty()) {
            if (tvBrand != null) tvBrand.setText("Retail POS");
            if (tvShopAddress != null) tvShopAddress.setText("—");
            if (imgLogo != null) imgLogo.setImageResource(R.drawable.bg_logo_circle);
            return;
        }

        ShopRepository.fetchFirstShop(requireContext(), token, new ShopRepository.Callback() {
            @Override
            public void onSuccess(Shop shop) {
                if (!isAdded() || shop == null) return;

                String name = (shop.name != null && !shop.name.trim().isEmpty())
                        ? shop.name.trim()
                        : "Retail POS";

                String address = (shop.address != null && !shop.address.trim().isEmpty())
                        ? shop.address.trim()
                        : "—";

                if (tvBrand != null) tvBrand.setText(name);
                if (tvShopAddress != null) tvShopAddress.setText(address);

                String logoUrl = forceHttps(shop.logoUrl);
                if (imgLogo != null) {
                    if (logoUrl == null || logoUrl.trim().isEmpty()) {
                        imgLogo.setImageResource(R.drawable.bg_logo_circle);
                    } else {
                        Glide.with(requireContext())
                                .load(logoUrl)
                                .circleCrop()
                                .placeholder(R.drawable.bg_logo_circle)
                                .error(R.drawable.bg_logo_circle)
                                .into(imgLogo);
                    }
                }
            }

            @Override
            public void onEmpty() {
                if (!isAdded()) return;
                if (tvBrand != null) tvBrand.setText("Retail POS");
                if (tvShopAddress != null) tvShopAddress.setText("—");
                if (imgLogo != null) imgLogo.setImageResource(R.drawable.bg_logo_circle);
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                if (tvBrand != null) tvBrand.setText("Retail POS");
                if (tvShopAddress != null) tvShopAddress.setText("—");
                if (imgLogo != null) imgLogo.setImageResource(R.drawable.bg_logo_circle);
            }
        });
    }

    public void onManualSearch(@NonNull String keyword) {
        String clean = safe(keyword);
        if (clean.isEmpty()) return;

        RetailProductItem exactBarcode = findExactBarcodeMatch(clean);
        if (exactBarcode != null) {
            int qty = addOrIncrementScannedProduct(exactBarcode);
            addProductToCart(exactBarcode);

            String baseName = getBaseName(exactBarcode);
            toast(qty > 1 ? baseName + " x" + qty : baseName + " added");

            if (etSearchHint != null) {
                etSearchHint.setText("");
                etSearchHint.clearFocus();
            }
            return;
        }

        List<RetailProductItem> matches = new ArrayList<>();
        String q = clean.toLowerCase(Locale.US);

        for (RetailProductItem item : allProducts) {
            if (item == null) continue;

            String name = safe(item.name).toLowerCase(Locale.US);
            if (name.contains(q)) {
                matches.add(item);
            }
        }

        if (matches.isEmpty()) {
            toast("Product not found");
            return;
        }

        if (matches.size() == 1) {
            RetailProductItem found = matches.get(0);
            int qty = addOrIncrementScannedProduct(found);
            addProductToCart(found);

            String baseName = getBaseName(found);
            toast(qty > 1 ? baseName + " x" + qty : baseName + " added");

            if (etSearchHint != null) {
                etSearchHint.setText("");
                etSearchHint.clearFocus();
            }
            return;
        }

        toast("Found " + matches.size() + " products. Please refine search.");
    }

    public void onBarcodeScanned(@NonNull String barcode) {
        String clean = safe(barcode);
        if (clean.isEmpty()) return;

        RetailProductItem exact = findExactBarcodeMatch(clean);
        if (exact == null) {
            toast("Product not found");
            return;
        }

        int qty = addOrIncrementScannedProduct(exact);
        addProductToCart(exact);

        String baseName = getBaseName(exact);
        if (qty > 1) {
            toast(baseName + " x" + qty);
        } else {
            toast(baseName + " added");
        }
    }

    @Nullable
    private RetailProductItem findExactBarcodeMatch(@NonNull String barcode) {
        for (RetailProductItem item : allProducts) {
            if (item != null && item.matchesBarcode(barcode)) {
                return item;
            }
        }
        return null;
    }

    private int addOrIncrementScannedProduct(@NonNull RetailProductItem item) {
        final long productId = item.id;

        if (productId <= 0) {
            scannedProducts.add(item);
            if (productAdapter != null) productAdapter.setData(scannedProducts);
            hideEmptyState();
            updateSummary();
            return 1;
        }

        RetailProductItem existing = findDisplayedProductById(productId);

        if (existing == null) {
            baseNameMap.put(productId, safe(item.name));
            scannedQtyMap.put(productId, 1);
            applyDisplayName(item, 1);
            scannedProducts.add(item);
        } else {
            int newQty = getScannedQty(productId) + 1;
            scannedQtyMap.put(productId, newQty);
            applyDisplayName(existing, newQty);
        }

        if (productAdapter != null) {
            productAdapter.setData(scannedProducts);
        }

        hideEmptyState();
        updateSummary();
        return getScannedQty(productId);
    }

    @Nullable
    private RetailProductItem findDisplayedProductById(long productId) {
        for (RetailProductItem item : scannedProducts) {
            if (item != null && item.id == productId) {
                return item;
            }
        }
        return null;
    }

    private int getScannedQty(long productId) {
        Integer qty = scannedQtyMap.get(productId);
        return qty == null ? 1 : Math.max(1, qty);
    }

    public int getQtyForProduct(long productId) {
        return getScannedQty(productId);
    }

    public int getQtyForProduct(int productId) {
        return getScannedQty(productId);
    }

    @NonNull
    private String getBaseName(@NonNull RetailProductItem item) {
        String base = baseNameMap.get(item.id);
        if (base == null || base.trim().isEmpty()) {
            base = safe(item.name);
            if (base.matches(".*\\sx\\d+$")) {
                base = base.replaceAll("\\sx\\d+$", "").trim();
            }
            baseNameMap.put(item.id, base);
        }
        return base;
    }

    private void applyDisplayName(@NonNull RetailProductItem item, int qty) {
        String base = getBaseName(item);
        item.name = qty > 1 ? base + " x" + qty : base;
    }

    private void addProductToCart(@NonNull RetailProductItem item) {
        if (host == null) return;
        host.onRetailAddToCartRequested(RetailCartItem.fromProduct(item));
    }

    public int getTotalScannedQty() {
        int totalQty = 0;

        if (scannedProducts.isEmpty()) return 0;

        for (RetailProductItem item : scannedProducts) {
            if (item == null) continue;
            totalQty += getScannedQty(item.id);
        }
        return totalQty;
    }

    public double getGrandTotalAmount() {
        double total = 0d;

        for (RetailProductItem item : scannedProducts) {
            if (item != null) {
                total += (item.price * getScannedQty(item.id));
            }
        }

        return total;
    }

    public List<RetailProductItem> getScannedProducts() {
        return scannedProducts;
    }

    private void loadProducts() {
        showLoading(true);

        final String token = session.getToken();
        if (TextUtils.isEmpty(token)) {
            showLoading(false);
            showEmptyState("Token missing");
            return;
        }

        String url = ApiConfig.url(session, "api/products/");

        StringRequest req = new StringRequest(
                Request.Method.GET,
                url,
                response -> {
                    showLoading(false);
                    allProducts.clear();

                    try {
                        Object json = new JSONTokener(response).nextValue();
                        JSONArray itemsArray = null;

                        if (json instanceof JSONArray) {
                            itemsArray = (JSONArray) json;
                        } else if (json instanceof JSONObject) {
                            JSONObject obj = (JSONObject) json;
                            itemsArray = obj.optJSONArray("results");
                            if (itemsArray == null) {
                                itemsArray = obj.optJSONArray("data");
                            }
                        }

                        if (itemsArray == null) {
                            showEmptyState("Products format not supported");
                            return;
                        }

                        for (int i = 0; i < itemsArray.length(); i++) {
                            JSONObject o = itemsArray.optJSONObject(i);
                            if (o == null) continue;

                            RetailProductItem item = RetailProductItem.fromJson(o);
                            if (item.id <= 0) continue;
                            if (!item.active) continue;

                            if (item.imageUrl != null && item.imageUrl.startsWith("http://")) {
                                item.imageUrl = forceHttps(item.imageUrl);
                            }

                            allProducts.add(item);
                        }

                        if (scannedProducts.isEmpty()) {
                            showEmptyState("Scan barcode to add product");
                        } else {
                            hideEmptyState();
                        }
                        updateSummary();

                    } catch (Exception e) {
                        toast("Failed to parse products: " + e.getMessage());
                        showEmptyState("Failed to parse products");
                    }
                },
                error -> {
                    showLoading(false);

                    String detail = "Load products error";
                    if (error != null) {
                        if (error.networkResponse != null) {
                            detail += " code=" + error.networkResponse.statusCode;
                        } else if (error.getMessage() != null) {
                            detail += ": " + error.getMessage();
                        }
                    }

                    toast(detail);
                    showEmptyState("Failed to load products");
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> h = new HashMap<>();
                h.put("Accept", "application/json");
                h.put("Authorization", "Token " + token.trim());
                return h;
            }
        };

        req.setTag(TAG_PRODUCTS);
        ApiClient.getInstance(requireContext()).add(req);
    }

    private void updateSummary() {
        int itemCount = getTotalScannedQty();
        double total = getGrandTotalAmount();

        if (txtItemCount != null) {
            txtItemCount.setText(itemCount + (itemCount == 1 ? " item" : " items"));
        }

        if (txtGrandTotal != null) {
            txtGrandTotal.setText(String.format(Locale.US, "$%.2f", total));
        }

        if (txtTransactionStatus != null) {
            txtTransactionStatus.setText(itemCount > 0 ? "Ready" : "Draft");
        }

        if (txtSectionSubtitle != null) {
            txtSectionSubtitle.setText(
                    itemCount > 0
                            ? "Review scanned items before checkout"
                            : "Items will appear after barcode scan"
            );
        }

        if (btnCheckout != null) {
            btnCheckout.setEnabled(itemCount > 0);
            btnCheckout.setAlpha(itemCount > 0 ? 1f : 0.6f);
        }
    }

    private void showLoading(boolean loading) {
        if (progressProducts != null) {
            progressProducts.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        if (rvProducts != null) {
            rvProducts.setVisibility(loading ? View.INVISIBLE : View.VISIBLE);
        }
    }

    private void showEmptyState(@NonNull String message) {
        if (tvEmptyState != null) {
            tvEmptyState.setVisibility(View.VISIBLE);
            tvEmptyState.setText(message);
        }
        if (rvProducts != null) {
            rvProducts.setVisibility(View.GONE);
        }
    }

    private void hideEmptyState() {
        if (tvEmptyState != null) {
            tvEmptyState.setVisibility(View.GONE);
        }
        if (rvProducts != null && (progressProducts == null || progressProducts.getVisibility() != View.VISIBLE)) {
            rvProducts.setVisibility(View.VISIBLE);
        }
    }

    private void toast(@NonNull String message) {
        if (!isAdded()) return;
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    @NonNull
    private String safe(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    @Nullable
    private String forceHttps(@Nullable String url) {
        if (url == null) return null;
        String clean = url.trim();
        if (clean.startsWith("http://")) {
            return "https://" + clean.substring("http://".length());
        }
        return clean;
    }
}