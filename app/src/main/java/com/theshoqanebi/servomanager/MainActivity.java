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
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.theshoqanebi.servomanager.databinding.ActivityMainBinding;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity implements OnConnectListener, OnDisconnectListener, OnMotorSpeedClickListener {
    private static final int INITIAL_SPEED = 1000;
    private static final UUID PORT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final ArrayList<String> permissions = new ArrayList<>();

    private final int STEP = 50;

    static {
        permissions.add(Manifest.permission.BLUETOOTH);
        permissions.add(Manifest.permission.BLUETOOTH_ADMIN);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    private final ArrayList<Motor> motors = new ArrayList<>();
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
    private final Adapter adapter = new Adapter(motors, this);
    BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    private LoadingDialog dialog;
    private boolean isRealTime = false;
    private ActivityMainBinding binding;
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
    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;

    public static boolean isValidMAC(String mac) {
        // Regular expression for validating MAC address
        String regex = "^([0-9A-Fa-f]{2}[:\\-]){5}[0-9A-Fa-f]{2}$";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(mac);
        return matcher.matches();
    }

    private void initMotors() {
        motors.clear();
        motors.add(new Motor(1, 0));
        motors.add(new Motor(2, 0));
        motors.add(new Motor(3, 0));
        motors.add(new Motor(4, 0));
    }

    private void resetRecyclerView() {
        initMotors();
        adapter.resetAll();
    }

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                getWindow().setStatusBarColor(getColor(R.color.statusBar));
            }
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
            if (isChecked) {
                binding.oneSeekBar.setVisibility(View.GONE);
                binding.tvSpeed.setVisibility(View.GONE);
                binding.fourSeekBar.setVisibility(View.VISIBLE);
                initMotors();
                resetRecyclerView();
            } else {
                binding.oneSeekBar.setVisibility(View.VISIBLE);
                binding.tvSpeed.setVisibility(View.VISIBLE);
                binding.fourSeekBar.setVisibility(View.GONE);
                resetRecyclerView();
            }
        });

        init();
    }


    private void sendDroneInfo() {
        JSONObject json = new JSONObject();
        for (Motor motor : motors) {
            try {
                json.put(String.format(Locale.ENGLISH, "motor_%d", motor.getId()), INITIAL_SPEED + motor.getSpeed());
            } catch (JSONException e) {
                return;
            }
        }
        sendSpeed(json.toString());
    }

    private void connectToBluetooth(OnConnectListener onConnectListener) {
        new Thread(() -> {
            String address = binding.macAddress.getText().toString().trim().toUpperCase(Locale.ROOT);
            if (address.isEmpty()) {
                onConnectListener.onConnectFailure("Please enter a MAC address");
                return;
            } else if (!isValidMAC(address)) {
                onConnectListener.onConnectFailure("Invalid MAC address");
                return;
            } else if (!isBluetoothEnabled()) {
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

    private boolean isBluetoothEnabled() {
        if (bluetoothAdapter == null) {
            return false;
        } else {
            return bluetoothAdapter.isEnabled();
        }
    }

    private boolean checkForBluetoothErrors() {
        if (!isBluetoothEnabled()) {
            Toast.makeText(this, "Bluetooth not enabled", Toast.LENGTH_LONG).show();
            return true;
        } else if (bluetoothSocket == null || !bluetoothSocket.isConnected()) {
            Toast.makeText(this, "You are not connected to any bluetooth device", Toast.LENGTH_LONG).show();
            return true;
        } else if (outputStream == null) {
            Toast.makeText(this, "Send Failed", Toast.LENGTH_LONG).show();
            return true;
        }
        return false;
    }

    private void sendSpeed(int speed) {
        if (checkForBluetoothErrors()) {
            return;
        }
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
        if (checkForBluetoothErrors()) {
            return;
        }
        try {
            String message = speed + "\n";
            outputStream.write(message.getBytes());
            outputStream.flush();
        } catch (IOException e) {
            Toast.makeText(this, "Send Failed: " + e, Toast.LENGTH_LONG).show();
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

        binding.increase.setOnClickListener(v -> {
            binding.speedSeekBar.setProgress(binding.speedSeekBar.getProgress() + STEP);
            int speed = 1000 + binding.speedSeekBar.getProgress();
            String s = "Speed: " + speed;
            binding.tvSpeed.setText(s);
            sendSpeed(speed);
        });

        binding.decrease.setOnClickListener(v -> {
            binding.speedSeekBar.setProgress(binding.speedSeekBar.getProgress() - STEP);
            int speed = 1000 + binding.speedSeekBar.getProgress();
            String s = "Speed: " + speed;
            binding.tvSpeed.setText(s);
            sendSpeed(speed);
        });

        binding.speedSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
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

        binding.fourSeekBar.setAdapter(adapter);
        binding.fourSeekBar.setLayoutManager(new LinearLayoutManager(this));
        binding.fourSeekBar.setItemAnimator(new DefaultItemAnimator());
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

    @Override
    public void onIncrease(int position) {
        for (int i = 0; i < motors.size(); i++) {
            Motor motor = motors.get(i);
            if (motor.getId() == (position + 1)) {
                int currentSpeed = motor.getSpeed();
                if (currentSpeed < 1000) {
                    int newSpeed = currentSpeed + STEP;
                    if (newSpeed > 1000) newSpeed = 1000;
                    motor.setSpeed(newSpeed);
                }
            }
        }
        adapter.notifyItemChanged(position);
        sendDroneInfo();
    }

    @Override
    public void onDecrease(int position) {
        for (int i = 0; i < motors.size(); i++) {
            Motor motor = motors.get(i);
            if (motor.getId() == (position + 1)) {
                int currentSpeed = motor.getSpeed();
                if (currentSpeed > 0) {
                    int newSpeed = currentSpeed - STEP;
                    if (newSpeed < 0) newSpeed = 0;
                    motor.setSpeed(newSpeed);
                }
            }
        }
        adapter.notifyItemChanged(position);
        sendDroneInfo();
    }

    @Override
    public void onSeekBarProgressChanged(int position, int progress) {
        for (int i = 0; i < motors.size(); i++) {
            Motor motor = motors.get(i);
            if (motor.getId() == (position + 1)) {
                motors.get(i).setSpeed(progress);
            }
        }
        if (isRealTime) {
            sendDroneInfo();
        }
    }

    @Override
    public void onSeekBarStartTrackingTouch(int position) {

    }

    @Override
    public void onSeekBarStopTrackingTouch(int position) {
        adapter.update(motors, position);
        sendDroneInfo();
    }
}
