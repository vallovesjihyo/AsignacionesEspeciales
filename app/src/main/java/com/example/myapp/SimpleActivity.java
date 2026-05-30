package com.example.myapp;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import com.example.myapp.bluetooth.BluetoothAdmin;
import com.example.myapp.bluetooth.BluetoothDeviceArrayAdapter;

import java.util.ArrayList;

import com.example.myapp.R;

public class SimpleActivity extends Activity {
    public final static String TAG_BLUETOOTH_DEVICE = "BLUETOOTH";
    private Button btnBuscarDispositivo;
    private BluetoothAdapter bAdapter;
    private ArrayList<BluetoothDevice> arrayDevices;

    private ListView lvDispositivos;


    BluetoothDevice bluetoothDevice;

    ArrayAdapter arrayAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simple);

        btnBuscarDispositivo = (Button) findViewById(R.id.btnBuscarDispositivo);
        lvDispositivos = (ListView) findViewById(R.id.lvDispositivos);

        String[] perms = {"android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION", "android.permission.BLUETOOTH", "android.permission.BLUETOOTH_ADMIN", "android.permission.BLUETOOTH_CONNECT", "android.permission.BLUETOOTH_SCAN", "android.permission.INTERNET", "com.sec.android.provider.badge.permission.WRITE", "android.permission.BLUETOOTH_ADVERTISE"};

        BluetoothAdmin.getPermissions(perms, this);

        lvDispositivos.setOnItemClickListener(new ListView.OnItemClickListener() {
            @SuppressLint("MissingPermission")
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                bluetoothDevice = arrayDevices.get(position);

                Intent returnIntent = new Intent();
                returnIntent.putExtra(TAG_BLUETOOTH_DEVICE, bluetoothDevice);
                setResult(Activity.RESULT_OK,returnIntent);

                Toast.makeText(getApplicationContext(), "" + bluetoothDevice.getName(), Toast.LENGTH_SHORT).show();

                finish();

            }
        });

        // Acciones a realizar al finalizar el proceso de descubrimiento
        // Instanciamos un nuevo adapter para el ListView mediante la clase que acabamos de crear
        arrayDevices  = new ArrayList<BluetoothDevice>();
        arrayAdapter = new BluetoothDeviceArrayAdapter(getBaseContext(), android.R.layout.simple_list_item_2, arrayDevices);

        lvDispositivos.setAdapter(arrayAdapter);

        bAdapter = BluetoothAdapter.getDefaultAdapter();
        if(bAdapter == null)
        {
            Toast.makeText(this,"Device does not support Bluetooth", Toast.LENGTH_SHORT);
            return;
        }

        if( bAdapter.isEnabled() ){
            btnBuscarDispositivo.setEnabled(true);
            
            // Cargar dispositivos vinculados/emparejados de inmediato
            try {
                @SuppressLint("MissingPermission")
                java.util.Set<BluetoothDevice> pairedDevices = bAdapter.getBondedDevices();
                if (pairedDevices != null && pairedDevices.size() > 0) {
                    for (BluetoothDevice device : pairedDevices) {
                        arrayDevices.add(device);
                    }
                    arrayAdapter.notifyDataSetChanged();
                }
            } catch (Exception e) {
                Log.e("SimpleActivity", "Error cargando vinculados", e);
            }
        }

        BluetoothAdmin.registrarEventosBluetooth(this, bReceiver);
    }

    @Override
    protected void onStop() {
        super.onStop();
        this.unregisterReceiver(bReceiver);
    }

    public void onEnd(View v) {
        this.unregisterReceiver(bReceiver);
        finish();
    }

    @SuppressLint("MissingPermission")
    public void onBuscarDispositivo(View v) {
//        if(arrayDevices != null)
        //           arrayDevices.clear();

        // Comprobamos si existe un descubrimiento en curso. En caso afirmativo, se cancela.
        if (bAdapter.isDiscovering())
            bAdapter.cancelDiscovery();

        // Iniciamos la busqueda de dispositivos y mostramos el mensaje de que el proceso ha comenzado
        if (bAdapter.startDiscovery()) {
            Toast.makeText(this, "Iniciando búsqueda de dispositivos bluetooth", Toast.LENGTH_SHORT).show();
            // Si el array no ha sido aun inicializado, lo instanciamos
            if (arrayDevices == null)
                arrayDevices = new ArrayList<BluetoothDevice>();
            else
                arrayDevices.clear();
        } else
            Toast.makeText(this, "Error al iniciar búsqueda de dispositivos bluetooth", Toast.LENGTH_SHORT).show();
    }

    @SuppressLint("MissingPermission")
    public void onBluetooth(View v) {
        Log.e("ON-SimpleActivity", "onBluetooth()");

        if( !bAdapter.isEnabled() ){
            Toast.makeText(this, "Active el Bluetooth", Toast.LENGTH_SHORT).show();
        }
    }

    private final BroadcastReceiver bReceiver = new BroadcastReceiver()
    {

        @SuppressLint("MissingPermission")
        @Override
        public void onReceive(Context context, Intent intent)
        {
            final String action = intent.getAction();

            Log.e("ON-SimpleActivity", "onReceive");

            // Filtramos por la accion. Nos interesa detectar BluetoothAdapter.ACTION_STATE_CHANGED
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action))
            {
                Log.e("ON-SimpleActivity", "onReceive(): ACTION_STATE_CHANGED");
            }
            else if (BluetoothDevice.ACTION_FOUND.equals(action))
            {
                // AGREGAR dispositivos encontrados en list view
                Log.e("ON-SimpleActivity", "onReceive(): ACTION_FOUND");

                BluetoothDevice dispositivo = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (dispositivo != null) {
                    String nombre = dispositivo.getName();
                    if (nombre != null && !nombre.isEmpty()) {
                        // Evitar duplicados en la lista
                        boolean exists = false;
                        for (BluetoothDevice d : arrayDevices) {
                            if (d.getAddress().equals(dispositivo.getAddress())) {
                                exists = true;
                                break;
                            }
                        }
                        if (!exists) {
                            arrayDevices.add(dispositivo);
                            arrayAdapter.notifyDataSetChanged();
                        }
                    }
                }
            }
            else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action))
            {
                Log.e("ON-SimpleActivity", "onReceive(): ACTION_DISCOVERY_FINISHED");

                Toast.makeText(getBaseContext(), "Fin de la búsqueda", Toast.LENGTH_SHORT).show();
            }
        }
    };

}