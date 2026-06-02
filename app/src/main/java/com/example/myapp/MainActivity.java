package com.example.myapp;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.example.myapp.R;
import com.example.myapp.background.MyService;
import com.example.myapp.data.ConfigUtils;
import androidx.core.content.ContextCompat;

public class MainActivity extends Activity {
    int LAUNCH_SIMPLE_ACTIVITY = 1;
    EditText etServerIp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etServerIp = (EditText) findViewById(R.id.etServerIp);

        // Cargar IP previamente guardada
        etServerIp.setText(ConfigUtils.getCleanIp(this));
    }

    public void onSaveIp(View v) {
        String ip = etServerIp.getText().toString().trim();
        if (ip.isEmpty()) {
            Toast.makeText(this, "Por favor ingresa una IP o puerto válido", Toast.LENGTH_SHORT).show();
            return;
        }
        ConfigUtils.saveServerIp(this, ip);
        Toast.makeText(this, "Dirección IP guardada con éxito", Toast.LENGTH_SHORT).show();
    }

    public void onService(View v) {
        Log.e("ON-MainActivity", "onService()");
        try {
            Intent act = new Intent(this, SimpleActivity.class);
            startActivityForResult(act, LAUNCH_SIMPLE_ACTIVITY);
        }catch(Exception e){
            Log.e("ON-MainActivity", "onService(): Exception", e);
        }
    }

    @SuppressLint("MissingPermission")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        Log.e("ON-MainActivity", "onActivityResult()");

        if( requestCode == LAUNCH_SIMPLE_ACTIVITY ) {
            if( resultCode == SimpleActivity.RESULT_OK && data != null) {
                Log.e("ON-MainActivity", "onActivityResult(): LAUNCH_SIMPLE_ACTIVITY OK");
                
                Intent demon = new Intent(this, MyService.class);
                BluetoothDevice bt = data.getParcelableExtra(SimpleActivity.TAG_BLUETOOTH_DEVICE);
                
                if (bt != null) {
                    demon.putExtra(SimpleActivity.TAG_BLUETOOTH_DEVICE, bt);
                    ContextCompat.startForegroundService(this, demon);
                    
                    // Abrir la Central de Control inmediatamente
                    Intent dashboardIntent = new Intent(this, DashboardActivity.class);
                    startActivity(dashboardIntent);
                }
            } else {
                Log.e("ON-MainActivity", "onActivityResult(): Cancelado o sin datos");
            }
        }
    }

    public void onGarmin(View v) {
        Intent intent = new Intent(this, DashboardActivity.class);
        startActivity(intent);
    }
}