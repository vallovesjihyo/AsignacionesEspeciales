package com.example.myapp;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import com.example.myapp.background.MyService;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import androidx.appcompat.app.AppCompatActivity;

public class DashboardActivity extends AppCompatActivity {

    private static final int LAUNCH_SIMPLE_ACTIVITY = 102;
    private static final int PERMISSION_REQUEST_CODE = 123;

    private View vArduinoLed;
    private TextView tvArduinoStatus;
    private TextView tvArduinoBpm;
    private Button btnConnectArduino;
    private Button btnDisconnectArduino;


    private View vGarminLed;
    private TextView tvGarminStatus;
    private TextView tvGarminBpm;
    private TextView tvGarminSeq;
    private Button btnStartGarmin;
    private Button btnStopGarmin;

    private ScrollView scrollerTerminal;
    private TextView tvServerLog;
    private TextView btnClearTerminal;

    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    private final BroadcastReceiver serviceUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;

            switch (action) {
                case MyService.ACTION_DEVICE_STATUS:
                    String device = intent.getStringExtra("device");
                    String status = intent.getStringExtra("status");
                    String name = intent.getStringExtra("name");
                    updateDeviceStatusUI(device, status, name);
                    break;

                case MyService.ACTION_NEW_TELEMETRY:
                    String telemetryDevice = intent.getStringExtra("device");
                    String bpm = intent.getStringExtra("bpm");
                    int seq = intent.getIntExtra("seq", 0);
                    updateTelemetryUI(telemetryDevice, bpm, seq);
                    break;

                case MyService.ACTION_SERVER_LOG:
                    String message = intent.getStringExtra("message");
                    appendLog(message);
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        checkAndRequestPermissions();

        // Referenciar elementos de la interfaz
        vArduinoLed = findViewById(R.id.vArduinoLed);
        tvArduinoStatus = findViewById(R.id.tvArduinoStatus);
        tvArduinoBpm = findViewById(R.id.tvArduinoBpm);
        btnConnectArduino = findViewById(R.id.btnConnectArduino);
        btnDisconnectArduino = findViewById(R.id.btnDisconnectArduino);


        vGarminLed = findViewById(R.id.vGarminLed);
        tvGarminStatus = findViewById(R.id.tvGarminStatus);
        tvGarminBpm = findViewById(R.id.tvGarminBpm);
        tvGarminSeq = findViewById(R.id.tvGarminSeq);
        btnStartGarmin = findViewById(R.id.btnStartGarmin);
        btnStopGarmin = findViewById(R.id.btnStopGarmin);

        scrollerTerminal = findViewById(R.id.scrollerTerminal);
        tvServerLog = findViewById(R.id.tvServerLog);
        btnClearTerminal = findViewById(R.id.btnClearTerminal);

        setupListeners();
    }

    private void setupListeners() {
        // CONECTAR ARDUINO (Abre selector de dispositivos)
        btnConnectArduino.setOnClickListener(v -> {
            Intent act = new Intent(this, SimpleActivity.class);
            startActivityForResult(act, LAUNCH_SIMPLE_ACTIVITY);
        });

        // DESCONECTAR ARDUINO
        btnDisconnectArduino.setOnClickListener(v -> sendControlCommand("disconnect_arduino", null, null));



        // CONTROLES GARMIN
        btnStartGarmin.setOnClickListener(v -> sendControlCommand("start_garmin", null, null));
        btnStopGarmin.setOnClickListener(v -> sendControlCommand("stop_garmin", null, null));

        // LIMPIAR TERMINAL
        btnClearTerminal.setOnClickListener(v -> tvServerLog.setText(""));
    }

    private boolean hasBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                   ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void checkAndRequestPermissions() {
        if (!hasBluetoothPermissions()) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN},
                    PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                sendControlCommand("request_status", null, null);
            } else {
                Toast.makeText(this, "Permisos de Bluetooth son requeridos", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void sendControlCommand(String command, String extraKey, String extraValue) {
        if (!hasBluetoothPermissions()) {
            checkAndRequestPermissions();
            return;
        }
        Intent intent = new Intent(this, MyService.class);
        intent.putExtra("command", command);
        if (extraKey != null && extraValue != null) {
            intent.putExtra(extraKey, extraValue);
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        } catch (Exception e) {
            Log.e("DashboardActivity", "Error al enviar comando al servicio", e);
        }
    }

    private void sendConnectCommand(BluetoothDevice device) {
        if (!hasBluetoothPermissions()) {
            checkAndRequestPermissions();
            return;
        }
        Intent intent = new Intent(this, MyService.class);
        intent.putExtra("command", "connect_arduino");
        intent.putExtra(SimpleActivity.TAG_BLUETOOTH_DEVICE, device);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        } catch (Exception e) {
            Log.e("DashboardActivity", "Error al enviar comando de conexion al servicio", e);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        // Registrar el receptor de eventos del servicio en segundo plano
        IntentFilter filter = new IntentFilter();
        filter.addAction(MyService.ACTION_DEVICE_STATUS);
        filter.addAction(MyService.ACTION_NEW_TELEMETRY);
        filter.addAction(MyService.ACTION_SERVER_LOG);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(serviceUpdateReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(serviceUpdateReceiver, filter);
        }

        // Solicitar estado actual al servicio para poblar la UI al abrir la pantalla
        if (hasBluetoothPermissions()) {
            sendControlCommand("request_status", null, null);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(serviceUpdateReceiver);
    }

    @Override
    @SuppressLint("MissingPermission")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == LAUNCH_SIMPLE_ACTIVITY && resultCode == RESULT_OK && data != null) {
            BluetoothDevice bt = data.getParcelableExtra(SimpleActivity.TAG_BLUETOOTH_DEVICE);
            if (bt != null) {
                appendLog("[Dashboard] Seleccionado Arduino: " + bt.getName());
                sendConnectCommand(bt);
            }
        }
    }

    private void updateDeviceStatusUI(String device, String status, String name) {
        if ("arduino".equals(device)) {
            if ("connected".equals(status)) {
                vArduinoLed.setBackgroundColor(Color.parseColor("#00E676")); // Verde
                tvArduinoStatus.setText("Conectado: " + (name != null ? name : "Arduino"));
                tvArduinoStatus.setTextColor(Color.parseColor("#2E7D32"));
            } else if ("connecting".equals(status)) {
                vArduinoLed.setBackgroundColor(Color.parseColor("#FFD600")); // Amarillo
                tvArduinoStatus.setText("Conectando...");
                tvArduinoStatus.setTextColor(Color.parseColor("#F57F17"));
            } else {
                vArduinoLed.setBackgroundColor(Color.parseColor("#90A4AE")); // Gris
                tvArduinoStatus.setText("Desconectado");
                tvArduinoStatus.setTextColor(Color.parseColor("#78909C"));
                tvArduinoBpm.setText("-- BPM");
            }
        } else if ("garmin".equals(device)) {
            if ("connected".equals(status)) {
                vGarminLed.setBackgroundColor(Color.parseColor("#00E676")); // Verde
                tvGarminStatus.setText("Conectado" + (name != null ? ": " + name : ""));
                tvGarminStatus.setTextColor(Color.parseColor("#2E7D32"));
            } else if ("connecting".equals(status)) {
                vGarminLed.setBackgroundColor(Color.parseColor("#FFD600")); // Amarillo
                tvGarminStatus.setText(name != null ? name : "Sincronizando...");
                tvGarminStatus.setTextColor(Color.parseColor("#F57F17"));
            } else {
                vGarminLed.setBackgroundColor(Color.parseColor("#90A4AE")); // Gris
                tvGarminStatus.setText("Desconectado");
                tvGarminStatus.setTextColor(Color.parseColor("#78909C"));
                tvGarminBpm.setText("-- BPM");
                tvGarminSeq.setText("Seq: --");
            }
        }
    }

    private void updateTelemetryUI(String device, String bpm, int seq) {
        if ("arduino".equals(device)) {
            tvArduinoBpm.setText(bpm + " BPM");
        } else if ("garmin".equals(device)) {
            tvGarminBpm.setText(bpm + " BPM");
            tvGarminSeq.setText("Seq: " + seq);
        }
    }

    private void appendLog(String message) {
        runOnUiThread(() -> {
            String currentTime = timeFormat.format(new Date());
            String logLine = String.format("[%s] %s\n", currentTime, message);
            tvServerLog.append(logLine);
            
            // Auto scroll al final de la consola
            scrollerTerminal.post(() -> scrollerTerminal.fullScroll(View.FOCUS_DOWN));
        });
    }
}
