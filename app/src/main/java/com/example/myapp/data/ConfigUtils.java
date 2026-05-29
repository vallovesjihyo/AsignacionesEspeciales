package com.example.myapp.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class ConfigUtils {
    private static final String KEY_SERVER_IP = "server_ip";
    private static final String DEFAULT_IP = "172.16.92.211:3010"; // Dirección por defecto

    public static void saveServerIp(Context context, String ip) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        preferences.edit().putString(KEY_SERVER_IP, ip.trim()).apply();
    }

    public static String getCleanIp(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String ip = preferences.getString(KEY_SERVER_IP, DEFAULT_IP).trim();
        
        // Normalizar limpiando protocolos si el usuario los escribió por error
        if (ip.startsWith("ws://")) {
            ip = ip.substring(5);
        } else if (ip.startsWith("wss://")) {
            ip = ip.substring(6);
        } else if (ip.startsWith("http://")) {
            ip = ip.substring(7);
        } else if (ip.startsWith("https://")) {
            ip = ip.substring(8);
        }
        return ip;
    }

    public static String getWebSocketUrl(Context context) {
        return "ws://" + getCleanIp(context);
    }

    public static String getHttpUrl(Context context) {
        return "http://" + getCleanIp(context);
    }
}
