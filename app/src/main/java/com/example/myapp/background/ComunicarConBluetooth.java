package com.example.myapp.background;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

// Hilo encargado de mantener la conexion y realizar las lecturas y escrituras
// de los mensajes intercambiados entre dispositivos.
public class ComunicarConBluetooth extends Thread {
    private final InputStream inputStream;    // Flujo de entrada (lecturas)
    private final OutputStream outputStream;   // Flujo de salida (escrituras)
    private boolean isRunning = true;
    private BluetoothDataListener listener;

    public interface BluetoothDataListener {
        void onDataReceived(String data);
    }

    @SuppressLint("MissingPermission")
    public ComunicarConBluetooth(BluetoothSocket socket, BluetoothDataListener listener) {
        Log.e("ON-ComunicarConBluetooth", "Constructor(): Iniciando metodo");
        this.listener = listener;

        // Se generan los flujos de entrada y salida
        InputStream tmpInputStream = null;
        OutputStream tmpOutputStream = null;

        try {
            tmpInputStream = socket.getInputStream();
            tmpOutputStream = socket.getOutputStream();
        } catch (IOException e) {
            Log.e("ON-ComunicarConBluetooth", "Constructor(): IOException", e);
        }
        inputStream = tmpInputStream;
        outputStream = tmpOutputStream;
    }

    // Metodo principal del hilo, encargado de realizar las lecturas
    public void write(String s) {
        Log.e("ON-ComunicarConBluetooth", "write()");
        byte[] out_buffer = s.getBytes();

        try {
            outputStream.write(out_buffer);
        } catch (IOException e) {
            Log.e("ON-ComunicarConBluetooth", "write(): IOException", e);
        }
    }

    @Override
    public void run() {
        Log.e("ON-ComunicarConBluetooth", "run()");
        byte[] buffer = new byte[1024];
        int bytes;

        while (isRunning) {
            try {
                // Leemos del flujo de entrada del socket
                bytes = inputStream.read(buffer);
                if (bytes > 0) {
                    String dataRecibida = new String(buffer, 0, bytes, StandardCharsets.UTF_8);
                    if (listener != null) {
                        listener.onDataReceived(dataRecibida);
                    }
                }
            } catch (IOException e) {
                Log.e("ON-ComunicarConBluetooth", "run(): IOException", e);
                break;
            }
        }
    }

    public void cancel() {
        isRunning = false;
    }
}