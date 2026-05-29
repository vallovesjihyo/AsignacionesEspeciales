package com.example.myapp.background;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.IOException;
import java.util.UUID;

// Hilo encargado de solicitar una conexion a un dispositivo que este corriendo un
// HiloServidor.
public class ConectarMiBluetooth {
    private final BluetoothDevice dispositivo;
    private final BluetoothSocket socket;

    private static final String NOMBRE_SEGURO = "bt-prueba-123";
    private static UUID UUID_SEGURO;
    private  final UUID MY_UUID = UUID.fromString("0125bb20-d629-11e3-9c1a-0800200c9a66");
    private static final UUID BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    public static final int MSG_NINGUNO = 10;
    public static final int MSG_LEER = 11;
    public static final int MSG_ESCRIBIR = 12;

    @SuppressLint("MissingPermission")
    public ConectarMiBluetooth(BluetoothDevice dispositivo) {
        UUID_SEGURO = UUID.randomUUID();

        BluetoothSocket tmpSocket = null;
        this.dispositivo = dispositivo;

        // Obtenemos un socket para el dispositivo con el que se quiere conectar
        try {
            //tmpSocket = dispositivo.createRfcommSocketToServiceRecord(MY_UUID);
            tmpSocket = dispositivo.createRfcommSocketToServiceRecord(BTMODULEUUID);
        } catch (IOException e) {
            Log.e("HILO-CLIENTE", "HiloCliente.HiloCliente(): Error al abrir el socket", e);
        }

        socket = tmpSocket;
    }

    @SuppressLint("MissingPermission")
    public void execute()
    {
        Log.e("ON-ConectarMiBluetooth", "execute(): Iniciando metodo");

        try {
            socket.connect();
        }
        catch(IOException e) {
            Log.e("ON-ConectarMiBluetooth", "execute(): IOException", e);
            try {
                socket.close();
            }
            catch(IOException inner) {
                Log.e("ON-ConectarMiBluetooth", "execute(): IOException", inner);
            }
        }
    }

    public BluetoothSocket getSocket(){
        return this.socket;
    }
}
