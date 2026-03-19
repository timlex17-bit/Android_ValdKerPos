package com.example.valdker.workshop;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.valdker.R;
import com.example.valdker.SessionManager;
import com.example.valdker.cart.CartManager;
import com.example.valdker.models.CartItem;
import com.example.valdker.models.Product;
import com.example.valdker.repositories.CheckoutConfigRepository;
import com.example.valdker.repositories.OrderRepository;
import com.example.valdker.repositories.ProductRepository;
import com.example.valdker.ui.checkout.BankAccountItem;
import com.example.valdker.ui.checkout.NativeCheckoutDialogFragment;
import com.example.valdker.ui.checkout.PaymentMethodItem;
import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class WorkshopPOSFragment extends Fragment
        implements WorkshopWorkspaceAdapter.Listener, CartManager.Listener {

    public interface WorkshopHostActions {
        void openChangePasswordFromUserMenu();
        void openPrivacyPolicyFromUserMenu();
        void requestCloseShiftFromUserMenu();
        void requestLogoutFromUserMenu();
    }

    public static final String REQUEST_KEY_WORKSHOP_ITEM = "request_workshop_item";
    public static final String BUNDLE_KEY_ITEM_ID = "item_id";
    public static final String BUNDLE_KEY_ITEM_NAME = "item_name";
    public static final String BUNDLE_KEY_ITEM_PRICE = "item_price";
    public static final String BUNDLE_KEY_ITEM_TYPE = "item_type";
    public static final String BUNDLE_KEY_ITEM_SKU = "item_sku";
    public static final String BUNDLE_KEY_ITEM_BARCODE = "item_barcode";
    public static final String BUNDLE_KEY_ITEM_IMAGE = "item_image";
    public static final String BUNDLE_KEY_ITEM_STOCK = "item_stock";

    private ProductRepository productRepository;
    private OrderRepository orderRepository;
    private CheckoutConfigRepository checkoutConfigRepository;
    private SessionManager sessionManager;
    private WorkshopHostActions hostActions;

    private final List<Product> workshopProducts = new ArrayList<>();
    private boolean productsLoaded = false;
    private boolean productsLoading = false;
    private boolean checkoutSubmitting = false;

    private final List<PaymentMethodItem> checkoutPaymentMethods = new ArrayList<>();
    private final List<BankAccountItem> checkoutBankAccounts = new ArrayList<>();
    private boolean checkoutConfigLoaded = false;
    private boolean checkoutConfigLoading = false;

    private TextView txtCustomerName;
    private TextView txtVehicleName;
    private TextView txtPlateNumber;
    private TextView txtTransactionStatus;

    private android.widget.ImageView imgLogo;
    private TextView tvBrand;
    private TextView tvShopAddress;

    private TextView txtServiceTotal;
    private TextView txtPartsTotal;
    private TextView txtProductTotal;
    private TextView txtGrandTotal;

    private RecyclerView recyclerWorkspace;
    private MaterialButton btnSelectCustomer;
    private MaterialButton btnAddService;
    private MaterialButton btnAddPart;
    private MaterialButton btnAddProduct;
    private MaterialButton btnCheckout;
    private ImageButton btnUser;

    private WorkshopWorkspaceAdapter adapter;
    private CartManager cartManager;

    private final WorkshopHeader header = new WorkshopHeader();
    private final List<WorkshopCartItem> cartItems = new ArrayList<>();

    public static WorkshopPOSFragment newInstance() {
        return new WorkshopPOSFragment();
    }

    public WorkshopPOSFragment() {
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof WorkshopHostActions) {
            hostActions = (WorkshopHostActions) context;
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        hostActions = null;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_workshop_pos, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        cartManager = CartManager.getInstance(requireContext());
        sessionManager = new SessionManager(requireContext());
        productRepository = new ProductRepository(requireContext());
        orderRepository = new OrderRepository(requireContext());
        checkoutConfigRepository = new CheckoutConfigRepository(requireContext());

        bindViews(view);
        setupHeader();
        setupRecycler();
        setupButtons();
        setupUserMenu();
        setupFragmentResults();
        loadCartFromManager();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (cartManager != null) {
            cartManager.addListener(this);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (cartManager != null) {
            cartManager.removeListener(this);
        }
    }

    private void bindViews(@NonNull View view) {
        imgLogo = view.findViewById(R.id.imgLogo);
        tvBrand = view.findViewById(R.id.tvBrand);
        tvShopAddress = view.findViewById(R.id.tvShopAddress);

        txtCustomerName = view.findViewById(R.id.txtCustomerName);
        txtVehicleName = view.findViewById(R.id.txtVehicleName);
        txtPlateNumber = view.findViewById(R.id.txtPlateNumber);
        txtTransactionStatus = view.findViewById(R.id.txtTransactionStatus);

        txtServiceTotal = view.findViewById(R.id.txtServiceTotal);
        txtPartsTotal = view.findViewById(R.id.txtPartsTotal);
        txtProductTotal = view.findViewById(R.id.txtProductTotal);
        txtGrandTotal = view.findViewById(R.id.txtGrandTotal);

        recyclerWorkspace = view.findViewById(R.id.recyclerWorkspace);

        btnSelectCustomer = view.findViewById(R.id.btnSelectCustomer);
        btnAddService = view.findViewById(R.id.btnAddService);
        btnAddPart = view.findViewById(R.id.btnAddPart);
        btnAddProduct = view.findViewById(R.id.btnAddProduct);
        btnCheckout = view.findViewById(R.id.btnCheckout);
        btnUser = view.findViewById(R.id.btnUser);
    }

    private void setupHeader() {
        if (TextUtils.isEmpty(header.customerName)) {
            header.customerName = "Walk-in Customer";
        }
        if (TextUtils.isEmpty(header.vehicleName)) {
            header.vehicleName = "-";
        }
        if (TextUtils.isEmpty(header.plateNumber)) {
            header.plateNumber = "-";
        }
        if (TextUtils.isEmpty(header.status)) {
            header.status = "Draft";
        }

        if (tvBrand != null) {
            tvBrand.setText(safeText(sessionManager.getShopName(), "Shop"));
        }

        if (tvShopAddress != null) {
            tvShopAddress.setText(safeText(sessionManager.getShopAddress(), "-"));
        }

        if (imgLogo != null) {
            String logoUrl = sessionManager.getShopLogo();

            if (!TextUtils.isEmpty(logoUrl)) {
                Glide.with(this)
                        .load(logoUrl)
                        .placeholder(R.drawable.bg_logo_circle)
                        .error(R.drawable.bg_logo_circle)
                        .circleCrop()
                        .into(imgLogo);
            } else {
                imgLogo.setImageResource(R.drawable.bg_logo_circle);
            }
        }

        updateHeaderUI();
    }

    private void updateHeaderUI() {
        if (txtCustomerName != null) {
            txtCustomerName.setText(safeText(header.customerName, "Walk-in Customer"));
        }
        if (txtVehicleName != null) {
            txtVehicleName.setText(safeText(header.vehicleName, "-"));
        }
        if (txtPlateNumber != null) {
            txtPlateNumber.setText(safeText(header.plateNumber, "-"));
        }
        if (txtTransactionStatus != null) {
            txtTransactionStatus.setText(safeText(header.status, "Draft"));
        }
    }

    @NonNull
    private String safeText(@Nullable String value, @NonNull String fallback) {
        return TextUtils.isEmpty(value) ? fallback : value;
    }

    private void setupRecycler() {
        adapter = new WorkshopWorkspaceAdapter(this);
        recyclerWorkspace.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerWorkspace.setAdapter(adapter);
    }

    private void setupButtons() {
        btnSelectCustomer.setOnClickListener(v -> {
            if (!ensureShiftIsOpen()) return;
            openCustomerPicker();
        });

        btnAddService.setOnClickListener(v -> {
            if (!ensureShiftIsOpen()) return;
            openWorkshopItemPicker(WorkshopCartItem.TYPE_SERVICE);
        });

        btnAddPart.setOnClickListener(v -> {
            if (!ensureShiftIsOpen()) return;
            openWorkshopItemPicker(WorkshopCartItem.TYPE_PART);
        });

        btnAddProduct.setOnClickListener(v -> {
            if (!ensureShiftIsOpen()) return;
            openWorkshopItemPicker(WorkshopCartItem.TYPE_PRODUCT);
        });

        btnCheckout.setOnClickListener(v -> {
            if (!ensureShiftIsOpen()) return;
            handleCheckout();
        });
    }

    private void setupUserMenu() {
        if (btnUser != null) {
            btnUser.setOnClickListener(this::showUserPopup);
        }
    }

    private void showUserPopup(@NonNull View anchor) {
        Context context = requireContext();

        View popupView = LayoutInflater.from(context).inflate(R.layout.popup_user_menu, null, false);

        TextView tvUserName = popupView.findViewById(R.id.tvUserName);
        TextView tvUserRole = popupView.findViewById(R.id.tvUserRole);

        TextView btnChangePassword = popupView.findViewById(R.id.btnChangePassword);
        TextView btnPrivacy = popupView.findViewById(R.id.btnPrivacy);
        TextView btnCloseShift = popupView.findViewById(R.id.btnCloseShift);
        TextView btnLogout = popupView.findViewById(R.id.btnLogout);

        String fullName = sessionManager.getFullName();
        String username = sessionManager.getUsername();
        String role = sessionManager.getRole();

        if (TextUtils.isEmpty(fullName)) {
            fullName = username;
        }
        if (TextUtils.isEmpty(fullName)) {
            fullName = "User";
        }
        if (TextUtils.isEmpty(role)) {
            role = "cashier";
        }

        tvUserName.setText(fullName);
        tvUserRole.setText(role);

        final PopupWindow popupWindow = new PopupWindow(
                popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );

        popupWindow.setElevation(12f);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setFocusable(true);

        popupView.measure(
                View.MeasureSpec.UNSPECIFIED,
                View.MeasureSpec.UNSPECIFIED
        );

        int popupWidth = popupView.getMeasuredWidth();

        anchor.post(() -> {
            int xOff = -(popupWidth - anchor.getWidth());
            popupWindow.showAsDropDown(anchor, xOff, 12);
        });

        btnChangePassword.setOnClickListener(v -> {
            popupWindow.dismiss();
            if (hostActions != null) {
                hostActions.openChangePasswordFromUserMenu();
            } else {
                Toast.makeText(requireContext(), "Change Password unavailable", Toast.LENGTH_SHORT).show();
            }
        });

        btnPrivacy.setOnClickListener(v -> {
            popupWindow.dismiss();
            if (hostActions != null) {
                hostActions.openPrivacyPolicyFromUserMenu();
            } else {
                Toast.makeText(requireContext(), "Privacy Policy unavailable", Toast.LENGTH_SHORT).show();
            }
        });

        btnCloseShift.setOnClickListener(v -> {
            popupWindow.dismiss();
            if (hostActions != null) {
                hostActions.requestCloseShiftFromUserMenu();
            } else {
                Toast.makeText(requireContext(), "Close Shift unavailable", Toast.LENGTH_SHORT).show();
            }
        });

        btnLogout.setOnClickListener(v -> {
            popupWindow.dismiss();
            if (hostActions != null) {
                hostActions.requestLogoutFromUserMenu();
            } else {
                Toast.makeText(requireContext(), "Logout unavailable", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupFragmentResults() {
        getParentFragmentManager().setFragmentResultListener(
                REQUEST_KEY_WORKSHOP_ITEM,
                getViewLifecycleOwner(),
                (requestKey, result) -> {
                    int itemId = result.getInt(BUNDLE_KEY_ITEM_ID, -1);
                    String itemName = result.getString(BUNDLE_KEY_ITEM_NAME, "");
                    double price = result.getDouble(BUNDLE_KEY_ITEM_PRICE, 0d);
                    String itemType = result.getString(BUNDLE_KEY_ITEM_TYPE, WorkshopCartItem.TYPE_PRODUCT);
                    String sku = result.getString(BUNDLE_KEY_ITEM_SKU, "");
                    String barcode = result.getString(BUNDLE_KEY_ITEM_BARCODE, "");
                    String image = result.getString(BUNDLE_KEY_ITEM_IMAGE, "");
                    int stock = result.getInt(BUNDLE_KEY_ITEM_STOCK, 0);

                    if (itemId <= 0 || TextUtils.isEmpty(itemName)) {
                        Toast.makeText(requireContext(), "Item workshop tidak valid", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    addSelectedItemToCart(
                            itemId,
                            0,
                            itemName,
                            itemType,
                            price,
                            sku,
                            barcode,
                            image,
                            stock
                    );
                }
        );
    }

    private void openWorkshopItemPicker(String itemType) {
        ensureWorkshopProductsLoaded(() -> showWorkshopItemPicker(itemType));
    }

    private void ensureWorkshopProductsLoaded(@NonNull Runnable onReady) {
        if (productsLoaded) {
            onReady.run();
            return;
        }

        if (productsLoading) {
            Toast.makeText(requireContext(), "Sedang memuat item...", Toast.LENGTH_SHORT).show();
            return;
        }

        productsLoading = true;

        String token = sessionManager.getToken();
        if (token == null || token.trim().isEmpty()) {
            productsLoading = false;
            Toast.makeText(requireContext(), "Token login tidak ditemukan", Toast.LENGTH_SHORT).show();
            return;
        }

        productRepository.fetchProducts(token, new ProductRepository.Callback() {
            @Override
            public void onSuccess(@NonNull List<Product> products) {
                if (!isAdded()) return;

                productsLoading = false;
                productsLoaded = true;

                workshopProducts.clear();
                workshopProducts.addAll(products);

                onReady.run();
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                if (!isAdded()) return;

                productsLoading = false;
                productsLoaded = false;

                Toast.makeText(
                        requireContext(),
                        "Gagal memuat item: " + message,
                        Toast.LENGTH_LONG
                ).show();
            }
        });
    }

    private boolean ensureShiftIsOpen() {
        boolean shiftOpen = sessionManager != null && sessionManager.isShiftOpen();

        if (!shiftOpen) {
            Toast.makeText(requireContext(),
                    "Shift belum dibuka. Silakan open shift terlebih dahulu.",
                    Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private void showWorkshopItemPicker(@NonNull String itemType) {
        List<Product> filtered = new ArrayList<>();
        String normalizedTarget = CartItem.normalizeItemType(itemType);

        for (Product product : workshopProducts) {
            if (product == null) continue;
            if (!product.isActive) continue;

            String normalizedProductType = CartItem.normalizeItemType(product.itemType);
            if (TextUtils.equals(normalizedTarget, normalizedProductType)) {
                filtered.add(product);
            }
        }

        if (filtered.isEmpty()) {
            String label;
            switch (normalizedTarget) {
                case CartItem.ITEM_TYPE_SERVICE:
                    label = "service";
                    break;
                case CartItem.ITEM_TYPE_PART:
                    label = "part";
                    break;
                case CartItem.ITEM_TYPE_PRODUCT:
                default:
                    label = "product";
                    break;
            }

            Toast.makeText(requireContext(), "Belum ada data " + label, Toast.LENGTH_SHORT).show();
            return;
        }

        String title;
        switch (normalizedTarget) {
            case CartItem.ITEM_TYPE_SERVICE:
                title = "Pilih Service";
                break;
            case CartItem.ITEM_TYPE_PART:
                title = "Pilih Part";
                break;
            case CartItem.ITEM_TYPE_PRODUCT:
            default:
                title = "Pilih Product";
                break;
        }

        String[] labels = new String[filtered.size()];
        for (int i = 0; i < filtered.size(); i++) {
            Product p = filtered.get(i);

            String stockText = "";
            if (!CartItem.ITEM_TYPE_SERVICE.equals(normalizedTarget)) {
                stockText = " • Stock: " + p.stock;
            }

            labels[i] = safeText(p.name, "-") + " • " + formatMoney(p.price) + stockText;
        }

        new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setItems(labels, (dialog, which) -> {
                    Product selected = filtered.get(which);

                    int itemId;
                    try {
                        itemId = Integer.parseInt(selected.id);
                    } catch (Exception e) {
                        Toast.makeText(requireContext(), "ID item tidak valid", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    int shopId = selected.shopId > 0
                            ? selected.shopId
                            : (selected.shop_id > 0 ? selected.shop_id : sessionManager.getShopId());

                    addSelectedItemToCart(
                            itemId,
                            shopId,
                            safeText(selected.name, ""),
                            normalizedTarget,
                            selected.price,
                            selected.sku,
                            selected.barcode,
                            !TextUtils.isEmpty(selected.imageUrl) ? selected.imageUrl : selected.image_url,
                            selected.stock
                    );
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void addSelectedItemToCart(int itemId,
                                       int shopId,
                                       @NonNull String itemName,
                                       @NonNull String itemType,
                                       double price,
                                       @Nullable String sku,
                                       @Nullable String barcode,
                                       @Nullable String imageUrl,
                                       int stock) {
        if (cartManager == null) return;

        String normalizedType = WorkshopCartItem.normalizeItemType(itemType);

        if (WorkshopCartItem.TYPE_PART.equals(normalizedType)
                || WorkshopCartItem.TYPE_PRODUCT.equals(normalizedType)) {
            if (stock <= 0) {
                Toast.makeText(requireContext(), "Stock item kosong", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        CartItem existing = findExistingCartItem(itemId, normalizedType);

        if (existing != null) {
            int nextQty = existing.qty + 1;

            if ((WorkshopCartItem.TYPE_PART.equals(normalizedType)
                    || WorkshopCartItem.TYPE_PRODUCT.equals(normalizedType))
                    && stock > 0
                    && nextQty > stock) {
                Toast.makeText(requireContext(), "Qty melebihi stock tersedia", Toast.LENGTH_SHORT).show();
                return;
            }

            cartManager.setQty(existing.productId, nextQty);
        } else {
            CartItem cartItem = new CartItem();
            cartItem.productId = itemId;
            cartItem.shopId = shopId;
            cartItem.name = itemName;
            cartItem.price = price;
            cartItem.imageUrl = imageUrl != null ? imageUrl : "";
            cartItem.qty = 1;
            cartItem.orderType = "";
            cartItem.itemType = CartItem.normalizeItemType(normalizedType);
            cartManager.add(cartItem);
        }

        Toast.makeText(requireContext(), itemName + " ditambahkan", Toast.LENGTH_SHORT).show();
        loadCartFromManager();
    }

    @Nullable
    private CartItem findExistingCartItem(int productId, @NonNull String itemType) {
        if (cartManager == null) return null;

        List<CartItem> items = cartManager.getItems();
        if (items == null) return null;

        String normalizedType = CartItem.normalizeItemType(itemType);

        for (CartItem item : items) {
            if (item.productId == productId
                    && TextUtils.equals(CartItem.normalizeItemType(item.itemType), normalizedType)) {
                return item;
            }
        }
        return null;
    }

    private void openCustomerPicker() {
        CustomerPickerDialog dialog = CustomerPickerDialog.newInstance();

        dialog.setListener(customer -> {
            if (customer.id > 0) {
                header.customerId = customer.id;
                header.customerName = customer.name;
            } else {
                header.customerId = 0;
                header.customerName = "Walk-in Customer";
            }

            updateHeaderUI();
            openVehicleInput();
        });

        dialog.show(getParentFragmentManager(), "customer_picker");
    }

    private void openVehicleInput() {
        VehicleInputDialog dialog = VehicleInputDialog.newInstance();

        dialog.setListener((vehicle, plate) -> {
            header.vehicleName = vehicle;
            header.plateNumber = plate;
            updateHeaderUI();
        });

        dialog.show(getParentFragmentManager(), "vehicle_input");
    }

    private void loadCartFromManager() {
        if (cartManager == null) return;

        List<CartItem> rawItems = cartManager.getItems();
        if (rawItems == null) rawItems = new ArrayList<>();

        cartItems.clear();
        for (CartItem item : rawItems) {
            cartItems.add(WorkshopCartItem.fromCartItem(item));
        }

        refreshWorkspace();
    }

    private void refreshWorkspace() {
        if (adapter != null) {
            adapter.submitList(buildDisplayItems());
        }
        updateSummary();
    }

    private List<WorkshopDisplayItem> buildDisplayItems() {
        List<WorkshopDisplayItem> result = new ArrayList<>();

        List<WorkshopCartItem> services = new ArrayList<>();
        List<WorkshopCartItem> parts = new ArrayList<>();
        List<WorkshopCartItem> products = new ArrayList<>();

        for (WorkshopCartItem item : cartItems) {
            if (TextUtils.equals(item.getItemType(), WorkshopCartItem.TYPE_SERVICE)) {
                services.add(item);
            } else if (TextUtils.equals(item.getItemType(), WorkshopCartItem.TYPE_PART)) {
                parts.add(item);
            } else {
                products.add(item);
            }
        }

        if (!services.isEmpty()) {
            result.add(WorkshopDisplayItem.createHeader("Services"));
            for (WorkshopCartItem item : services) {
                result.add(WorkshopDisplayItem.createItem(item));
            }
        }

        if (!parts.isEmpty()) {
            result.add(WorkshopDisplayItem.createHeader("Parts"));
            for (WorkshopCartItem item : parts) {
                result.add(WorkshopDisplayItem.createItem(item));
            }
        }

        if (!products.isEmpty()) {
            result.add(WorkshopDisplayItem.createHeader("Products"));
            for (WorkshopCartItem item : products) {
                result.add(WorkshopDisplayItem.createItem(item));
            }
        }

        return result;
    }

    private void updateSummary() {
        double serviceTotal = 0;
        double partsTotal = 0;
        double productTotal = 0;

        for (WorkshopCartItem item : cartItems) {
            switch (item.getItemType()) {
                case WorkshopCartItem.TYPE_SERVICE:
                    serviceTotal += item.getLineTotal();
                    break;
                case WorkshopCartItem.TYPE_PART:
                    partsTotal += item.getLineTotal();
                    break;
                case WorkshopCartItem.TYPE_PRODUCT:
                default:
                    productTotal += item.getLineTotal();
                    break;
            }
        }

        double grandTotal = serviceTotal + partsTotal + productTotal;

        if (txtServiceTotal != null) txtServiceTotal.setText(formatMoney(serviceTotal));
        if (txtPartsTotal != null) txtPartsTotal.setText(formatMoney(partsTotal));
        if (txtProductTotal != null) txtProductTotal.setText(formatMoney(productTotal));
        if (txtGrandTotal != null) txtGrandTotal.setText(formatMoney(grandTotal));
    }

    private double getCartGrandTotal() {
        double total = 0.0;
        for (WorkshopCartItem item : cartItems) {
            total += item.getLineTotal();
        }
        return total;
    }

    private void handleCheckout() {
        if (checkoutSubmitting) {
            Toast.makeText(requireContext(), "Checkout sedang diproses...", Toast.LENGTH_SHORT).show();
            return;
        }

        if (cartItems.isEmpty()) {
            Toast.makeText(requireContext(), "Workspace masih kosong", Toast.LENGTH_SHORT).show();
            return;
        }

        ensureCheckoutConfigLoaded(this::openCheckoutDialog);
    }

    private void ensureCheckoutConfigLoaded(@NonNull Runnable onReady) {
        if (checkoutConfigLoaded && !checkoutPaymentMethods.isEmpty()) {
            onReady.run();
            return;
        }

        if (checkoutConfigLoading) {
            Toast.makeText(requireContext(), "Sedang memuat metode pembayaran...", Toast.LENGTH_SHORT).show();
            return;
        }

        if (sessionManager == null) {
            Toast.makeText(requireContext(), "Session belum siap", Toast.LENGTH_SHORT).show();
            return;
        }

        String token = sessionManager.getToken();
        if (token == null || token.trim().isEmpty()) {
            Toast.makeText(requireContext(), "Token login tidak ditemukan", Toast.LENGTH_SHORT).show();
            return;
        }

        checkoutConfigLoading = true;
        checkoutPaymentMethods.clear();
        checkoutBankAccounts.clear();

        checkoutConfigRepository.fetchPaymentMethods(token, new CheckoutConfigRepository.PaymentMethodsCallback() {
            @Override
            public void onSuccess(@NonNull List<PaymentMethodItem> items) {
                if (!isAdded()) return;

                checkoutPaymentMethods.clear();
                for (PaymentMethodItem item : items) {
                    if (item != null && item.is_active) {
                        checkoutPaymentMethods.add(item);
                    }
                }

                checkoutConfigRepository.fetchBankAccounts(token, new CheckoutConfigRepository.BankAccountsCallback() {
                    @Override
                    public void onSuccess(@NonNull List<BankAccountItem> items) {
                        if (!isAdded()) return;

                        checkoutConfigLoading = false;
                        checkoutConfigLoaded = true;

                        checkoutBankAccounts.clear();
                        for (BankAccountItem item : items) {
                            if (item != null && item.is_active) {
                                checkoutBankAccounts.add(item);
                            }
                        }

                        if (checkoutPaymentMethods.isEmpty()) {
                            Toast.makeText(requireContext(),
                                    "Metode pembayaran aktif tidak tersedia",
                                    Toast.LENGTH_LONG).show();
                            return;
                        }

                        onReady.run();
                    }

                    @Override
                    public void onError(int statusCode, @NonNull String message) {
                        if (!isAdded()) return;

                        checkoutConfigLoading = false;
                        checkoutConfigLoaded = true;

                        checkoutBankAccounts.clear();

                        if (checkoutPaymentMethods.isEmpty()) {
                            Toast.makeText(requireContext(),
                                    "Gagal memuat metode pembayaran: " + message,
                                    Toast.LENGTH_LONG).show();
                            return;
                        }

                        onReady.run();
                    }
                });
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                if (!isAdded()) return;

                checkoutConfigLoading = false;
                checkoutConfigLoaded = false;

                Toast.makeText(requireContext(),
                        "Gagal memuat metode pembayaran: " + message,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void openCheckoutDialog() {
        double total = getCartGrandTotal();

        boolean needTable = false;
        boolean needDelivery = false;

        NativeCheckoutDialogFragment dialog =
                NativeCheckoutDialogFragment.newInstance(total, needTable, needDelivery);

        List<NativeCheckoutDialogFragment.PaymentMethodOption> paymentOptions = new ArrayList<>();
        for (PaymentMethodItem pm : checkoutPaymentMethods) {
            if (pm == null || !pm.is_active) continue;

            paymentOptions.add(new NativeCheckoutDialogFragment.PaymentMethodOption(
                    pm.id,
                    safeText(pm.code, ""),
                    safeText(pm.name, "Payment"),
                    pm.requires_bank_account
            ));
        }

        List<NativeCheckoutDialogFragment.BankAccountOption> bankOptions = new ArrayList<>();
        for (BankAccountItem ba : checkoutBankAccounts) {
            if (ba == null || !ba.is_active) continue;

            String label;
            if (!TextUtils.isEmpty(ba.account_number)) {
                label = safeText(ba.bank_name, "Bank") + " - "
                        + safeText(ba.name, "Account") + " ("
                        + safeText(ba.account_number, "-") + ")";
            } else {
                label = safeText(ba.bank_name, "Bank") + " - "
                        + safeText(ba.name, "Account");
            }

            bankOptions.add(new NativeCheckoutDialogFragment.BankAccountOption(
                    ba.id,
                    label
            ));
        }

        dialog.setPaymentOptions(paymentOptions);
        dialog.setBankOptions(bankOptions);

        dialog.setBankListener(new NativeCheckoutDialogFragment.BankListener() {
            @Override
            public void onConfirmBank(@NonNull NativeCheckoutDialogFragment.BankCheckoutResult result) {
                handleCheckoutBank(result);
            }
        });

        dialog.show(getParentFragmentManager(), "NativeCheckoutDialog");
    }

    private void handleCheckoutBank(@NonNull NativeCheckoutDialogFragment.BankCheckoutResult result) {
        if (orderRepository == null || sessionManager == null) {
            Toast.makeText(requireContext(), "Order repository belum siap", Toast.LENGTH_SHORT).show();
            return;
        }

        String token = sessionManager.getToken();
        if (token == null || token.trim().isEmpty()) {
            Toast.makeText(requireContext(), "Token login tidak ditemukan", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            checkoutSubmitting = true;
            setCheckoutLoading(true);

            JSONObject payload = buildWorkshopOrderPayload(
                    result.paymentMethodCode,
                    result.cashReceived,
                    result.changeAmount,
                    result.tableNumber,
                    result.deliveryAddress,
                    result.deliveryFee,
                    result.paymentMethodId,
                    result.bankAccountId,
                    result.bankAccountLabel,
                    result.referenceNumber,
                    result.paymentNote
            );

            orderRepository.createOrder(token, payload, new OrderRepository.CreateCallback() {
                @Override
                public void onSuccess(@NonNull JSONObject response) {
                    if (!isAdded()) return;

                    checkoutSubmitting = false;
                    setCheckoutLoading(false);
                    onCheckoutSubmitSuccess(response, result.paymentMethodCode);
                }

                @Override
                public void onError(int statusCode, @NonNull String message) {
                    if (!isAdded()) return;

                    checkoutSubmitting = false;
                    setCheckoutLoading(false);

                    Toast.makeText(
                            requireContext(),
                            "Gagal simpan transaksi: " + message,
                            Toast.LENGTH_LONG
                    ).show();
                }
            });

        } catch (Exception e) {
            checkoutSubmitting = false;
            setCheckoutLoading(false);
            Toast.makeText(requireContext(), "Error checkout: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @NonNull
    private JSONObject buildWorkshopOrderPayload(@NonNull String paymentMethod,
                                                 double cashReceived,
                                                 double changeAmount,
                                                 @NonNull String tableNumber,
                                                 @NonNull String deliveryAddress,
                                                 double deliveryFee,
                                                 @Nullable Integer paymentMethodId,
                                                 @Nullable Integer bankAccountId,
                                                 @Nullable String bankAccountLabel,
                                                 @Nullable String referenceNumber,
                                                 @Nullable String paymentNote) throws Exception {

        double subtotal = getCartGrandTotal();
        double discount = 0.0;
        double tax = 0.0;
        double total = subtotal + deliveryFee;

        JSONObject payload = new JSONObject();

        if (header.customerId > 0) {
            payload.put("customer", header.customerId);
        } else {
            payload.put("customer", JSONObject.NULL);
        }

        payload.put("payment_method", paymentMethod);
        payload.put("subtotal", subtotal);
        payload.put("discount", discount);
        payload.put("tax", tax);
        payload.put("total", total);

        StringBuilder notes = new StringBuilder();
        notes.append("Workshop order");
        notes.append(" | Customer: ").append(safeText(header.customerName, "Walk-in Customer"));
        notes.append(" | Vehicle: ").append(safeText(header.vehicleName, "-"));
        notes.append(" | Plate: ").append(safeText(header.plateNumber, "-"));

        if (!TextUtils.isEmpty(tableNumber)) {
            notes.append(" | Table: ").append(tableNumber);
        }

        if (!TextUtils.isEmpty(deliveryAddress)) {
            notes.append(" | Delivery: ").append(deliveryAddress);
        }

        if (deliveryFee > 0) {
            notes.append(" | Delivery Fee: ").append(formatMoney(deliveryFee));
        }

        if (!TextUtils.isEmpty(bankAccountLabel)) {
            notes.append(" | Bank: ").append(bankAccountLabel);
        }

        if (!TextUtils.isEmpty(referenceNumber)) {
            notes.append(" | Ref: ").append(referenceNumber);
        }

        if (!TextUtils.isEmpty(paymentNote)) {
            notes.append(" | Payment Note: ").append(paymentNote);
        }

        notes.append(" | Cash: ").append(formatMoney(cashReceived));
        notes.append(" | Change: ").append(formatMoney(changeAmount));

        payload.put("notes", notes.toString());

        JSONArray itemsArray = new JSONArray();
        for (WorkshopCartItem item : cartItems) {
            JSONObject itemObj = new JSONObject();
            itemObj.put("product", item.getProductId());
            itemObj.put("quantity", item.getQuantity());
            itemObj.put("price", item.getPrice());
            itemsArray.put(itemObj);
        }
        payload.put("items", itemsArray);

        JSONArray paymentsArray = new JSONArray();
        JSONObject paymentObj = new JSONObject();

        if (paymentMethodId != null && paymentMethodId > 0) {
            paymentObj.put("payment_method_id", paymentMethodId);
        } else {
            paymentObj.put("payment_method_id", JSONObject.NULL);
        }

        paymentObj.put("method_code", paymentMethod);
        paymentObj.put("amount", total);

        if (bankAccountId != null && bankAccountId > 0) {
            paymentObj.put("bank_account_id", bankAccountId);
        }

        paymentObj.put("reference_number", referenceNumber != null ? referenceNumber : "");
        paymentObj.put("note", paymentNote != null ? paymentNote : "");

        paymentsArray.put(paymentObj);
        payload.put("payments", paymentsArray);

        return payload;
    }

    private void onCheckoutSubmitSuccess(@NonNull JSONObject response, @NonNull String paymentMethod) {
        if (cartManager != null) {
            try {
                cartManager.clear();
            } catch (Exception ignored) {
            }
        }

        header.status = "Paid";
        header.vehicleName = "-";
        header.plateNumber = "-";
        header.customerId = 0;
        header.customerName = "Walk-in Customer";
        updateHeaderUI();

        loadCartFromManager();

        Toast.makeText(
                requireContext(),
                "Transaksi workshop berhasil disimpan (" + paymentMethod + ")",
                Toast.LENGTH_LONG
        ).show();
    }

    private void setCheckoutLoading(boolean loading) {
        if (btnCheckout != null) {
            btnCheckout.setEnabled(!loading);
            btnCheckout.setText(loading ? "Processing..." : "Checkout");
        }

        if (btnAddService != null) btnAddService.setEnabled(!loading);
        if (btnAddPart != null) btnAddPart.setEnabled(!loading);
        if (btnAddProduct != null) btnAddProduct.setEnabled(!loading);
        if (btnSelectCustomer != null) btnSelectCustomer.setEnabled(!loading);
        if (btnUser != null) btnUser.setEnabled(!loading);
    }

    @NonNull
    private String formatMoney(double value) {
        return String.format(Locale.US, "$%.2f", value);
    }

    @Override
    public void onIncreaseQty(WorkshopCartItem item) {
        if (cartManager == null) return;

        int nextQty = item.getQuantity() + 1;
        cartManager.setQty(item.getProductId(), nextQty);
    }

    @Override
    public void onDecreaseQty(WorkshopCartItem item) {
        if (cartManager == null) return;

        int nextQty = item.getQuantity() - 1;
        if (nextQty <= 0) {
            cartManager.remove(item.getProductId());
        } else {
            cartManager.setQty(item.getProductId(), nextQty);
        }
    }

    @Override
    public void onRemoveItem(WorkshopCartItem item) {
        if (cartManager == null) return;
        cartManager.remove(item.getProductId());
    }

    @Override
    public void onCartChanged() {
        if (!isAdded()) return;
        loadCartFromManager();
    }
}