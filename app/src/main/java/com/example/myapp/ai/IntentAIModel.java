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

    private String replaceSynonyms(String normalizedText) {
        String s = " " + normalizedText + " ";
        
        // Raíces de APAGAR
        s = s.replaceAll("(?i)\\b(apaga[n]?|apagalo|apagala|apagame|apagado|desactiva[n]?|desactivalo|desactivala|deten[n]?|detiene[n]?|detenlo|detenla|detenerlo|para[sn]?|paralo|parala|suspende[n]?|termina[n]?|desconecta[n]?|desconectalo|desconectala)\\b", "apagar");
        
        // Raíces de ENCENDER
        s = s.replaceAll("(?i)\\b(enciende[n]?|encendiendo|encendido|prendi[o]?[a-z]*|prende[n]?|prendelo|prendela|prendeme|activa[n]?|activalo|activala|inicia[sn]?|inicialo|iniciala|arranca[sn]?|arrancalo|comienza[n]?|empieza[n]?|habilita[n]?|habilitar|conecta[n]?|conectalo|conectala)\\b", "encender");
        
        // Raíces de LEER
        s = s.replaceAll("(?i)\\b(lee[sn]?|leyendo|leido|consulta[sn]?|muestra[sn]?|mostrarmelo|muestramelo|trae[rn]?|traelo|obten[g]?[o]?[a-z]*|obtenerlo|analiza[rn]?|verlo|registros|lecturas|mediciones|datos)\\b", "leer");
        
        // Raíces de STATUS
        s = s.replaceAll("(?i)\\b(estado|estatus|status|conexion|conectado|conectividad|revisa[rn]?|verifica[rn]?|comprueba[rn]?|funcionamiento|funcionando|info|informacion)\\b", "status");
        
        // Raíces de WIFI
        s = s.replaceAll("(?i)\\b(wifi|red|net|ssid|contrase[nñ]a|contra|clave|password|pass)\\b", "wifi");
        
        // Raíces de IP
        s = s.replaceAll("(?i)\\b(ip|servidor|server|host|puerto|port)\\b", "ip");
        
        return s.trim().replaceAll("\\s+", " ");
    }

    private Intent semanticMatch(String preprocessedText) {
        String s = " " + preprocessedText + " ";
        
        // 1. SET_WIFI (Alta prioridad: configuración de red)
        if (s.contains(" wifi ")) {
            return Intent.SET_WIFI;
        }

        // 2. SET_IP (Alta prioridad: configuración de servidor)
        if (s.contains(" ip ")) {
            return Intent.SET_IP;
        }

        // 3. APAGAR
        if (s.contains(" apagar ")) {
            return Intent.APAGAR;
        }
        
        // 4. ENCENDER
        if (s.contains(" encender ")) {
            return Intent.ENCENDER;
        }

        // 5. STATUS
        if (s.contains(" status ")) {
            return Intent.STATUS;
        }

        // 6. LEER
        if (s.contains(" leer ")) {
            return Intent.LEER;
        }

        return null;
    }

    public Prediction predict(String rawText) {
        String normalizedText = normalize(rawText);
        if (normalizedText.length() == 0) {
            return new Prediction(Intent.INVALIDO, 0.0);
        }

        String preprocessedText = replaceSynonyms(normalizedText);

        // 1. Intentar coincidencia semántica / heurística de alta prioridad
        Intent semanticIntent = semanticMatch(preprocessedText);
        if (semanticIntent != null) {
            Prediction p = new Prediction(semanticIntent, 1.0); // Confianza máxima para coincidencia semántica
            return validateAndBuildCommand(p, rawText, preprocessedText);
        }

        // 2. Si no coincide semánticamente, recurrir al clasificador probabilístico Naive Bayes
        Prediction prediction = classify(preprocessedText);

        if (prediction.confidence < MIN_CONFIDENCE) {
            return new Prediction(Intent.INVALIDO, prediction.confidence);
        }

        return validateAndBuildCommand(prediction, rawText, preprocessedText);
    }

    private void buildTrainingData() {
        add(Intent.ENCENDER,
                "encender", "prender", "activar", "iniciar sensor", "enciende el sensor",
                "activa el sensor cardiaco", "comienza la lectura", "inicia la lectura",
                "empieza a medir", "arranca el sensor", "quiero iniciar el sensor",
                "conectar sensor", "dale energia al sensor", "activa el monitoreo",
                "comienza a registrar", "habilita las lecturas", "enciendelo por favor",
                "prende el bluetooth", "iniciar transmision");

        add(Intent.APAGAR,
                "apagar", "detener", "desactivar", "apaga el sensor", "deten la lectura",
                "para el sensor", "suspende la medicion", "deja de enviar datos",
                "termina la lectura", "desconecta el sensor cardiaco",
                "apagar el sensor", "desconectar el sensor", "apaga la medicion",
                "para de medir", "quita la energia", "detener el monitoreo",
                "apagarlo por favor", "desconecta el bluetooth", "cancela las lecturas");

        add(Intent.LEER,
                "leer", "consultar datos", "leer informacion", "trae los datos", "muestra las lecturas",
                "consulta el servidor", "obtener informacion", "ver registros", "analiza los datos",
                "quiero ver la informacion guardada", "ver datos", "mostrar registros",
                "descargar base de datos", "dame los registros", "muestra la informacion",
                "traer historico", "ver mediciones anteriores", "que midio el sensor",
                "trae las lecturas de hoy", "obtener historicos");

        add(Intent.STATUS,
                "status", "estado", "estatus", "revisa estado", "como esta conectado",
                "verifica conexion", "comprueba el servidor", "estado del sistema", "estado del sensor",
                "dime si esta conectado", "revisa si el servidor esta activo",
                "estado de conexion", "esta conectado", "dime si funciona", "checa la conexion",
                "verificar estado", "saber si esta activo", "revisar bluetooth", "esta prendido");

        add(Intent.SET_WIFI,
                "set wifi", "cambiar wifi", "configurar wifi", "cambia la red", "modifica internet",
                "actualiza la red wifi", "conecta a esta red", "cambia ssid", "cambia contrasena wifi",
                "pon la red de internet", "usa esta red wifi", "cambia la contra del net",
                "cambia la contraseña a la red", "cambia contraseña", "cambiar la clave de la red",
                "contraseña de la red", "conecta el wifi a la red", "clave del wifi",
                "cambia la contra a la red", "contra 12345678", "password de la red net",
                "configurar internet", "conectar a la red", "actualizar credenciales wifi",
                "poner wifi de la casa", "modificar clave wifi", "conectar al wifi", "cambiar ssid de red");

        add(Intent.SET_IP,
                "set ip", "cambiar ip", "configurar ip", "cambia servidor", "modifica servidor",
                "actualiza la ip del servidor", "pon la ip", "cambia host", "set server",
                "conecta al servidor", "usa esta direccion del servidor", "cambia la ip de la red",
                "dirección del host del servidor", "cambiar ip del servidor",
                "configurar ip del servidor", "cambiar direccion del host", "cambiar puerto del servidor",
                "actualizar ip de la base de datos", "poner servidor ip", "modificar host");

        add(Intent.INVALIDO,
                "hola", "gracias", "buenos dias", "que haces", "cuentame algo",
                "ayuda", "mensaje de prueba", "como estas", "no se", "texto cualquiera",
                "adios", "saludos", "buenas tardes", "buenas noches", "quien eres");
    }

    private void add(Intent intent, String... examples) {
        ArrayList<String[]> list = trainingData.get(intent);
        if (list == null) {
            list = new ArrayList<>();
            trainingData.put(intent, list);
        }
        for (String ex : examples) {
            String normalized = normalize(ex);
            String preprocessed = replaceSynonyms(normalized);
            list.add(tokenize(preprocessed));
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

    private Prediction classify(String preprocessedText) {
        String[] tokens = tokenize(preprocessedText);
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
                // CORRECCIÓN OOV: Ignorar palabras completamente desconocidas que no forman parte del vocabulario de entrenamiento
                if (!vocabulary.contains(token)) continue;

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

    private Prediction validateAndBuildCommand(Prediction p, String rawText, String preprocessedText) {
        switch (p.intent) {
            case ENCENDER:
                p.valid = true;
                p.officialCommand = "ENCENDER";
                p.bluetoothPayload = "1\n";
                p.userResponse = "IA: instruccion detectada ENCENDER. Sensor encendido.";
                return p;

            case APAGAR:
                p.valid = true;
                p.officialCommand = "APAGAR";
                p.bluetoothPayload = "0\n";
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
                return buildWifiCommand(p, rawText, preprocessedText);

            case SET_IP:
                return buildServerCommand(p, rawText, preprocessedText);

            default:
                return new Prediction(Intent.INVALIDO, p.confidence);
        }
    }

    private static class WifiParams {
        String ssid = "";
        String password = "";
    }

    private WifiParams extractWifiParams(String rawText) {
        WifiParams params = new WifiParams();
        
        // 1. Intentar buscar comillas (dobles, simples o curvadas)
        // Reemplazar diferentes tipos de comillas por " para simplificar
        String cleaned = rawText.replaceAll("[\"''“”指標]", "\"");
        ArrayList<String> quoted = new ArrayList<>();
        Matcher quoteMatcher = Pattern.compile("\"([^\"]+)\"").matcher(cleaned);
        while (quoteMatcher.find()) {
            quoted.add(quoteMatcher.group(1).trim());
        }
        
        // Si hay comillas, asignarlas
        if (quoted.size() >= 2) {
            params.ssid = quoted.get(0);
            params.password = quoted.get(1);
            return params;
        } else if (quoted.size() == 1) {
            params.ssid = quoted.get(0);
        }

        // 2. Extracción heurística por palabras clave (SSID y Password)
        String lower = rawText.toLowerCase();
        int ssidIdx = -1;
        String ssidKeyUsed = "";
        String[] ssidKeys = {"ssid", "red", "wifi"};
        for (String key : ssidKeys) {
            int idx = lower.indexOf(key);
            if (idx != -1 && (ssidIdx == -1 || idx < ssidIdx)) {
                ssidIdx = idx;
                ssidKeyUsed = key;
            }
        }

        int passIdx = -1;
        String passKeyUsed = "";
        String[] passKeys = {"password", "contrasena", "contraseña", "contra", "clave", "pass"};
        for (String key : passKeys) {
            int idx = lower.indexOf(key);
            if (idx != -1 && (passIdx == -1 || idx < passIdx)) {
                passIdx = idx;
                passKeyUsed = key;
            }
        }

        // Caso A: Se encontraron ambos y el SSID va antes que el Password (e.g. "red Casa con contra 123")
        if (ssidIdx != -1 && passIdx != -1 && ssidIdx < passIdx) {
            if (params.ssid.isEmpty()) {
                String ssidPart = rawText.substring(ssidIdx + ssidKeyUsed.length(), passIdx).trim();
                // Limpiar conectores comunes al inicio y final
                ssidPart = ssidPart.replaceAll("^(?i)(?:de\\s+|la\\s+|con\\s+|a\\s+)+", "");
                ssidPart = ssidPart.replaceAll("(?i)\\s+(?:con|de|la|a)$", "");
                ssidPart = ssidPart.replaceAll("^[:=,\\s]+", "").replaceAll("[:=,\\s]+$", "");
                params.ssid = ssidPart.trim();
            }
            if (params.password.isEmpty()) {
                String passPart = rawText.substring(passIdx + passKeyUsed.length()).trim();
                passPart = passPart.replaceAll("^[:=,\\s]+", "");
                int endIdx = passPart.indexOf('\n');
                if (endIdx != -1) passPart = passPart.substring(0, endIdx);
                params.password = passPart.trim();
            }
        }
        // Caso B: Se encontraron ambos y el Password va antes que el SSID (e.g. "contra 123 red Casa")
        else if (ssidIdx != -1 && passIdx != -1 && passIdx < ssidIdx) {
            if (params.password.isEmpty()) {
                String passPart = rawText.substring(passIdx + passKeyUsed.length(), ssidIdx).trim();
                passPart = passPart.replaceAll("^(?i)(?:de\\s+|la\\s+|con\\s+|a\\s+)+", "");
                passPart = passPart.replaceAll("(?i)\\s+(?:con|de|la|a)$", "");
                passPart = passPart.replaceAll("^[:=,\\s]+", "").replaceAll("[:=,\\s]+$", "");
                params.password = passPart.trim();
            }
            if (params.ssid.isEmpty()) {
                String ssidPart = rawText.substring(ssidIdx + ssidKeyUsed.length()).trim();
                ssidPart = ssidPart.replaceAll("^[:=,\\s]+", "");
                int endIdx = ssidPart.indexOf('\n');
                if (endIdx != -1) ssidPart = ssidPart.substring(0, endIdx);
                params.ssid = ssidPart.trim();
            }
        }
        // Caso C: Solo se encontró el SSID (e.g. "conecta al wifi Mi Red")
        else if (ssidIdx != -1 && params.ssid.isEmpty()) {
            String ssidPart = rawText.substring(ssidIdx + ssidKeyUsed.length()).trim();
            ssidPart = ssidPart.replaceAll("^(?i)(?:de\\s+|la\\s+|con\\s+|a\\s+)+", "");
            ssidPart = ssidPart.replaceAll("^[:=,\\s]+", "");
            int endIdx = ssidPart.indexOf('\n');
            if (endIdx != -1) ssidPart = ssidPart.substring(0, endIdx);
            params.ssid = ssidPart.trim();
        }
        // Caso D: Solo se encontró el Password
        else if (passIdx != -1 && params.password.isEmpty()) {
            String passPart = rawText.substring(passIdx + passKeyUsed.length()).trim();
            passPart = passPart.replaceAll("^[:=,\\s]+", "");
            int endIdx = passPart.indexOf('\n');
            if (endIdx != -1) passPart = passPart.substring(0, endIdx);
            params.password = passPart.trim();
        }

        // Si tenemos contraseña pero falta SSID, intentar extraer la palabra antes de la contraseña
        if (params.ssid.isEmpty() && !params.password.isEmpty() && passIdx > 0) {
            String beforePass = rawText.substring(0, passIdx).trim();
            String[] words = beforePass.split("\\s+");
            if (words.length > 0) {
                String candidate = words[words.length - 1];
                if (!candidate.toLowerCase().matches("^(con|de|la|el|a|y)$")) {
                    params.ssid = candidate;
                }
            }
        }

        return params;
    }

    private Prediction buildWifiCommand(Prediction p, String rawText, String preprocessedText) {
        WifiParams params = extractWifiParams(rawText);

        if (params.ssid.isEmpty()) {
            p.valid = false;
            p.officialCommand = "SET WIFI INVALIDO";
            p.userResponse = "IA: intencion SET WIFI detectada, pero falta SSID/red. No se ejecuto.";
            return p;
        }

        p.valid = true;
        p.officialCommand = "SET WIFI";
        if (params.password.isEmpty()) {
            p.bluetoothPayload = "set wifi \"" + params.ssid + "\"\n";
        } else {
            p.bluetoothPayload = "set wifi \"" + params.ssid + "\" " + params.password + "\n";
        }
        p.userResponse = "IA: instruccion detectada SET WIFI. Enviando nueva red al ESP32.";
        return p;
    }

    private Prediction buildServerCommand(Prediction p, String rawText, String preprocessedText) {
        String host = findIpOrHost(rawText);
        int port = findPort(rawText);

        if (host.length() == 0) {
            // Extracción heurística si no coincide con los formatos IP/dominio estrictos
            String lower = rawText.toLowerCase();
            int serverIdx = -1;
            String[] serverKeys = {"servidor", "server", "ip", "host"};
            String keyUsed = "";
            for (String key : serverKeys) {
                int idx = lower.indexOf(key);
                if (idx != -1 && (serverIdx == -1 || idx < serverIdx)) {
                    serverIdx = idx;
                    keyUsed = key;
                }
            }
            if (serverIdx != -1) {
                String candidate = rawText.substring(serverIdx + keyUsed.length()).trim();
                candidate = candidate.replaceAll("^[:=,\\s]+", "");
                candidate = candidate.replaceAll("^(?i)(?:de\\s+|la\\s+|con\\s+|a\\s+|en\\s+)+", "");
                String[] words = candidate.split("\\s+");
                if (words.length > 0) {
                    host = words[0].replaceAll("[^a-zA-Z0-9.-]", "");
                }
            }
        }

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
            Pattern p = Pattern.compile("(?i)" + Pattern.quote(key) + "\\s*[:=]\\s*[\"''“”指標]?([^\\\";,\\n]+)[\"''“”指標]?");
            Matcher m = p.matcher(raw);
            if (m.find()) return m.group(1).trim();
        }
        return "";
    }

    private String findQuoted(String raw, int index) {
        Matcher m = Pattern.compile("[\"''“”指標]([^\"''“”指標]+)[\"''指標]").matcher(raw);
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

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("   IntentAIModel - Consola de Prueba Standalone   ");
        System.out.println("==================================================");
        System.out.println("Inicializando modelo local Naive Bayes...");
        
        IntentAIModel model = new IntentAIModel();
        System.out.println("¡Modelo cargado y entrenado exitosamente!");
        System.out.println("Escribe tus instrucciones (o escribe 'salir' para terminar):");
        System.out.println("--------------------------------------------------");

        java.util.Scanner scanner = new java.util.Scanner(System.in);
        while (true) {
            System.out.print("> ");
            if (!scanner.hasNextLine()) break;
            String input = scanner.nextLine().trim();
            if (input.equalsIgnoreCase("salir") || input.equalsIgnoreCase("exit")) {
                System.out.println("Saliendo de la consola de pruebas. ¡Hasta luego!");
                break;
            }

            if (input.isEmpty()) continue;

            Prediction pred = model.predict(input);
            System.out.println("\n[Resultado de Inferencia]");
            System.out.println(" - Intencion Inferida: " + pred.intent);
            System.out.println(" - Nivel de Confianza: " + String.format(Locale.US, "%.4f", pred.confidence));
            System.out.println(" - ¿Es Valida?:       " + (pred.valid ? "SI" : "NO"));
            System.out.println(" - Comando Oficial:   " + pred.officialCommand);
            System.out.println(" - Payload Bluetooth:  " + (pred.bluetoothPayload.isEmpty() ? "(vacio)" : pred.bluetoothPayload.replace("\n", "\\n")));
            System.out.println(" - Respuesta Usuario:  " + pred.userResponse);
            System.out.println("--------------------------------------------------");
        }
        scanner.close();
    }
}
