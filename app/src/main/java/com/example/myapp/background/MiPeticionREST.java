package com.example.myapp.background;

import android.os.AsyncTask;
import android.util.Log;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class MiPeticionREST extends AsyncTask<String,String,String> {
    private TextView output;
    private android.content.Context context;

    HttpURLConnection urlConnection;
    StringBuilder json;
    String resultado = "";
    int offset;

    public MiPeticionREST(android.content.Context context, TextView output){
        offset = 0;
        this.context = context;
        this.output = output;
    }
    public MiPeticionREST(android.content.Context context){
        offset = 0;
        this.context = context;
        this.output = null;
    }
    public MiPeticionREST(TextView output){
        offset = 0;
        this.context = null;
        this.output = output;
    }
    public MiPeticionREST(){
        offset = 0;
        this.context = null;
        this.output = null;
    }

    @Override
    public void onPreExecute(){
    }

    @Override
    protected String doInBackground(String... info) {
        String res = "";

        try
        {

            if( info[0].contains("POST")) {
                String baseUrl = "https://5e64-200-68-165-1.ngrok-free.app";
                if (context != null) {
                    baseUrl = com.example.myapp.data.ConfigUtils.getHttpUrl(context);
                }
                URL url = new URL(baseUrl + "/users2");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setDoOutput(true);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");

                conn.setRequestProperty("ngrok-skip-browser-warning", "true");

                String input = "{\"id\": \"0\",\"dato\": \"" + "\"1\"" + "\",\"segundo\": \"" + "\"2\"" + "}";
                OutputStream os = conn.getOutputStream();
                os.write(input.getBytes());
                os.flush();

                if (conn.getResponseCode() != HttpURLConnection.HTTP_CREATED) {
                    res = "error";
                }

                conn.disconnect();
            }

            if( info[0].contains("GET-SEND")){
                //Tiene como objetivo hacer que el BOT envíe un mensaje al chat con ID definido
                URL url = new URL("https://api.21telegram.org/bot8710550268:AAEH-ONfsp14Adw8_D6BNNv1jHXu0qLnx8Y/sendMessage?chat_id=5174630596&text=" + info[1]);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setDoOutput(true);
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Content-length", "0");
                conn.setUseCaches(false);
                conn.setAllowUserInteraction(false);
                conn.setConnectTimeout(1000);
                conn.setReadTimeout(1000);
                conn.connect();

                int status = conn.getResponseCode();

                if ( status == 200 ) {
                    res = "Message Send as  BOT";
                }

                conn.disconnect();
            }

            if( info[0].contains("GET-UPDATES")){
                URL url = new URL("https://api.telegram.org/bot8710550268:AAEH-ONfsp14Adw8_D6BNNv1jHXu0qLnx8Y/getUpdates?offset=" + offset + "timeout=5");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setDoOutput(true);
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Content-length", "0");
                conn.setUseCaches(false);
                conn.setAllowUserInteraction(false);
                conn.setConnectTimeout(1000);
                conn.setReadTimeout(1000);
                conn.connect();

                int status = conn.getResponseCode();

                if ( status == 200 ) {
                    //InputStreamReader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8);
                    InputStreamReader reader = new InputStreamReader(conn.getInputStream());
                    BufferedReader br = new BufferedReader(reader);

                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line + "\n");
                    }
                    br.close();
                    res = sb.toString();
                }

                conn.disconnect();
            }
        }
        catch (MalformedURLException e) {
            Log.e("ENVIOREST", "[MalformedURLException]=>" + e.getMessage());
            e.printStackTrace();

        } catch (IOException e) {
            Log.e("ENVIOREST", "[IOException]=>" + e.getMessage());
            e.printStackTrace();
        }

        return res;
    }

    @Override
    protected void onProgressUpdate(String... progress){

    }

    @Override
    protected void onPostExecute(String result) {
        this.resultado = result;
        if( this.output != null ) {
            this.output.setText("[" + result + "]");
        }
    }
}

