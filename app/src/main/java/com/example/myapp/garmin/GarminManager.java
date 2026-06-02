package com.example.myapp.garmin;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.garmin.android.connectiq.ConnectIQ;
import com.garmin.android.connectiq.IQApp;
import com.garmin.android.connectiq.IQDevice;
import com.garmin.android.connectiq.exception.InvalidStateException;
import com.garmin.android.connectiq.exception.ServiceUnavailableException;

import java.util.List;
import java.util.Map;

public class GarminManager {
    private static final String TAG = "GarminManager";
    
    // El ID de la aplicación de ConnectIQ en el reloj (mismo que en Android-Garmin)
    private static final String MY_APP_ID = "b7c2a8f1-3d4e-4c6a-9f2b-1e8d7c5a3b0e";
    
    private Context context;
    private GarminDataListener listener;
    private GarminStatusListener statusListener;

    private ConnectIQ connectIQ;
    private IQDevice connectedDevice;
    private IQApp myApp;
    private boolean isInitialized = false;

    private boolean isDestroyed = false;

    public interface GarminDataListener {
        void onDataReceived(String bpm, int seq);
    }

    public interface GarminStatusListener {
        void onStatusChanged(String status);
    }

    public GarminManager(Context context, GarminDataListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setStatusListener(GarminStatusListener statusListener) {
        this.statusListener = statusListener;
    }

    private void notifyStatus(String status) {
        if (isDestroyed) return;
        Log.e(TAG, status);
        if (statusListener != null) {
            statusListener.onStatusChanged(status);
        }
        Intent intent = new Intent("GARMIN_STATUS_UPDATE");
        intent.putExtra("status", status);
        context.sendBroadcast(intent);
    }

    private void onSdkReadyLogic() {
        if (isDestroyed) return;
        Log.e(TAG, "Garmin SDK Listo");
        isInitialized = true;
        notifyStatus("SDK listo. Buscando dispositivos...");
        buscarDispositivos();
    }

    public void initialize() {
        if (isDestroyed) return;
        Log.e(TAG, "Inicializando GarminManager...");
        notifyStatus("Inicializando SDK...");
        
        try {
            connectIQ = ConnectIQ.getInstance(context, ConnectIQ.IQConnectType.WIRELESS);
            
            boolean isAlreadyReady = false;
            try {
                connectIQ.getKnownDevices();
                isAlreadyReady = true;
            } catch (InvalidStateException e) {
                // Not initialized
            } catch (Exception e) {
                // Not initialized or other error
            }

            if (isAlreadyReady) {
                Log.e(TAG, "SDK ya estaba inicializado, saltando inicializacion.");
                onSdkReadyLogic();
                return;
            }

            connectIQ.initialize(context, true, new ConnectIQ.ConnectIQListener() {
                @Override
                public void onSdkReady() {
                    onSdkReadyLogic();
                }

                @Override
                public void onInitializeError(ConnectIQ.IQSdkErrorStatus err) {
                    if (isDestroyed) return;
                    Log.e(TAG, "Error inicializando Garmin SDK: " + err.name());
                    notifyStatus("Error SDK: " + err.name());
                }

                @Override
                public void onSdkShutDown() {
                    if (isDestroyed) return;
                    Log.e(TAG, "Garmin SDK apagado");
                    isInitialized = false;
                    notifyStatus("SDK apagado");
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Excepción al inicializar ConnectIQ", e);
            notifyStatus("Error: " + e.getMessage());
        }
    }

    private void buscarDispositivos() {
        if (isDestroyed) return;
        try {
            List<IQDevice> devices = connectIQ.getKnownDevices();
            if (devices != null && devices.size() > 0) {
                notifyStatus("Encontrados " + devices.size() + " dispositivo(s)");
                for (IQDevice device : devices) {
                    IQDevice.IQDeviceStatus status = connectIQ.getDeviceStatus(device);
                    Log.e(TAG, "Dispositivo: " + device.getFriendlyName() + " - Estado: " + status);
                    
                    if (status == IQDevice.IQDeviceStatus.CONNECTED) {
                        connectedDevice = device;
                        Log.e(TAG, "Dispositivo Garmin conectado: " + device.getFriendlyName());
                        notifyStatus("Conectado: " + device.getFriendlyName());
                        registrarApp();
                        return;
                    }
                }
                notifyStatus("Ningún dispositivo conectado");
            } else {
                notifyStatus("No se encontraron dispositivos Garmin");
            }
        } catch (InvalidStateException e) {
            Log.e(TAG, "InvalidStateException en buscarDispositivos", e);
            notifyStatus("Error: SDK en estado inválido");
        } catch (ServiceUnavailableException e) {
            Log.e(TAG, "ServiceUnavailableException en buscarDispositivos", e);
            notifyStatus("Error: Garmin Connect Mobile no disponible");
        }
    }

    private void registrarApp() {
        if (isDestroyed) return;
        myApp = new IQApp(MY_APP_ID);
        try {
            notifyStatus("Registrando app en reloj...");
            connectIQ.registerForAppEvents(connectedDevice, myApp, new ConnectIQ.IQApplicationEventListener() {
                @Override
                public void onMessageReceived(IQDevice device, IQApp app, List<Object> messages, ConnectIQ.IQMessageStatus status) {
                    if (isDestroyed) return;
                    if (messages != null && messages.size() > 0) {
                        for (Object payload : messages) {
                            Log.d(TAG, ">>> MENSAJE RECIBIDO DEL RELOJ: " + payload.toString());
                            
                            String bpmRecibido = null;
                            int seqRecibido = 0;
                            
                            if (payload instanceof Map) {
                                Map<?, ?> dataMap = (Map<?, ?>) payload;
                                Object bpmObj = dataMap.get("bpm");
                                Object seqObj = dataMap.get("seq");
                                
                                if (bpmObj instanceof Number) {
                                    bpmRecibido = String.valueOf(((Number) bpmObj).intValue());
                                }
                                if (seqObj instanceof Number) {
                                    seqRecibido = ((Number) seqObj).intValue();
                                }
                            } else {
                                bpmRecibido = payload.toString();
                            }
                            
                            if (bpmRecibido != null && listener != null) {
                                listener.onDataReceived(bpmRecibido, seqRecibido);
                                notifyStatus("BPM: " + bpmRecibido + " (seq: " + seqRecibido + ")");
                            }
                        }
                    }
                }
            });
            notifyStatus("Escuchando datos del reloj...");
        } catch (InvalidStateException e) {
            Log.e(TAG, "Error registrando app events", e);
            notifyStatus("Error al registrar eventos de la app");
        }
    }
    
    public void destroy() {
        isDestroyed = true;
        try {
            if (connectIQ != null) {
                if (connectedDevice != null && myApp != null) {
                    try {
                        connectIQ.unregisterForApplicationEvents(connectedDevice, myApp);
                    } catch (InvalidStateException e) {
                        Log.e(TAG, "Error unregister events", e);
                    }
                }
                connectIQ = null;
            }
            connectedDevice = null;
            myApp = null;
            isInitialized = false;
            Log.e(TAG, "GarminManager destruido.");
        } catch (Exception e) {
            Log.e(TAG, "Error al destruir GarminManager", e);
        }
    }
}
