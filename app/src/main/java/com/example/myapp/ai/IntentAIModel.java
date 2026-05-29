package com.example.myapp.ai;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Modelo local de IA para clasificar intenciones en lenguaje natural.
 *
 * Tipo de modelo: Naive Bayes Multinomial.
 * La inferencia corre dentro de Android.
 */
public class IntentAIModel {

    public enum Intent {
        ENCENDER,
        APAGAR,
        LEER,
        STATUS,
        SET_WIFI,
        SET_IP,
        INVALIDO
    }

    public static class Prediction {
        public Intent intent;
        public double confidence;
        public String officialCommand;
        public String bluetoothPayload;
        public String userResponse;
        public boolean valid;

        public Prediction(Intent intent, double confidence) {
            this.intent = intent;
            this.confidence = confidence;
            this.valid = false;
            this.officialCommand = "INVALIDO";
            this.bluetoothPayload = "";
            this.userResponse = "Comando no reconocido. No se ejecuto ninguna accion.";
        }
    }

    private static final double MIN_CONFIDENCE = 0.35;

    private final Map<Intent, ArrayList<String[]>> trainingData = new HashMap<>();
    private final Map<Intent, Map<String, Integer>> wordCountByIntent = new HashMap<>();
    private final Map<Intent, Integer> totalWordsByIntent = new HashMap<>();
    private final Map<Intent, Integer> documentCountByIntent = new HashMap<>();
    private final Set<String> vocabulary = new HashSet<>();
    private int totalDocuments = 0;

    public IntentAIModel() {
        buildTrainingData();
        train();
    }

    public Prediction predict(String rawText) {
        String text = normalize(rawText);
        if (text.length() == 0) {
            return new Prediction(Intent.INVALIDO, 0.0);
        }

        Prediction prediction = classify(text);

        if (prediction.confidence < MIN_CONFIDENCE) {
            return new Prediction(Intent.INVALIDO, prediction.confidence);
        }

        return validateAndBuildCommand(prediction, rawText, text);
    }

    private void buildTrainingData() {
        add(Intent.ENCENDER,
                "encender", "prender", "activar", "iniciar sensor", "enciende el sensor",
                "activa el sensor cardiaco", "comienza la lectura", "inicia la lectura",
                "empieza a medir", "arranca el sensor", "quiero iniciar el sensor");

        add(Intent.APAGAR,
                "apagar", "detener", "desactivar", "apaga el sensor", "deten la lectura",
                "para el sensor", "suspende la medicion", "deja de enviar datos",
                "termina la lectura", "desconecta el sensor cardiaco");

        add(Intent.LEER,
                "leer", "consultar datos", "leer informacion", "trae los datos", "muestra las lecturas",
                "consulta el servidor", "obtener informacion", "ver registros", "analiza los datos",
                "quiero ver la informacion guardada");

        add(Intent.STATUS,
                "status", "estado", "estatus", "revisa estado", "como esta conectado",
                "verifica conexion", "comprueba el servidor", "estado del sistema", "estado del sensor",
                "dime si esta conectado", "revisa si el servidor esta activo");

        add(Intent.SET_WIFI,
                "set wifi", "cambiar wifi", "configurar wifi", "cambia la red", "modifica internet",
                "actualiza la red wifi", "conecta a esta red", "cambia ssid", "cambia contrasena wifi",
                "pon la red de internet", "usa esta red wifi");

        add(Intent.SET_IP,
                "set ip", "cambiar ip", "configurar ip", "cambia servidor", "modifica servidor",
                "actualiza la ip del servidor", "pon la ip", "cambia host", "set server",
                "conecta al servidor", "usa esta direccion del servidor");

        add(Intent.INVALIDO,
                "hola", "gracias", "buenos dias", "que haces", "cuentame algo",
                "ayuda", "mensaje de prueba", "como estas", "no se", "texto cualquiera");
    }

    private void add(Intent intent, String... examples) {
        ArrayList<String[]> list = trainingData.get(intent);
        if (list == null) {
            list = new ArrayList<>();
            trainingData.put(intent, list);
        }
        for (String ex : examples) {
            list.add(tokenize(normalize(ex)));
        }
    }

    private void train() {
        for (Intent intent : trainingData.keySet()) {
            wordCountByIntent.put(intent, new HashMap<String, Integer>());
            totalWordsByIntent.put(intent, 0);
            documentCountByIntent.put(intent, trainingData.get(intent).size());
            totalDocuments += trainingData.get(intent).size();

            for (String[] tokens : trainingData.get(intent)) {
                for (String token : tokens) {
                    if (token.length() < 2) continue;
                    vocabulary.add(token);
                    Map<String, Integer> wc = wordCountByIntent.get(intent);
                    wc.put(token, wc.containsKey(token) ? wc.get(token) + 1 : 1);
                    totalWordsByIntent.put(intent, totalWordsByIntent.get(intent) + 1);
                }
            }
        }
    }

    private Prediction classify(String normalizedText) {
        String[] tokens = tokenize(normalizedText);
        Intent bestIntent = Intent.INVALIDO;
        double bestScore = Double.NEGATIVE_INFINITY;
        double secondScore = Double.NEGATIVE_INFINITY;

        for (Intent intent : trainingData.keySet()) {
            double prior = Math.log((documentCountByIntent.get(intent) + 1.0) /
                    (totalDocuments + trainingData.keySet().size()));
            double score = prior;

            Map<String, Integer> wc = wordCountByIntent.get(intent);
            int totalWords = totalWordsByIntent.get(intent);
            int vocabSize = Math.max(vocabulary.size(), 1);

            for (String token : tokens) {
                if (token.length() < 2) continue;
                int count = wc.containsKey(token) ? wc.get(token) : 0;
                score += Math.log((count + 1.0) / (totalWords + vocabSize));
            }

            if (score > bestScore) {
                secondScore = bestScore;
                bestScore = score;
                bestIntent = intent;
            } else if (score > secondScore) {
                secondScore = score;
            }
        }

        double confidence = 1.0 / (1.0 + Math.exp(-(bestScore - secondScore)));
        return new Prediction(bestIntent, confidence);
    }

    private Prediction validateAndBuildCommand(Prediction p, String rawText, String normalizedText) {
        switch (p.intent) {
            case ENCENDER:
                p.valid = true;
                p.officialCommand = "ENCENDER";
                p.bluetoothPayload = "1";
                p.userResponse = "IA: instruccion detectada ENCENDER. Sensor encendido.";
                return p;

            case APAGAR:
                p.valid = true;
                p.officialCommand = "APAGAR";
                p.bluetoothPayload = "0";
                p.userResponse = "IA: instruccion detectada APAGAR. Sensor apagado.";
                return p;

            case LEER:
                p.valid = true;
                p.officialCommand = "LEER";
                p.bluetoothPayload = "";
                p.userResponse = "IA: instruccion detectada LEER. Consultando servidor.";
                return p;

            case STATUS:
                p.valid = true;
                p.officialCommand = "STATUS";
                p.bluetoothPayload = "status\n";
                p.userResponse = "IA: instruccion detectada STATUS. Solicitando estado al ESP32.";
                return p;

            case SET_WIFI:
                return buildWifiCommand(p, rawText, normalizedText);

            case SET_IP:
                return buildServerCommand(p, rawText, normalizedText);

            default:
                return new Prediction(Intent.INVALIDO, p.confidence);
        }
    }

    private Prediction buildWifiCommand(Prediction p, String rawText, String normalizedText) {
        String ssid = findValue(rawText, "ssid", "red", "wifi");
        String pass = findValue(rawText, "password", "pass", "contrasena", "contraseña", "clave");

        if (ssid.length() == 0) {
            ssid = findQuoted(rawText, 0);
            pass = findQuoted(rawText, 1);
        }

        if (ssid.length() == 0) {
            p.valid = false;
            p.officialCommand = "SET WIFI INVALIDO";
            p.userResponse = "IA: intencion SET WIFI detectada, pero falta SSID/red. No se ejecuto.";
            return p;
        }

        p.valid = true;
        p.officialCommand = "SET WIFI";
        if (pass.length() == 0) {
            p.bluetoothPayload = "set wifi \"" + ssid + "\"\n";
        } else {
            p.bluetoothPayload = "set wifi \"" + ssid + "\" " + pass + "\n";
        }
        p.userResponse = "IA: instruccion detectada SET WIFI. Enviando nueva red al ESP32.";
        return p;
    }

    private Prediction buildServerCommand(Prediction p, String rawText, String normalizedText) {
        String host = findIpOrHost(rawText);
        int port = findPort(rawText);

        if (host.length() == 0) {
            p.valid = false;
            p.officialCommand = "SET IP INVALIDO";
            p.userResponse = "IA: intencion SET IP detectada, pero falta IP/host del servidor. No se ejecuto.";
            return p;
        }

        p.valid = true;
        p.officialCommand = "SET IP";
        if (port > 0 || port == -1) {
            p.bluetoothPayload = "set server " + host + " " + port + "\n";
        } else {
            p.bluetoothPayload = "set server " + host + "\n";
        }
        p.userResponse = "IA: instruccion detectada SET IP. Enviando servidor al ESP32.";
        return p;
    }

    private String findValue(String raw, String... keys) {
        for (String key : keys) {
            Pattern p = Pattern.compile("(?i)" + Pattern.quote(key) + "\\s*[:=]\\s*\\\"?([^\\\";,\\n]+)\\\"?");
            Matcher m = p.matcher(raw);
            if (m.find()) return m.group(1).trim();
        }
        return "";
    }

    private String findQuoted(String raw, int index) {
        Matcher m = Pattern.compile("\"([^\"]+)\"").matcher(raw);
        int i = 0;
        while (m.find()) {
            if (i == index) return m.group(1).trim();
            i++;
        }
        return "";
    }

    private String findIpOrHost(String raw) {
        Matcher ip = Pattern.compile("\\b((25[0-5]|2[0-4]\\d|1?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|1?\\d?\\d)){3})\\b").matcher(raw);
        if (ip.find()) return ip.group(1);

        Matcher host = Pattern.compile("(?i)\\b([a-z0-9-]+(\\.[a-z0-9-]+)+)\\b").matcher(raw);
        if (host.find()) return host.group(1);

        return "";
    }

    private int findPort(String raw) {
        Matcher explicit = Pattern.compile("(?i)(puerto|port)\\s*[:=]?\\s*(-?\\d{1,5})").matcher(raw);
        if (explicit.find()) return parsePort(explicit.group(2));

        Matcher any = Pattern.compile("\\b(-?\\d{2,5})\\b").matcher(raw);
        while (any.find()) {
            int p = parsePort(any.group(1));
            if (p == -1 || (p > 0 && p < 65536)) return p;
        }
        return 0;
    }

    private int parsePort(String s) {
        try {
            int p = Integer.parseInt(s.trim());
            if (p == -1 || (p > 0 && p < 65536)) return p;
        } catch (Exception ignored) { }
        return 0;
    }

    private String[] tokenize(String s) {
        return s.split("\\s+");
    }

    private String normalize(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        n = n.toLowerCase(Locale.ROOT);
        n = n.replaceAll("[^a-z0-9.:-]+", " ");
        return n.trim().replaceAll("\\s+", " ");
    }
}
