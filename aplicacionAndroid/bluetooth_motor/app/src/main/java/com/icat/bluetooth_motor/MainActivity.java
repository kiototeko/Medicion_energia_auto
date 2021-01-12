package com.icat.bluetooth_motor;

import android.Manifest;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_ENABLE_BT = 1, REQUEST_COARSE_LOC = 2;
    BluetoothAdapter mBluetoothAdapter;
    Spinner dispositivos;
    final static UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    List<String> spinnerArray =  new ArrayList<String>();
    List<BluetoothDevice> bld = new ArrayList<BluetoothDevice>();
    ArrayAdapter<String> adapter;
    private ProgressBar progressBar;



    /* esta función es un handler que escucha todo tipo de eventos */
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(BluetoothDevice.ACTION_FOUND.equals(action)){
                /* a nosotros solo nos interesa los eventos relacionados con el Bluetooth, específicamente cuando encuentra
                otros dispositivos
                 */
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                adapter.add(device.getName() + " " + device.getAddress());
                /* por cada dispositivo encontrado, agrega su información a adapter y a bld */
                adapter.notifyDataSetChanged();
                bld.add(device);
            }
            /* otros eventos de Bluetooth sirven para mostrar la barra de progreso */
            else if(BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                progressBar.setVisibility(View.VISIBLE);
            }
            else if(BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)){
                progressBar.setVisibility(View.INVISIBLE);
            }

        }
    };

    // onCreate se ejecuta al inicio de la actividad
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // se establece la interfaz gráfica de la actividad

        setContentView(R.layout.activity_main);

        adapter = new ArrayAdapter<String>(
                this, R.layout.spinner_layout, spinnerArray);


        getSupportActionBar().setTitle(R.string.main_title);

        progressBar = findViewById(R.id.progreso);

        // obtiene una variable que representa el adaptador Bluetooth
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.no_blue, Toast.LENGTH_LONG).show();
            return;
        }

        // si no está habilitado pide permiso
        if(!mBluetoothAdapter.isEnabled()){
            Intent BtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(BtIntent, REQUEST_ENABLE_BT);
        }
        else
            consulta_bluetooth();

        // obtiene otros permisos necesarios
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED){

            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQUEST_COARSE_LOC);

        }

        // dispositivos es la lista con los dispositivos Bluetooth
        dispositivos = findViewById(R.id.dispos);

        // se generan los botones
        Button bt_escaneo = findViewById(R.id.bt_escaneo);
        Button bt_conectar = findViewById(R.id.bt_conectar);

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        dispositivos.setAdapter(adapter); // se asocia adapter a la lista de dispositivos

        // se establece la funcionalidad de los botones


        /* en el caso del que dice "ESCANEAR", se limpian las listas con la información de los dispositivos
         y se inicia un proceso de descubrimiento
         */
        bt_escaneo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mBluetoothAdapter.cancelDiscovery();
                adapter.clear();
                bld.clear();
                mBluetoothAdapter.startDiscovery();
            }
        });

        /* para el botón de "CONECTAR" se elige el dispositivo que está al frente de la lista dispositivos
        y se conecta. Posteriormente se pasa a la actividad de Graficar
         */
        bt_conectar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(adapter.isEmpty())
                    return;
                int pos = dispositivos.getSelectedItemPosition();
                Conectar cnc = new Conectar(bld.get(pos));
                cnc.execute();
            }
        });


        // se establecen los eventos que se quieren filtrar
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(mReceiver, filter);


    }


    @Override
    protected void onDestroy (){
        super.onDestroy();
        unregisterReceiver(mReceiver);
    }


    /* función que escucha los resultados de la obtención de permisos */
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults){

        switch(requestCode){
            case REQUEST_COARSE_LOC: {
                if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED);
                else
                    Toast.makeText(this, R.string.funcionalidad, Toast.LENGTH_LONG).show();
            }
        }
    }


    /* parecida a la anterior pero para los permisos de bluetooth*/
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(requestCode == REQUEST_ENABLE_BT){
            if(resultCode == RESULT_OK){
                consulta_bluetooth();
            }
        }
    }

    /* esta función sirve para obtener los datos de los dispositivos bluetooth previamente acoplados */
    void consulta_bluetooth(){
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if(pairedDevices.size() > 0){
            for(BluetoothDevice device : pairedDevices){
                adapter.add(device.getName() + " " + device.getAddress());
                bld.add(device);
            }
            adapter.notifyDataSetChanged();
        }
    }

    /*
     en esta clase se conecta el smartphone con el dispositivo bluetooth
     */
    private class Conectar extends AsyncTask<Void, Void, Integer>{
        private final BluetoothSocket btSocket;
        private final BluetoothDevice btDevice;

        public Conectar(BluetoothDevice device){
            BluetoothSocket tmp = null;
            btDevice = device;

            try {
                tmp = device.createRfcommSocketToServiceRecord(uuid); // se crea un socket
            } catch(IOException e){}
            btSocket = tmp;
        }

        /* esta rutina se ejecuta en el fondo y es donde intenta hacer la conexión*/
        public Integer doInBackground(Void ... v){
            mBluetoothAdapter.cancelDiscovery();

            try{
                publishProgress();
                btSocket.connect();
            }catch (IOException e){
                try{
                    btSocket.close();
                } catch(IOException b){}
                return 1;
            }
            return 0;
        }

        /* al acabar la rutina en el fondo se ejecuta esta donde se prepara todo para ejecutar actividad
        de graficar
         */
        public void onPostExecute(Integer result){
            progressBar.setVisibility(View.INVISIBLE);

            if(result == 0) {
                Intent intent = new Intent(MainActivity.this, Graficar.class);
                BtSocketHandler.setBtsocket(btSocket); // se pasa el socket para comunicarse con el dispositivo como argumento
                startActivity(intent);

            }
            else
                Toast.makeText(MainActivity.this, R.string.no_conecta, Toast.LENGTH_LONG).show();
        }


        public void onProgressUpdate(Void ... v){
            progressBar.setVisibility(View.VISIBLE);
        }
    }


    @Override
    public void onStart() {
        super.onStart();
        Log.i("hola2", "onstart");
    }


    @Override
    public void onResume() {
        super.onResume();
        Log.i("hola3", "onresume");
    }

}