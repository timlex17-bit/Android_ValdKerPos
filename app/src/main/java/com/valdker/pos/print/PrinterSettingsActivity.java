package com.valdker.pos.print;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.valdker.pos.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class PrinterSettingsActivity extends AppCompatActivity {

    private static final int REQ_BT_CONNECT = 5001;

    private TextView tvSelected;
    private Button btnClear;
    private ListView list;

    private final List<BluetoothDevice> devices = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_printer_settings);

        tvSelected = findViewById(R.id.tvSelectedPrinter);
        btnClear = findViewById(R.id.btnClearPrinter);
        list = findViewById(R.id.listDevices);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        list.setAdapter(adapter);

        btnClear.setOnClickListener(v -> {
            PrinterPrefs.clear(this);
            updateSelectedText();
            Toast.makeText(this, "Printer cleared", Toast.LENGTH_SHORT).show();
        });

        list.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= devices.size()) return;

            BluetoothDevice d = devices.get(position);

            // ✅ Android 12+: calling getName/getAddress requires BLUETOOTH_CONNECT
            if (!hasBtConnectPermission()) {
                requestBtConnectPermission();
                return;
            }

            try {
                String name = safeDeviceName(d);
                String mac = safeDeviceAddress(d);

                PrinterPrefs.setSelected(this, name, mac);
                updateSelectedText();

                Toast.makeText(this, "Selected: " + name, Toast.LENGTH_SHORT).show();
            } catch (SecurityException se) {
                Toast.makeText(this, "Bluetooth permission required", Toast.LENGTH_LONG).show();
            }
        });

        updateSelectedText();
        ensurePermissionThenLoad();
    }

    private void updateSelectedText() {
        String name = PrinterPrefs.getName(this);
        String mac = PrinterPrefs.getMac(this);

        if (TextUtils.isEmpty(name)) {
            tvSelected.setText("Selected printer: -");
            return;
        }

        if (TextUtils.isEmpty(mac)) {
            tvSelected.setText("Selected printer: " + name);
            return;
        }

        tvSelected.setText("Selected printer: " + name + " (" + mac + ")");
    }

    private void ensurePermissionThenLoad() {
        // ✅ Only needed on Android 12+ (S)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasBtConnectPermission()) {
                requestBtConnectPermission();
                return;
            }
        }
        loadPairedDevices();
    }

    private boolean hasBtConnectPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestBtConnectPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                    REQ_BT_CONNECT
            );
        }
    }

    private void loadPairedDevices() {
        devices.clear();
        if (adapter != null) adapter.clear();

        BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
        if (bt == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_LONG).show();
            return;
        }
        if (!bt.isEnabled()) {
            Toast.makeText(this, "Please enable Bluetooth first", Toast.LENGTH_LONG).show();
            return;
        }

        // ✅ Android 12+: protect getBondedDevices with permission + try/catch
        if (!hasBtConnectPermission()) {
            requestBtConnectPermission();
            return;
        }

        final Set<BluetoothDevice> bonded;
        try {
            bonded = bt.getBondedDevices();
        } catch (SecurityException se) {
            Toast.makeText(this, "Bluetooth permission required", Toast.LENGTH_LONG).show();
            return;
        }

        if (bonded == null || bonded.isEmpty()) {
            Toast.makeText(this, "No paired devices found", Toast.LENGTH_LONG).show();
            return;
        }

        for (BluetoothDevice d : bonded) {
            devices.add(d);

            String name;
            String addr;
            try {
                name = safeDeviceName(d);
                addr = safeDeviceAddress(d);
            } catch (SecurityException se) {
                name = "Unknown device";
                addr = "-";
            }

            if (adapter != null) {
                adapter.add(name + "\n" + addr);
            }
        }

        if (adapter != null) adapter.notifyDataSetChanged();
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private String safeDeviceName(@NonNull BluetoothDevice d) {
        String n = d.getName();
        if (n == null || n.trim().isEmpty()) return "Unknown device";
        return n.trim();
    }

    private String safeDeviceAddress(@NonNull BluetoothDevice d) {
        String a = d.getAddress();
        if (a == null || a.trim().isEmpty()) return "-";
        return a.trim();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_BT_CONNECT) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted) {
                loadPairedDevices();
            } else {
                Toast.makeText(this, "Bluetooth permission denied", Toast.LENGTH_LONG).show();
            }
        }
    }
}