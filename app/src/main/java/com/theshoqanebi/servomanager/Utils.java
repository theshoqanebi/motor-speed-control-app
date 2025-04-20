package com.theshoqanebi.servomanager;

import static android.app.Activity.RESULT_OK;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.Editable;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {

    public static String capitalize(Editable e) {
        String s = e == null ? null : e.toString();
        if (s == null || s.isEmpty()) return s;
        return s.toUpperCase(Locale.ENGLISH).trim();
    }

    public static boolean isValidMAC(String mac) {
        String regex = "^([0-9A-Fa-f]{2}[:\\-]){5}[0-9A-Fa-f]{2}$";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(mac);
        return matcher.matches();
    }

    public static boolean isBluetoothEnabled() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            return false;
        } else {
            return bluetoothAdapter.isEnabled();
        }
    }

    public static void enableBluetooth(ComponentActivity activity) {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        ActivityResultLauncher<Intent> bluetoothLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        Toast.makeText(activity, "Bluetooth enabled", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(activity, "Bluetooth not enabled", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        if (bluetoothAdapter == null) {
            Toast.makeText(activity, "Bluetooth not supported", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            bluetoothLauncher.launch(enableBtIntent);
        } else {
            Toast.makeText(activity, "Bluetooth already enabled", Toast.LENGTH_SHORT).show();
        }
    }

    public static void requestPermission(ComponentActivity activity, String[] permissions) {
        activity.registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            boolean all = true;
            for (String permission : permissions) {
                if (!Boolean.TRUE.equals(result.getOrDefault(permission, false))) {
                    all = false;
                    break;
                }
            }
            if (all) {
                Toast.makeText(activity, "All permissions granted!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(activity, "Please grant all permissions for proper functionality.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    public static boolean isPermitted(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        } else {
            return true;
        }
    }
}
