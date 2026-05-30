package com.example.myapp.background;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.myapp.MainActivity;
import com.example.myapp.R;
import com.example.myapp.SimpleActivity;
import com.example.myapp.ai.IntentAIModel;
import com.example.myapp.data.ConfigUtils;
import com.example.myapp.data.MyData;
import com.example.myapp.data.MyJSONParser;
import com.example.myapp.garmin.GarminManager;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.util.Locale;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.Response;

public class MyService extends Service {

    // Actions for broadcasting status & data to UI
    public static final String ACTION_DEVICE_STATUS = "com.example.myapp.ACTION_DEVICE_STATUS";
    public static final String ACTION_NEW_TELEMETRY = "com.example.myapp.ACTION_NEW_TELEMETRY";
    public static final String ACTION_SERVER_LOG = "com.example.myapp.ACTION_SERVER_LOG";
    
    // Actions for receiving control signals from UI
    public static final String ACTION_CONTROL_SERVICE = "com.example.myapp.ACTION_CONTROL_SERVICE";

    private static final String CHANNEL_ID = "DeviceControlServerChannel";
    private static final int NOTIFICATION_ID = 101;

    private MyJSONParser parser = null;
    private int offset = -1;

    private IntentAIModel modeloIA = new IntentAIModel();
    private static final boolean USAR_NGROK = false;
    private static final String URL_NGROK = "https://5e64-200-68-165-1.ngrok-free.app";

    private String getUrlServidor(boolean websocket) {
        if (USAR_NGROK) {
            return URL_NGROK;
        } else {
            return websocket ? ConfigUtils.getWebSocketUrl(this) : ConfigUtils.getHttpUrl(this);
        }
    }

    private String resTelegram = "";
    private String resServidor = "";

    private ConectarMiBluetooth bt_connect = null;
    private ComunicarConBluetooth bt_comm = null;

    private WebSocket mWebSocket;
    private final OkHttpClient client = new OkHttpClient();

    private GarminManager garminManager;
    private BroadcastReceiver controlReceiver;

    private boolean isServiceRunning = false;
    private Thread pollingThread;

    private boolean arduinoConnected = false;
    private boolean garminConnected = false;
    private String lastArduinoBpm = "--";
    private String lastGarminBpm = "--";
    private int lastGarminSeq = 0;
    private BluetoothDevice lastBtDevice = null;

    private void processControlCommand(String cmd, Intent intent) {
        if (cmd == null) return;
        Log.e("MyService", "Procesando comando de control: " + cmd);
        
        if ("start_garmin".equals(cmd)) {
            initGarmin();
        } else if ("stop_garmin".equals(cmd)) {
            stopGarmin();
        } else if ("connect_arduino".equals(cmd)) {
            BluetoothDevice device = intent.getParcelableExtra(SimpleActivity.TAG_BLUETOOTH_DEVICE);
            if (device != null) {
                lastBtDevice = device;
            }
            if (lastBtDevice != null) {
                connectArduino(lastBtDevice);
            } else {
                broadcastLog("[Control] Error: No hay dispositivo Arduino seleccionado");
            }
        } else if ("disconnect_arduino".equals(cmd)) {
            disconnectArduino();
        } else if ("write_arduino".equals(cmd)) {
            String payload = intent.getStringExtra("payload");
            if (bt_comm != null && payload != null) {
                bt_comm.write(payload);
                broadcastLog("[Control -> Arduino] Enviado: " + payload.trim());
            } else {
                broadcastLog("[Control] Error: Arduino no conectado o vacío");
            }
        } else if ("request_status".equals(cmd)) {
            // Re-enviar estados actuales para sincronizar la UI al abrirse
            broadcastStatus("arduino", arduinoConnected ? "connected" : "disconnected", 
                    lastBtDevice != null ? lastBtDevice.getName() : null);
            broadcastStatus("garmin", garminConnected ? "connected" : "disconnected", 
                    garminConnected ? "Activo" : null);
            
            if (arduinoConnected) {
                broadcastTelemetry("arduino", lastArduinoBpm, 0);
            }
            if (garminConnected) {
                broadcastTelemetry("garmin", lastGarminBpm, lastGarminSeq);
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("MyService", "onCreate()");
        
        // Registrar receptor de señales de control del UI/Dashboard (para compatibilidad de broadcast)
        controlReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ACTION_CONTROL_SERVICE.equals(intent.getAction())) {
                    String cmd = intent.getStringExtra("command");
                    processControlCommand(cmd, intent);
                }
            }
        };
        
        IntentFilter filter = new IntentFilter(ACTION_CONTROL_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(controlReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(controlReceiver, filter);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.e("MyService", "onStartCommand()");
        
        // Iniciar el Foreground Service con su respectiva notificación obligatoria
        startForegroundService();

        if (intent != null) {
            String cmd = intent.getStringExtra("command");
            if (cmd != null) {
                processControlCommand(cmd, intent);
            } else {
                BluetoothDevice device = intent.getParcelableExtra(SimpleActivity.TAG_BLUETOOTH_DEVICE);
                if (device != null) {
                    lastBtDevice = device;
                    connectArduino(lastBtDevice);
                }
            }
        }

        if (!isServiceRunning) {
            isServiceRunning = true;
            
            // Conectar el WebSocket persistente hacia el servidor
            initWebSocket();
            
            // Iniciar Garmin por defecto
            initGarmin();

            // Levantar hilo de segundo plano para Telegram polling
            pollingThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    broadcastLog("[Servidor] Hilo de escucha Telegram iniciado.");
                    MyData data = null;
                    while (isServiceRunning) {
                        try {
                            data = get_updates();
                            if (data != null) {
                                process(data);
                            }
                            // Dormir un momento para evitar consumo excesivo
                            Thread.sleep(1500);
                        } catch (InterruptedException e) {
                            Log.e("MyService", "Hilo interrumpido");
                            break;
                        } catch (Exception e) {
                            Log.e("MyService", "Excepción en hilo de escucha", e);
                        }
                    }
                    broadcastLog("[Servidor] Hilo de escucha finalizado.");
                }
            });
            pollingThread.start();
        }

        return START_STICKY;
    }

    private void startForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Central de Control de Dispositivos",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Mantiene activa la comunicación con Arduino y Garmin");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Servidor de Dispositivos Activo")
                .setContentText("Monitoreando Arduino y Reloj Garmin...")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void connectArduino(BluetoothDevice bt) {
        if (bt == null) return;
        
        broadcastStatus("arduino", "connecting", bt.getName());
        new Thread(() -> {
            try {
                broadcastLog("[Arduino] Conectando a " + bt.getName() + "...");
                
                // Desconectar previo si existe
                if (bt_comm != null) {
                    bt_comm.cancel();
                }
                if (bt_connect != null) {
                    try {
                        bt_connect.getSocket().close();
                    } catch (Exception ignored) {}
                }

                bt_connect = new ConectarMiBluetooth(bt);
                bt_connect.execute();
                
                if (bt_connect.getSocket() != null && bt_connect.getSocket().isConnected()) {
                    arduinoConnected = true;
                    broadcastStatus("arduino", "connected", bt.getName());
                    broadcastLog("[Arduino] ¡Conexión serial SPP establecida con " + bt.getName() + "!");
                    
                    bt_comm = new ComunicarConBluetooth(bt_connect.getSocket(), new ComunicarConBluetooth.BluetoothDataListener() {
                        @Override
                        public void onDataReceived(String data) {
                            String bpm = data.trim();
                            if (!bpm.isEmpty()) {
                                lastArduinoBpm = bpm;
                                broadcastTelemetry("arduino", bpm, 0);
                                emitirDatosAlServidor("arduino", bpm);
                            }
                        }
                    });
                    bt_comm.start();
                } else {
                    arduinoConnected = false;
                    broadcastStatus("arduino", "disconnected", null);
                    broadcastLog("[Arduino] Error: No se pudo establecer canal RFCOMM (¿está encendido y previamente emparejado en el sistema?).");
                }
            } catch (Exception e) {
                Log.e("MyService", "Error al conectar Arduino", e);
                arduinoConnected = false;
                broadcastStatus("arduino", "disconnected", null);
                broadcastLog("[Arduino] Error en la conexión: " + e.getMessage());
            }
        }).start();
    }

    private void disconnectArduino() {
        arduinoConnected = false;
        new Thread(() -> {
            try {
                if (bt_comm != null) {
                    bt_comm.cancel();
                    bt_comm = null;
                }
                if (bt_connect != null) {
                    if (bt_connect.getSocket() != null) {
                        bt_connect.getSocket().close();
                    }
                    bt_connect = null;
                }
                broadcastStatus("arduino", "disconnected", null);
                broadcastLog("[Arduino] Desconectado por el usuario.");
            } catch (IOException e) {
                Log.e("MyService", "Error al cerrar socket", e);
            }
        }).start();
    }

    private void initGarmin() {
        broadcastStatus("garmin", "connecting", "Buscando...");
        broadcastLog("[Garmin] Inicializando SDK ConnectIQ...");
        
        garminManager = new GarminManager(this, new GarminManager.GarminDataListener() {
            @Override
            public void onDataReceived(String bpm, int seq) {
                lastGarminBpm = bpm;
                lastGarminSeq = seq;
                garminConnected = true;
                
                broadcastTelemetry("garmin", bpm, seq);
                emitirDatosAlServidor("garmin", bpm);
            }
        });
        
        garminManager.setStatusListener(new GarminManager.GarminStatusListener() {
            @Override
            public void onStatusChanged(String status) {
                broadcastLog("[Garmin] " + status);
                if (status.contains("Conectado:")) {
                    garminConnected = true;
                    broadcastStatus("garmin", "connected", status.replace("Conectado: ", ""));
                } else if (status.contains("Desconectado") || status.contains("No se encontraron") || status.contains("Error")) {
                    garminConnected = false;
                    broadcastStatus("garmin", "disconnected", status);
                } else {
                    broadcastStatus("garmin", "connecting", status);
                }
            }
        });
        
        garminManager.initialize();
    }

    private void stopGarmin() {
        garminConnected = false;
        if (garminManager != null) {
            garminManager.destroy();
            garminManager = null;
        }
        broadcastStatus("garmin", "disconnected", null);
        broadcastLog("[Garmin] Monitoreo detenido.");
    }

    private void broadcastStatus(String device, String status, String name) {
        Intent intent = new Intent(ACTION_DEVICE_STATUS);
        intent.putExtra("device", device);
        intent.putExtra("status", status);
        intent.putExtra("name", name);
        sendBroadcast(intent);
    }

    private void broadcastTelemetry(String device, String bpm, int seq) {
        Intent intent = new Intent(ACTION_NEW_TELEMETRY);
        intent.putExtra("device", device);
        intent.putExtra("bpm", bpm);
        intent.putExtra("seq", seq);
        sendBroadcast(intent);
    }

    private void broadcastLog(String message) {
        Intent intent = new Intent(ACTION_SERVER_LOG);
        intent.putExtra("message", message);
        sendBroadcast(intent);
    }

    @Override
    public void onDestroy() {
        Log.e("MyService", "onDestroy()");
        isServiceRunning = false;
        
        if (pollingThread != null) {
            pollingThread.interrupt();
        }
        
        disconnectArduino();
        stopGarmin();

        if (controlReceiver != null) {
            unregisterReceiver(controlReceiver);
        }

        if (mWebSocket != null) {
            mWebSocket.close(1000, "Service terminating");
        }

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // Servicio no enlazado, se comunica por Intents y Broadcasts
    }

    private void initWebSocket() {
        try {
            String url = getUrlServidor(true);
            if (url.startsWith("http")) {
                url = url.replace("http", "ws");
            }

            Log.e("ON-MyService", "Iniciando WebSocket hacia: " + url);
            Request request = new Request.Builder().url(url).build();

            mWebSocket = client.newWebSocket(request, new WebSocketListener() {
                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    Log.e("ON-MyService", "WebSocket conectado!");
                    broadcastLog("[Servidor Web] WebSocket conectado exitosamente!");
                    webSocket.send("{\"type\":\"hello\",\"source\":\"android_app\"}");
                }

                @Override
                public void onMessage(WebSocket webSocket, String text) {
                    Log.e("ON-MyService", "Mensaje del servidor: " + text);
                }

                @Override
                public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                    Log.e("ON-MyService", "Error en WebSocket: " + t.getMessage());
                    broadcastLog("[Servidor Web] Fallo en la conexión WebSocket.");
                }

                @Override
                public void onClosing(WebSocket webSocket, int code, String reason) {
                    webSocket.close(1000, null);
                    Log.e("ON-MyService", "WebSocket cerrándose: " + reason);
                }
            });

        } catch (Exception e) {
            Log.e("ON-MyService", "Error inicializando WebSocket", e);
        }
    }

    private void emitirDatosAlServidor(String origen, String bpm) {
        if (mWebSocket != null) {
            try {
                JSONObject obj = new JSONObject();
                obj.put("source", origen);

                try {
                    obj.put("bpm", Integer.parseInt(bpm));
                } catch (NumberFormatException e) {
                    obj.put("bpm", bpm);
                }

                obj.put("seq", 0);
                obj.put("timestamp", System.currentTimeMillis());

                mWebSocket.send(obj.toString());
                Log.d("ON-MyService", "Dato enviado por WebSocket: " + obj.toString());
            } catch (Exception e) {
                Log.e("ON-MyService", "Error enviando dato por WebSocket", e);
            }
        }
    }

    private String get() {
        Log.e("ON-MyService", "get()");
        String urlServidor = getUrlServidor(false);
        Log.e("ON-MyService", "get(): usando URL=" + urlServidor);
        resServidor = "";

        try {
            URL url = new URL(urlServidor + "/users2");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(false);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Content-length", "0");

            if (USAR_NGROK) {
                conn.setRequestProperty("ngrok-skip-browser-warning", "true");
            }

            conn.setUseCaches(false);
            conn.setAllowUserInteraction(false);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.connect();

            int status = conn.getResponseCode();
            Log.e("ON-MyService", "get(): status=" + status);

            if (status == 200) {
                InputStreamReader reader = new InputStreamReader(conn.getInputStream());
                BufferedReader br = new BufferedReader(reader);

                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line + "\n");
                }
                br.close();
                resServidor = sb.toString();
                Log.e("ON-MyService", "get(): respuesta=" + resServidor);
            }

            conn.disconnect();

        } catch (MalformedURLException e) {
            Log.e("ON-MyService", "get(): MalformedURLException", e);
        } catch (IOException e) {
            Log.e("ON-MyService", "get(): IOException", e);
        }

        return resServidor;
    }

    private void send(String info) {
        Log.e("ON-MyService", "send(): " + info);
        try {
            String textoCodificado = URLEncoder.encode(info, "UTF-8");

            URL url = new URL(
                    "https://api.telegram.org/bot8710550268:AAEH-ONfsp14Adw8_D6BNNv1jHXu0qLnx8Y/sendMessage?chat_id=5174630596&text="
                            + textoCodificado);

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Content-length", "0");

            conn.setRequestProperty("ngrok-skip-browser-warning", "true");

            conn.setUseCaches(false);
            conn.setAllowUserInteraction(false);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.connect();

            int status = conn.getResponseCode();
            Log.e("ON-MyService", "send(): status=" + status);

            conn.disconnect();
        } catch (MalformedURLException e) {
            Log.e("ON-MyService", "send(): MalformedURLException", e);
        } catch (IOException e) {
            Log.e("ON-MyService", "send(): IOException", e);
        }
    }

    private MyData get_updates() {
        resTelegram = "";

        HttpURLConnection conn = null;
        try {
            String my_url = "https://api.telegram.org/bot8710550268:AAEH-ONfsp14Adw8_D6BNNv1jHXu0qLnx8Y/getUpdates?offset="
                    + offset + "&timeout=1000";
            URL url = new URL(my_url);
            conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Content-length", "0");
            conn.setUseCaches(false);
            conn.setAllowUserInteraction(false);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.connect();

            int status = conn.getResponseCode();

            if (status == 200) {
                InputStreamReader reader = new InputStreamReader(conn.getInputStream());
                BufferedReader br = new BufferedReader(reader);

                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line + "\n");
                }
                br.close();
                resTelegram = sb.toString();

                this.parser = new MyJSONParser("[" + resTelegram + "]");
                MyData data = this.parser.getValue();
                return data;
            }

            if (conn != null) conn.disconnect();

        } catch (Exception e) {
            Log.e("MyService", "Error get_updates", e);
            if (conn != null) conn.disconnect();
        }

        return null;
    }

    private boolean process(MyData data) {
        if (data.msg == null || data.msg.size() == 0) {
            return true;
        }

        for (int i = 0; i < data.msg.size(); ++i) {
            int update_id = data.update_id.get(i);
            String msg = data.msg.get(i);

            broadcastLog("[Telegram -> Servidor] " + msg);
            this.offset = update_id + 1;

            // Invocar el clasificador Naive Bayes local
            IntentAIModel.Prediction prediction = modeloIA.predict(msg);
            IntentAIModel.Intent intent = prediction.intent;
            
            Log.e("IA-MODEL", "Comando detectado por IA: " + intent.name() + " (" + prediction.confidence + ")");
            broadcastLog("[IA Intel] Comando inferido: " + intent.name() + " (" + String.format(Locale.getDefault(), "%.2f", prediction.confidence) + ")");

            if (intent == IntentAIModel.Intent.INVALIDO || !prediction.valid) {
                // Comando no válido o no reconocido
                broadcastLog("[IA Intel] Mensaje ignorado o no coincide con intenciones registradas: " + prediction.userResponse);
                this.send(prediction.userResponse);
                continue;
            }

            if (intent == IntentAIModel.Intent.LEER) {
                String r = get();
                if (r.isEmpty()) {
                    this.send("El servidor no respondió o está vacío");
                    broadcastLog("[Acción] Leer: Sin respuesta del servidor.");
                } else {
                    this.send("Info del servidor:\n" + r);
                    broadcastLog("[Acción] Leer: Info enviada a Telegram.");
                }
            } else {
                // Para el resto de intenciones válidas (ENCENDER, APAGAR, STATUS, SET_WIFI, SET_IP)
                String payload = prediction.bluetoothPayload;
                if (payload != null && !payload.isEmpty()) {
                    if (bt_comm != null) {
                        bt_comm.write(payload);
                        broadcastLog("[Acción IA] Enviando payload a Arduino: " + payload.trim());
                    } else {
                        broadcastLog("[Acción IA] Error: Arduino no conectado para ejecutar " + intent.name());
                    }
                }
                this.send(prediction.userResponse);
            }
        }

        return true;
    }

    private String extraerParametrosSetWifi(String msg) {
        String upper = msg.toUpperCase();
        if (upper.startsWith("SET WIFI ")) {
            return msg.substring(9).trim();
        }
        if (upper.contains("WIFI")) {
            int index = upper.indexOf("WIFI") + 4;
            return msg.substring(index).replace("a", "").replace(":", "").trim();
        }
        return "";
    }

    private String extraerParametrosSetServer(String msg) {
        String upper = msg.toUpperCase();
        if (upper.startsWith("SET SERVER ")) {
            return msg.substring(11).trim();
        }
        if (upper.contains("SERVIDOR")) {
            int index = upper.indexOf("SERVIDOR") + 8;
            return msg.substring(index).replace("a", "").replace(":", "").trim();
        }
        if (upper.contains("SERVER")) {
            int index = upper.indexOf("SERVER") + 6;
            return msg.substring(index).replace("a", "").replace(":", "").trim();
        }
        return "";
    }
}