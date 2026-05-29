package com.example.myapp;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import com.example.myapp.data.ConfigUtils;
import com.example.myapp.garmin.GarminManager;
import org.json.JSONObject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.Response;

public class GarminActivity extends Activity {

    private static final String TAG = "GarminActivity";

    private TextView tvBpmValue;
    private TextView tvStatus;
    private Switch switchGarmin;

    private GarminManager garminManager;

    private WebSocket webSocket;
    private final OkHttpClient okHttpClient = new OkHttpClient();

    private BroadcastReceiver bpmReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("GARMIN_BPM_UPDATE".equals(intent.getAction())) {
                String bpm = intent.getStringExtra("bpm");
                if (tvBpmValue != null && bpm != null) {
                    tvBpmValue.setText(bpm);
                }
            }
        }
    };

    private BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("GARMIN_STATUS_UPDATE".equals(intent.getAction())) {
                String status = intent.getStringExtra("status");
                if (tvStatus != null && status != null) {
                    tvStatus.setText(status);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_garmin);

        tvBpmValue = findViewById(R.id.tvBpmValue);
        tvStatus = findViewById(R.id.tvStatus);
        switchGarmin = findViewById(R.id.switchGarmin);

        initWebSocket();

        // GarminManager directamente en esta Activity
        garminManager = new GarminManager(this, new GarminManager.GarminDataListener() {
            private int lastSeq = -1;

            @Override
            public void onDataReceived(String bpm, int seq) {
                // Procesar inmediatamente para liberar la cola del reloj
                if (seq != 0 && seq == lastSeq)
                    return;
                lastSeq = seq;

                // Enviar al servidor en un hilo separado para no bloquear la recepción del
                // reloj
                new Thread(() -> {
                    emitirDatosAlServidor("garmin", bpm, seq);
                }).start();

                // Actualizar la UI
                runOnUiThread(() -> {
                    if (tvBpmValue != null)
                        tvBpmValue.setText(bpm);
                    if (tvStatus != null)
                        tvStatus.setText("BPM: " + bpm + " (Q: " + seq + ")");
                });

                Intent intent = new Intent("GARMIN_BPM_UPDATE");
                intent.putExtra("bpm", bpm);
                sendBroadcast(intent);
            }
        });

        switchGarmin.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    tvStatus.setText("Iniciando conexión...");
                    garminManager.initialize();
                } else {
                    garminManager.destroy();
                    tvBpmValue.setText("--");
                    tvStatus.setText("Desconectado");
                }
            }
        });

        // Iniciar la conexión automáticamente al abrir
        tvStatus.setText("Iniciando conexión...");
        garminManager.initialize();
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(bpmReceiver, new IntentFilter("GARMIN_BPM_UPDATE"), Context.RECEIVER_EXPORTED);
        registerReceiver(statusReceiver, new IntentFilter("GARMIN_STATUS_UPDATE"), Context.RECEIVER_EXPORTED);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(bpmReceiver);
        unregisterReceiver(statusReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (garminManager != null) {
            garminManager.destroy();
        }
        if (webSocket != null) {
            webSocket.close(1000, "Activity destroyed");
        }
    }

    private void initWebSocket() {
        if (webSocket != null)
            return; // Evitar múltiples conexiones

        String urlServidor = ConfigUtils.getWebSocketUrl(this);
        Log.d(TAG, "Iniciando WebSocket persistente hacia: " + urlServidor);
        Request request = new Request.Builder().url(urlServidor).build();
        webSocket = okHttpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                Log.d(TAG, "WebSocket conectado y estable!");
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                Log.e(TAG, "Error WebSocket: " + t.getMessage());
                // Reintentar solo después de un tiempo para no saturar
                webSocket.close(1001, "Error");
                GarminActivity.this.webSocket = null;
                getWindow().getDecorView().postDelayed(() -> initWebSocket(), 3000);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                GarminActivity.this.webSocket = null;
            }
        });
    }

    private void emitirDatosAlServidor(String origen, String bpm, int seq) {
        if (webSocket != null) {
            try {
                JSONObject obj = new JSONObject();
                obj.put("source", origen);
                try {
                    obj.put("bpm", Integer.parseInt(bpm));
                } catch (NumberFormatException e) {
                    obj.put("bpm", bpm);
                }
                obj.put("seq", seq);
                obj.put("timestamp", System.currentTimeMillis());

                webSocket.send(obj.toString());
                Log.d(TAG, "Dato enviado: " + bpm + " seq=" + seq);
            } catch (Exception e) {
                Log.e(TAG, "Error al enviar al servidor", e);
            }
        }
    }
}
