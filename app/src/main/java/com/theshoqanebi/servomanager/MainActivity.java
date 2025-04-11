package com.theshoqanebi.servomanager;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.theshoqanebi.servomanager.databinding.ActivityMainBinding;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements OnConnectListener, OnDisconnectListener{
    private static final int INITIAL_SPEED = 1000;
    private static final UUID PORT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    LoadingDialog dialog;
    private static final ArrayList<String> permissions = new ArrayList<>();

    static {
        permissions.add(Manifest.permission.BLUETOOTH);
        permissions.add(Manifest.permission.BLUETOOTH_ADMIN);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    private final ActivityResultLauncher<String[]> permissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
        boolean all = true;
        for (String permission : permissions) {
            if (!Boolean.TRUE.equals(result.getOrDefault(permission, false))) {
                all = false;
                break;
            }
        }
        if (all) {
            Toast.makeText(this, "All permissions granted!", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Please grant all permissions for proper functionality.", Toast.LENGTH_SHORT).show();
        }
    });
    private boolean isRealTime = false, isDroneMode = false;
    private ActivityMainBinding binding;
    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;

    private boolean isPermitted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        } else {
            return true;
        }
    }

    private void requestPermission() {
        permissionLauncher.launch(permissions.toArray(new String[0]));
    }

    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null) {
                    MainActivity.this.runOnUiThread(() -> disconnectChanges());
                }
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.main);
        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        registerReceiver(bluetoothReceiver, filter);

        dialog = new LoadingDialog(this);

        binding.realTimeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isRealTime = isChecked;
        });

        binding.droneModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isDroneMode = isChecked;
            if (isChecked) {
                binding.oneSeekBar.setVisibility(View.GONE);
                binding.tvSpeed.setVisibility(View.GONE);
                binding.fourSeekBar.setVisibility(View.VISIBLE);
            } else {
                binding.oneSeekBar.setVisibility(View.VISIBLE);
                binding.tvSpeed.setVisibility(View.VISIBLE);
                binding.fourSeekBar.setVisibility(View.GONE);
            }
        });

        binding.increase1.setOnClickListener(this::increase);
        binding.increase2.setOnClickListener(this::increase);
        binding.increase3.setOnClickListener(this::increase);
        binding.increase4.setOnClickListener(this::increase);

        binding.decrease1.setOnClickListener(this::decrease);
        binding.decrease2.setOnClickListener(this::decrease);
        binding.decrease3.setOnClickListener(this::decrease);
        binding.decrease4.setOnClickListener(this::decrease);

        binding.increase.setOnClickListener(v -> {
            binding.seekBarSpeed.setProgress(binding.seekBarSpeed.getProgress() + 10);
            int speed = 1000 + binding.seekBarSpeed.getProgress();
            String s = "Speed: " + speed;
            binding.tvSpeed.setText(s);
            sendSpeed(speed);
        });

        binding.decrease.setOnClickListener(v -> {
            binding.seekBarSpeed.setProgress(binding.seekBarSpeed.getProgress() - 10);
            int speed = 1000 + binding.seekBarSpeed.getProgress();
            String s = "Speed: " + speed;
            binding.tvSpeed.setText(s);
            sendSpeed(speed);
        });


        binding.seekBarSpeed1.setOnSeekBarChangeListener(droneSeekBar(1, binding.motor1));
        binding.seekBarSpeed2.setOnSeekBarChangeListener(droneSeekBar(2, binding.motor2));
        binding.seekBarSpeed3.setOnSeekBarChangeListener(droneSeekBar(3, binding.motor3));
        binding.seekBarSpeed4.setOnSeekBarChangeListener(droneSeekBar(4, binding.motor4));

        init();
    }

    private void increase(View view) {
        if (view.getId() == binding.increase1.getId()) {
            binding.seekBarSpeed1.setProgress(binding.seekBarSpeed1.getProgress() + 10);
            String s = "Motor 1: " + (1000 + binding.seekBarSpeed1.getProgress());
            binding.motor1.setText(s);
        } else if (view.getId() == binding.increase2.getId()) {
            binding.seekBarSpeed2.setProgress(binding.seekBarSpeed2.getProgress() + 10);
            String s = "Motor 2: " + (1000 + binding.seekBarSpeed2.getProgress());
            binding.motor2.setText(s);
        } else if (view.getId() == binding.increase3.getId()) {
            binding.seekBarSpeed3.setProgress(binding.seekBarSpeed3.getProgress() + 10);
            String s = "Motor 3: " + (1000 + binding.seekBarSpeed3.getProgress());
            binding.motor3.setText(s);
        } else if (view.getId() == binding.increase4.getId()) {
            binding.seekBarSpeed4.setProgress(binding.seekBarSpeed4.getProgress() + 10);
            String s = "Motor 4: " + (1000 + binding.seekBarSpeed4.getProgress());
            binding.motor4.setText(s);
        }
        sendDroneInfo();
    }

    private void sendDroneInfo() {
        JSONObject json = new JSONObject();
        try {
            json.put("motor_1", 1000 + binding.seekBarSpeed1.getProgress());
            json.put("motor_2", 1000 + binding.seekBarSpeed2.getProgress());
            json.put("motor_3", 1000 + binding.seekBarSpeed3.getProgress());
            json.put("motor_4", 1000 + binding.seekBarSpeed4.getProgress());
            sendSpeed(json.toString());
        } catch (JSONException e) {
            Toast.makeText(getApplicationContext(), "Error Json parse", Toast.LENGTH_SHORT).show();
        }
    }

    private void decrease(View view) {
        if (view.getId() == binding.decrease1.getId()) {
            int newSpeed = binding.seekBarSpeed1.getProgress() - 10;
            binding.seekBarSpeed1.setProgress(newSpeed);
            String s = "Motor 1: " + (1000 + newSpeed);
            binding.motor1.setText(s);
        } else if (view.getId() == binding.decrease2.getId()) {
            int newSpeed = binding.seekBarSpeed2.getProgress() - 10;
            binding.seekBarSpeed2.setProgress(newSpeed);
            String s = "Motor 2: " + (1000 + binding.seekBarSpeed2.getProgress());
            binding.motor2.setText(s);
        } else if (view.getId() == binding.decrease3.getId()) {
            int newSpeed = binding.seekBarSpeed3.getProgress() - 10;
            binding.seekBarSpeed3.setProgress(newSpeed);
            String s = "Motor 3: " + (1000 + binding.seekBarSpeed3.getProgress());
            binding.motor3.setText(s);
        } else if (view.getId() == binding.decrease4.getId()) {
            int newSpeed = binding.seekBarSpeed4.getProgress() - 10;
            binding.seekBarSpeed4.setProgress(newSpeed);
            String s = "Motor 4: " + (1000 + binding.seekBarSpeed4.getProgress());
            binding.motor4.setText(s);
        }
        sendDroneInfo();
    }

    private SeekBar.OnSeekBarChangeListener droneSeekBar(int motorNumber, TextView textView) {
        return new SeekBar.OnSeekBarChangeListener() {
            int progressValue = INITIAL_SPEED;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                progressValue = 1000 + progress;
                String label = String.format(Locale.US, "Motor%d: %s", motorNumber, progressValue);
                textView.setText(label);
                if (isRealTime) {
                    sendDroneInfo();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                sendDroneInfo();
            }
        };
    }

    private void connectToBluetooth(OnConnectListener onConnectListener) {
        new Thread(() -> {
            String address = binding.etMacAddress.getText().toString().trim();
            if (address.isEmpty()) {
                onConnectListener.onConnectFailure("Please enter a MAC address");
                return;
            }

            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
                onConnectListener.onConnectFailure("Bluetooth not enabled");
                return;
            }

            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);

            try {
                if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    requestPermission();
                    onConnectListener.onConnectFailure("Not permitted");
                    return;
                }
                bluetoothSocket = device.createRfcommSocketToServiceRecord(PORT_UUID);
                bluetoothSocket.connect();
                outputStream = bluetoothSocket.getOutputStream();
                outputStream.flush();
                onConnectListener.onConnect();
            } catch (IOException e) {
                onConnectListener.onConnectFailure(e.getMessage());
            }
        }).start();
    }

    private void disconnectBluetooth(OnDisconnectListener onDisconnectListener) {
        new Thread(() -> {
            if (bluetoothSocket != null) {
                try {
                    bluetoothSocket.close();
                    onDisconnectListener.onDisconnect();
                } catch (IOException e) {
                    onDisconnectListener.onDisconnectFailure(e.getMessage());
                }
            }
        }).start();

    }

    private void sendSpeed(int speed) {
        if (outputStream != null) {
            try {
                String message = speed + "\n";
                outputStream.write(message.getBytes());
                outputStream.flush();
            } catch (IOException e) {
                e.printStackTrace(System.err);
                Toast.makeText(this, "Send Failed", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void sendSpeed(String speed) {
        if (outputStream != null) {
            try {
                String message = speed + "\n";
                outputStream.write(message.getBytes());
                outputStream.flush();
            } catch (IOException e) {
                e.printStackTrace(System.err);
                Toast.makeText(this, "Send Failed", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void init() {
        if (!isPermitted()) {
            requestPermission();
        }

        binding.btnConnect.setOnClickListener(v -> {
            dialog.show();
            connectToBluetooth(this);
        });
        binding.btnDisconnect.setOnClickListener(v -> disconnectBluetooth(this));

        binding.seekBarSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            int progressValue = INITIAL_SPEED;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                progressValue = 1000 + progress;
                String label = "Speed: " + progressValue;
                binding.tvSpeed.setText(label);
                if (isRealTime) {
                    sendSpeed(progressValue);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                sendSpeed(progressValue);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(bluetoothReceiver);
        try {
            if (outputStream != null) outputStream.close();
            if (bluetoothSocket != null) bluetoothSocket.close();
        } catch (IOException e) {
            e.printStackTrace(System.err);
        }
    }

    @Override
    public void onConnect() {
        MainActivity.this.runOnUiThread(() -> {
            binding.speedControl.setVisibility(View.VISIBLE);
            binding.btnDisconnect.setVisibility(View.VISIBLE);
            binding.connectSection.setVisibility(View.GONE);
            binding.status.setImageResource(R.drawable.green_dot);
            binding.statusText.setText(R.string.connected);
            dialog.dismiss();
        });
    }

    @Override
    public void onConnectFailure(String err) {
        MainActivity.this.runOnUiThread(() -> {
            dialog.dismiss();
            Toast.makeText(getApplicationContext(), err, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void onDisconnect() {
        MainActivity.this.runOnUiThread(() -> {
            disconnectChanges();
            Toast.makeText(getApplicationContext(), "Bluetooth Disconnected!", Toast.LENGTH_LONG).show();
        });
    }

    private void disconnectChanges() {
        binding.speedControl.setVisibility(View.INVISIBLE);
        binding.btnDisconnect.setVisibility(View.INVISIBLE);
        binding.connectSection.setVisibility(View.VISIBLE);
        binding.status.setImageResource(R.drawable.red_dot);
        binding.statusText.setText(R.string.not_connected);
    }

    @Override
    public void onDisconnectFailure(String err) {
        Toast.makeText(getApplicationContext(), "Error while disconnecting Bluetooth", Toast.LENGTH_LONG).show();
    }
}
