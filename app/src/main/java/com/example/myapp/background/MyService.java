package com.example.myapp.background;

import android.annotation.SuppressLint;
import android.app.IntentService;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import com.example.myapp.SimpleActivity;
import com.example.myapp.ai.IntentAIModel; // NUEVO: modelo IA local para interpretar lenguaje natural
import com.example.myapp.data.ConfigUtils;
import com.example.myapp.data.MyData;
import com.example.myapp.data.MyJSONParser;
import com.example.myapp.garmin.GarminManager;

import org.json.JSONObject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.Response;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;

public class MyService extends IntentService {
    MyJSONParser parser = null;
    int offset = -1;

    // NUEVO: instancia del modelo IA local.
    // Este modelo recibe el mensaje natural de Telegram y devuelve una intención:
    // ENCENDER, APAGAR, LEER, STATUS, RECONECTAR, SET_WIFI, SET_SERVER, TERMINAR o INVALIDO.
    private IntentAIModel modeloIA = new IntentAIModel();

    // Variable para decidir que red usar
    private static final boolean USAR_NGROK = false;

    // Variable que guarda la url del servidor fuera de la red
    private static final String URL_NGROK = "https://5e64-200-68-165-1.ngrok-free.app";

    private String getUrlServidor(boolean websocket) {
        if (USAR_NGROK) {
            return URL_NGROK;
        } else {
            return websocket ? ConfigUtils.getWebSocketUrl(this) : ConfigUtils.getHttpUrl(this);
        }
    }

    // Variables separadas para no mezclar respuestas
    String resTelegram = "";
    String resServidor = "";

    ConectarMiBluetooth bt_connect = null;
    ComunicarConBluetooth bt_comm = null;

    private WebSocket mWebSocket;
    private final OkHttpClient client = new OkHttpClient();

    private GarminManager garminManager;

    public MyService() {
        super("MyService");
    }

    @SuppressLint("MissingPermission")
    @Override
    protected void onHandleIntent(Intent workIntent) {
        BluetoothDevice bt = workIntent.getParcelableExtra(SimpleActivity.TAG_BLUETOOTH_DEVICE);
        Log.e("ON-MyService", "onHandleIntent(): [" + bt.getName() + "]");

        initWebSocket();

        /*
         * garminManager = new GarminManager(this, new
         * GarminManager.GarminDataListener() {
         *
         * @Override
         * public void onDataReceived(String bpm) {
         * emitirDatosAlServidor("garmin", bpm);
         *
         * Intent intent = new Intent("GARMIN_BPM_UPDATE");
         * intent.putExtra("bpm", bpm);
         * sendBroadcast(intent);
         * }
         * });
         * garminManager.initialize();
         */

        BroadcastReceiver controlReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("GARMIN_CONTROL".equals(intent.getAction())) {
                    String cmd = intent.getStringExtra("command");
                    if ("stop".equals(cmd)) {
                        garminManager.destroy();
                    } else if ("start".equals(cmd)) {
                        garminManager.initialize();
                    }
                }
            }
        };
        registerReceiver(controlReceiver, new IntentFilter("GARMIN_CONTROL"), Context.RECEIVER_EXPORTED);

        bt_connect = new ConectarMiBluetooth(bt);
        bt_connect.execute();
        bt_comm = new ComunicarConBluetooth(bt_connect.getSocket(), new ComunicarConBluetooth.BluetoothDataListener() {
            @Override
            public void onDataReceived(String data) {
                // emitirDatosAlServidor("arduino", data.trim()); // Desactivado temporalmente
            }
        });
        bt_comm.start(); // Iniciar hilo para recibir latidos

        MyData data = null;

        do {
            data = this.get_updates();
        } while (data == null || this.process(data));

        // garminManager.destroy();
        unregisterReceiver(controlReceiver);

        if (mWebSocket != null) {
            mWebSocket.close(1000, "Service terminating");
        }
    }

    private void initWebSocket() {
        try {
            String url = getUrlServidor(true);
            // Asegurar protocolo ws/wss
            if (url.startsWith("http")) {
                url = url.replace("http", "ws");
            }

            Log.e("ON-MyService", "Iniciando WebSocket hacia: " + url);
            Request request = new Request.Builder().url(url).build();

            mWebSocket = client.newWebSocket(request, new WebSocketListener() {
                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    Log.e("ON-MyService", "WebSocket conectado!");
                    // Saludo inicial
                    webSocket.send("{\"type\":\"hello\",\"source\":\"android_app\"}");
                }

                @Override
                public void onMessage(WebSocket webSocket, String text) {
                    Log.e("ON-MyService", "Mensaje del servidor: " + text);
                }

                @Override
                public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                    Log.e("ON-MyService", "Error en WebSocket: " + t.getMessage());
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
                obj.put("source", origen); // Usamos "source" para compatibilidad con el servidor

                try {
                    obj.put("bpm", Integer.parseInt(bpm));
                } catch (NumberFormatException e) {
                    obj.put("bpm", bpm);
                }

                obj.put("seq", 0); // Campo seq que espera el servidor
                obj.put("timestamp", System.currentTimeMillis());

                mWebSocket.send(obj.toString());
                Log.e("ON-MyService", "Dato enviado por WebSocket: " + obj.toString());
            } catch (Exception e) {
                Log.e("ON-MyService", "Error enviando dato por WebSocket", e);
            }
        } else {
            Log.e("ON-MyService", "No se pudo enviar: WebSocket no inicializado");
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
            // CAMBIO: se codifica el texto para evitar errores si el mensaje tiene espacios, acentos o saltos de línea.
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
        Log.e("ON-MyService", "get_updates()");
        MyData data = null;
        resTelegram = ""; // ✅ Limpia antes de cada consulta

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
            Log.e("ON-MyService", "get_updates(): status=" + status);

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
                Log.e("ON-MyService", "get_updates(): respuesta=" + resTelegram);

                // ✅ Parsea y obtiene los mensajes correctamente
                this.parser = new MyJSONParser("[" + resTelegram + "]");
                data = this.parser.getValue();

                Log.e("ON-MyService", "get_updates(): mensajes encontrados=" + data.msg.size());
            }

            conn.disconnect();

        } catch (MalformedURLException e) {
            Log.e("ON-MyService", "get_updates(): MalformedURLException", e);
            if (conn != null)
                conn.disconnect();
        } catch (IOException e) {
            Log.e("ON-MyService", "get_updates(): IOException", e);
            if (conn != null)
                conn.disconnect();
        }

        return data;
    }

    private boolean process(MyData data) {
        Log.e("ON-MyService", "process()");

        // ✅ Verifica que haya mensajes antes de procesar
        if (data.msg == null || data.msg.size() == 0) {
            Log.e("ON-MyService", "process(): No hay mensajes nuevos");
            return true;
        }

        for (int i = 0; i < data.msg.size(); ++i) {
            int update_id = data.update_id.get(i);
            String msg = data.msg.get(i);

            Log.e("ON-MyService", "process(): mensaje=[" + msg + "]");

            this.offset = update_id + 1;

            // NUEVO: aquí entra el modelo de IA.
            // Antes se usaban muchos if con msg.contains("ENCENDER"), msg.contains("APAGAR"), etc.
            // Ahora el mensaje en lenguaje natural se manda al modelo y el modelo devuelve la intención detectada.
            String comandoIA = modeloIA.predecir(msg);

            Log.e("IA-MODEL", "Comando detectado por IA: " + comandoIA);

            if (comandoIA.equals("ENCENDER")) {
                bt_comm.write("1\n");
                this.send("Sensor Encendido");
            }

            else if (comandoIA.equals("APAGAR")) {
                bt_comm.write("0\n");
                this.send("Sensor Apagado");
            }

            else if (comandoIA.equals("LEER")) {
                Log.e("ON-MyService", "process(): Ejecutando LEER");
                String r = get();
                if (r.isEmpty()) {
                    this.send("El servidor no respondió o está vacío");
                } else {
                    this.send("Info del servidor:\n" + r);
                }
            }

            // ── ESP32 Serial-Monitor commands forwarded via BT ────────────────

            else if (comandoIA.equals("STATUS")) {
                Log.e("ON-MyService", "process(): STATUS detectado por IA");
                bt_comm.write("status\n");
                this.send("Comando STATUS enviado al ESP32");
            }

            else if (comandoIA.equals("RECONECTAR")) {
                Log.e("ON-MyService", "process(): RECONECTAR detectado por IA");
                bt_comm.write("reconnect\n");
                this.send("Comando RECONECTAR enviado al ESP32");
            }

            // SET WIFI <ssid> <password> or SET WIFI <ssid>
            else if (comandoIA.equals("SET_WIFI")) {
                String params = extraerParametrosSetWifi(msg);

                if (params.isEmpty()) {
                    // CAMBIO: si la IA detecta SET WIFI pero no hay parámetros claros, no se ejecuta.
                    this.send("SET WIFI detectado, pero faltan parámetros. Usa: SET WIFI nombre_red contraseña");
                } else {
                    Log.e("ON-MyService", "process(): SET WIFI params=[" + params + "]");
                    bt_comm.write("set wifi " + params + "\n");
                    this.send("Comando SET WIFI enviado al ESP32: " + params);
                }
            }

            // SET SERVER <host> <port> or SET SERVER <host>
            else if (comandoIA.equals("SET_SERVER")) {
                String params = extraerParametrosSetServer(msg);

                if (params.isEmpty()) {
                    // CAMBIO: si la IA detecta SET SERVER pero no hay IP/host claro, no se ejecuta.
                    this.send("SET SERVER detectado, pero faltan parámetros. Usa: SET SERVER host puerto");
                } else {
                    Log.e("ON-MyService", "process(): SET SERVER params=[" + params + "]");
                    bt_comm.write("set server " + params + "\n");
                    this.send("Comando SET SERVER enviado al ESP32: " + params);
                }
            }

            else if (comandoIA.equals("TERMINAR")) {
                Log.e("ON-MyService", "process(): TERMINAR recibido");
                return false;
            }

            else {
                // CAMBIO: si el mensaje no coincide con una intención válida, no se involucra el sistema.
                // Esto cumple la parte de sintaxis invalidable: si no se entiende o no es válido, no ejecuta nada.
                Log.e("IA-MODEL", "Mensaje ignorado por no coincidir con comandos válidos");
            }
        }

        return true;
    }

    // NUEVO: obtiene parámetros para SET WIFI.
    // Si el usuario escribe el comando formal, se respeta:
    // SET WIFI miRed miPassword
    // Si escribe lenguaje natural, intenta tomar lo que venga después de palabras clave.
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

    // NUEVO: obtiene parámetros para SET SERVER.
    // Se usa para frases como:
    // SET SERVER 192.168.1.50 3010
    // cambia el servidor a 192.168.1.50 3010
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