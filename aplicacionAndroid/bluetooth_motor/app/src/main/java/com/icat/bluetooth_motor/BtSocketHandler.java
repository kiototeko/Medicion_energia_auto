package com.icat.bluetooth_motor;

import android.bluetooth.BluetoothSocket;
import android.util.Log;


// clase que sirve para pasar el socket de la actividad principal a la que grafica
public class BtSocketHandler {

    private static BluetoothSocket btsocket;

    public static synchronized BluetoothSocket getBtsocket(){
        return btsocket;
    }

    public static synchronized void setBtsocket(BluetoothSocket btsocket){
        BtSocketHandler.btsocket = btsocket;
    }

    public static synchronized void closeBtsocket(){
        try{
            btsocket.close();
        }catch (Exception e) {
            Log.e("BtSocketHandler", e.getMessage());
        }

    }
}
