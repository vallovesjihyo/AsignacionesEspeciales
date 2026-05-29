package com.example.myapp;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.example.myapp.R;
import com.example.myapp.background.MiPeticionREST;
import com.example.myapp.background.MyService;
import com.example.myapp.data.ConfigUtils;
import com.example.myapp.data.MyData;
import com.example.myapp.data.MyJSONParser;
import androidx.core.content.ContextCompat;

public class MainActivity extends Activity {
    int LAUNCH_SIMPLE_ACTIVITY = 1;
    EditText etMessage;
    TextView tvResponse;
    EditText etServerIp;

    String data = null;
    MyJSONParser parser = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etMessage = (EditText) findViewById(R.id.etMessage);
        tvResponse = (TextView) findViewById(R.id.tvResponse);
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

    public void onSend(View v){
        MiPeticionREST obj = new MiPeticionREST(this, tvResponse);

        obj.execute("GET-SEND", etMessage.getText().toString());

    }

    public void onUpdate(View v){
        MiPeticionREST obj = new MiPeticionREST(this, tvResponse);

        obj.execute("GET-UPDATES");

        //Por que aquí no puede ir
        //data = this.tvResponse.getText().toString();

        //this.parser = new MyJSONParser(data);

    }

    public void onPost(View v){
        MiPeticionREST obj = new MiPeticionREST(this, tvResponse);

        obj.execute("POST", "A", "B", "C");
    }

    public void onJSON(View v) {
        //por que aqui
        if( data == null ) {
            data = this.tvResponse.getText().toString();
            this.parser = new MyJSONParser(data);
        }

        String msg = "";
        MyData data = this.parser.getValue();
        for (String i : data.msg) {
            msg = msg + " " + i;

            //interpretar cada mensaje
            if( msg.contains("ENCENDER") ){
                //Solicitud de encendido

                //responder apropiadamente al usuario cada mensaje
                MiPeticionREST obj = new MiPeticionREST(MainActivity.this, tvResponse);
                obj.execute("GET-SEND", "Sensor Encendido");
            }

            if( msg.contains("APAGAR") ) {
                //Solicitud de apagado

                //responder apropiadamente al usuario cada mensaje
                MiPeticionREST obj = new MiPeticionREST(MainActivity.this, tvResponse);
                obj.execute("GET-SEND", "Sensor Apagado");
            }
        }
    }

    public void onService(View v) {
        Log.e("ON-MainActivity", "onService()");
        try {
            Intent act = new Intent(this, SimpleActivity.class);
            //Intent act = new Intent(getBaseContext(), ConnectWith.class);
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
            if( resultCode == SimpleActivity.RESULT_OK) {
                Log.e("ON-MainActivity", "onActivityResult(): LAUNCH_SIMPLE_ACTIVITY");
            }


            Intent demon = new Intent(this, MyService.class);
            BluetoothDevice bt = data.getParcelableExtra(SimpleActivity.TAG_BLUETOOTH_DEVICE);

            demon.putExtra(SimpleActivity.TAG_BLUETOOTH_DEVICE, bt);
            ContextCompat.startForegroundService(this, demon);
            
            // Abrir la Central de Control inmediatamente
            Intent dashboardIntent = new Intent(this, DashboardActivity.class);
            startActivity(dashboardIntent);
        }
    }

    public void onGarmin(View v) {
        Intent intent = new Intent(this, DashboardActivity.class);
        startActivity(intent);
    }
}