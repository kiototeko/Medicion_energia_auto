package com.icat.bluetooth_motor;

import android.Manifest;
import android.app.usage.ExternalStorageStats;
import android.bluetooth.BluetoothSocket;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.PointF;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.androidplot.Plot;
import com.androidplot.Region;
import com.androidplot.ui.Anchor;
import com.androidplot.ui.HorizontalPositioning;
import com.androidplot.ui.SeriesBundle;
import com.androidplot.ui.Size;
import com.androidplot.ui.SizeMode;
import com.androidplot.ui.TextOrientation;
import com.androidplot.ui.VerticalPositioning;
import com.androidplot.ui.widget.TextLabelWidget;
import com.androidplot.util.PixelUtils;
import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.CatmullRomInterpolator;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.PanZoom;
import com.androidplot.xy.RectRegion;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.StepMode;
import com.androidplot.xy.XYCoords;
import com.androidplot.xy.XYGraphWidget;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYSeries;
import com.androidplot.xy.XYSeriesFormatter;

import org.w3c.dom.Text;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.FieldPosition;
import java.text.Format;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Observable;
import java.util.Observer;

public class Graficar extends AppCompatActivity {

    private XYPlot grafica;
    private static final int MESSAGE_READ = 3;
    private ActualizaGrafica actGraf;
    private Conectado data;
    static private Thread hilo;
    private TextLabelWidget acumulado;
    static private int BOUNDARY_STEP_Y = 10;
    static private double BASE_TIEMPO = 1000.0; //milisegundos
    final private int MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 1;
    static SampleDynamicSeries serie1;
    static LineAndPointFormatter serie1Formato;
    static private BufferedWriter writer = null;
    static private int opcion_grafica = 1;
    static String[] rangos = {"Energía (KJ)", "Voltaje (V)", "Intensidad de corriente (A)"};
    static boolean permiso = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graficar);


        getSupportActionBar().setTitle(R.string.main_title);


        // se checan los permisos para ver si se puede escribir en un archivo
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
        else {
            permiso = openWriteFile();
        }


        grafica = findViewById(R.id.plot);

        // el siguiente código tiene que ver con el formato de la gráfica

        acumulado = new TextLabelWidget(grafica.getLayoutManager(), "Energía",
                new Size(
                        PixelUtils.dpToPix(100), SizeMode.ABSOLUTE,
                        PixelUtils.dpToPix(100), SizeMode.ABSOLUTE),
                TextOrientation.HORIZONTAL);

        acumulado.getLabelPaint().setTextSize(PixelUtils.dpToPix(16));

        Paint p = new Paint();
        p.setARGB(100,0,0,0);
        acumulado.setBackgroundPaint(p);

        acumulado.position(0, HorizontalPositioning.RELATIVE_TO_CENTER,
                PixelUtils.dpToPix(45), VerticalPositioning.ABSOLUTE_FROM_TOP,
                Anchor.TOP_MIDDLE);
        acumulado.pack();


        final Handler mHandle = new HandleManager(acumulado);


        actGraf = new ActualizaGrafica(grafica);

        data = new Conectado(BtSocketHandler.getBtsocket(), mHandle, grafica);

        serie1 = new SampleDynamicSeries(data, 0, getResources().getString(R.string.medida));


        serie1Formato = new LineAndPointFormatter(Color.RED, null, Color.BLUE, null);

        serie1Formato.setInterpolationParams(
                new CatmullRomInterpolator.Params(10, CatmullRomInterpolator.Type.Centripetal));

        grafica.addSeries(serie1, serie1Formato);

        data.addObserver(actGraf);

        grafica.setDomainStepMode(StepMode.SUBDIVIDE);
        grafica.setDomainStepValue(7);
        grafica.setRangeStepMode(StepMode.SUBDIVIDE);
        grafica.setRangeStepValue(10);

        grafica.setRangeBoundaries(0, BOUNDARY_STEP_Y, BoundaryMode.FIXED);

        DashPathEffect dashFx = new DashPathEffect(
                new float[] {PixelUtils.dpToPix(3), PixelUtils.dpToPix(3)}, 0);
        grafica.getGraph().getDomainGridLinePaint().setPathEffect(dashFx);
        grafica.getGraph().getRangeGridLinePaint().setPathEffect(dashFx);

        grafica.getGraph().getLineLabelStyle(XYGraphWidget.Edge.BOTTOM).setFormat(new DecimalFormat("####.###"));

        PanZoom.attach(grafica, PanZoom.Pan.BOTH, PanZoom.Zoom.STRETCH_VERTICAL);

        hilo = new Thread(data);
        hilo.start();


    }


    /* función que escucha los resultados de la obtención de permisos */
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults){
        switch(requestCode){
            case MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE:
                if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    permiso = openWriteFile();
                }
                else {
                    Toast.makeText(getApplicationContext(), "No se podrán guardar los datos", Toast.LENGTH_LONG).show();
                }

        }
    }

    /* función para ver si el almacenaje externo se puede escribir */
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if(Environment.MEDIA_MOUNTED.equals(state)){
            return true;
        }
        return false;
    }


    /* función para abrir archivo de escritura */
    public boolean openWriteFile(){
        if(isExternalStorageWritable()){
            File directorio, archivo;
            directorio = getPublicAlbumStorageDir(getString(R.string.nombre_dir));
            DateFormat dateFormat = new SimpleDateFormat("ddMMyyyyHHmmss", Locale.US);
            Date date = new Date();
            archivo = new File(directorio, getString(R.string.nombre_archivo) + "_" + dateFormat.format(date) + ".csv");
            try {
                archivo.createNewFile();
                FileWriter fw = new FileWriter(archivo);
                writer = new BufferedWriter(fw);
                writer.write("Energía,Voltaje,Intensidad de corriente,Periodo,Tiempo\n");
            } catch (Exception e){
                Log.e("archivo", e.getMessage());
                return false;
            }
            return true;
        }
        return false;
    }


    // para obtener un directorio particular
    public File getPublicAlbumStorageDir(String albumName) {
        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), albumName);
        file.mkdir();
        return file;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu){
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_grafica, menu);
        return true;
    }

    // determina comportamiento de elementos en la barra de menú
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.reset:
                resetear();
                return true;
            case R.id.energia:
                opcion_grafica = 1;
                return true;
            case R.id.voltaje:
                opcion_grafica = 2;
                return true;
            case R.id.corriente:
                opcion_grafica = 3;
                return true;

        }

        return super.onOptionsItemSelected(item);
    }

    // si se aprieta el botón RESET, se modifican los ejes de la gráfica
    public void resetear(){
        int sz = serie1.size();
        double x = serie1.getX(sz - 1).doubleValue(), y = serie1.getY(sz - 1).doubleValue();
        Log.i("serie1", "size=" + Integer.toString(sz) + " x=" + Double.toString(x) + " y=" + Double.toString(y));

        grafica.centerOnRangeOrigin(y);

    }


    @Override
    public void onDestroy(){
        super.onDestroy();
        hilo.interrupt();
        BtSocketHandler.closeBtsocket();
    }

    @Override
    public void onStop() {
        super.onStop();
        try {
            writer.flush();
        } catch (Exception e){
            Log.e("flush", "no se pudo escribir en archivo");
        }

    }

    @Override
    public void onPause(){
        super.onPause();
    }

    @Override
    public void onStart() {
        super.onStart();
    }


    @Override
    public void onResume() {
        super.onResume();
    }

    // clase donde se actualiza la gráfica
    private static class Conectado implements Runnable {
        private final BufferedReader mmInStream;
        private Handler mHandler;
        private int TAM_MUESTRA = 0, LIMITE_TAM_MUESTRA = 1000;
        private MyObservable notifier;
        private List<String> energia, tiempo, voltaje, corriente, periodo, datos;
        private XYPlot grafica;


        public Conectado(BluetoothSocket socket, Handler mHandler, XYPlot grafica) {
            InputStream tmpIn = null;
            notifier = new MyObservable();
            this.grafica = grafica;

            try{
                tmpIn = socket.getInputStream();
            } catch (IOException e) {}

            // se crean las listas que almacenan los valores de las mediciones
            mmInStream = new BufferedReader (new InputStreamReader(tmpIn));
            this.mHandler = mHandler;
            energia = new ArrayList<String>();
            tiempo = new ArrayList<String>();
            voltaje = new ArrayList<String>();
            periodo = new ArrayList<String>();
            corriente = new ArrayList<String>();
            datos = new ArrayList<String>();

        }

        class MyObservable extends Observable{
            @Override
            public void notifyObservers(){
                setChanged();
                super.notifyObservers();
            }
        }

        // función que se ejecuta en otro hilo
        public void run(){

            String line;
            String str = "";
            int opcion_ant = 0;



            while(true) {
                if(hilo.isInterrupted()){
                    return;
                }
                try{
                    // checa si la trama es igual al formato establecido
                    if ((line = mmInStream.readLine()) != null
                            && line.matches("^[0-9]+\\.[0-9]{3}P[0-9]+\\.[0-9]{3}V[0-9]+\\.[0-9]{3}I[0-9]+T[0-9]+C")) {
                        energia.add(line.substring(0, line.indexOf('P')));

                        // agrega las mediciones de la muestra a las listas correspondientes
                        voltaje.add(line.substring(line.indexOf('P') + 1, line.indexOf('V')));
                        corriente.add(line.substring(line.indexOf('V') + 1, line.indexOf('I')));
                        periodo.add(line.substring(line.indexOf('I') + 1, line.indexOf('T')));
                        tiempo.add(Double.toString(Double.parseDouble(line.substring(line.indexOf('T') + 1,
                                line.indexOf('C'))) * Double.parseDouble(periodo.get(periodo.size() - 1)) / BASE_TIEMPO));


                        // se limita el número de muestras en la gráfica
                        if (TAM_MUESTRA < LIMITE_TAM_MUESTRA)
                            TAM_MUESTRA++;
                        else {
                            tiempo.remove(0);
                            energia.remove(0);
                            voltaje.remove(0);
                            corriente.remove(0);
                            periodo.remove(0);
                        }

                        // se escriben las mediciones en el archivo abierto
                        if (permiso)
                            writer.write(energia.get(energia.size() - 1) + "," + voltaje.get(voltaje.size() - 1) +
                                    "," + corriente.get(corriente.size() - 1) + "," + periodo.get(periodo.size() - 1) + "," +
                                    tiempo.get(tiempo.size() - 1) + "\n");

                        // dependiendo de lo que se haya elegido para graficar
                        switch (opcion_grafica){
                            case 1:
                                datos = energia;
                                str = "Energía = " + datos.get(datos.size() -1) + " KJ";
                                break;
                            case 2:
                                datos = voltaje;
                                str = "Voltaje = " + datos.get(datos.size() - 1) + " V";
                                break;
                            case 3:
                                datos = corriente;
                                str = "Corriente = " + datos.get(datos.size() -1) + " A";
                                break;
                        }

                        if(opcion_ant != opcion_grafica) {
                            if(opcion_ant != 0) {
                                grafica.clear();
                                TextLabelWidget txt = grafica.getRangeTitle();
                                txt.setText(rangos[opcion_grafica - 1]);
                                grafica.setRangeTitle(txt);
                                grafica.addSeries(serie1, serie1Formato);
                                grafica.redraw();
                            }
                            grafica.setRangeLowerBoundary(Double.parseDouble(datos.get(0))/2.0, BoundaryMode.FIXED);
                            grafica.setRangeUpperBoundary(Double.parseDouble(datos.get(datos.size()-1))*2.0, BoundaryMode.FIXED);
                            opcion_ant = opcion_grafica;
                        }

                        mHandler.obtainMessage(MESSAGE_READ, str.length(), -1, str).sendToTarget();


                        grafica.setDomainLowerBoundary(Double.parseDouble(tiempo.get(0)), BoundaryMode.FIXED);
                        if(tiempo.size() > 1)
                            grafica.setDomainUpperBoundary(Double.parseDouble(tiempo.get(tiempo.size()-1)), BoundaryMode.FIXED);

                        notifier.notifyObservers();

                    }

                } catch (IOException e) {
                    Log.e("run", "error en ejecución: " + e.getMessage());
                }

            }

        }


        public int getItemCount(int serie){
            return datos.size();
        }


        // getX y getY dan las muestras a la gráfica
        public Number getX(int serie, int indice){
            if(indice >= TAM_MUESTRA+1)
                throw new IllegalArgumentException();
            return Double.parseDouble(tiempo.get(indice));
        }

        public Number getY(int serie, int indice){
            if(indice >= TAM_MUESTRA+1)
                throw new IllegalArgumentException();
            return Double.parseDouble(datos.get(indice));
        }

        public void addObserver(Observer observer){
            notifier.addObserver(observer);
        }

    }

    private static class HandleManager extends Handler{
        TextLabelWidget sel;

        HandleManager(TextLabelWidget sel){
            this.sel = sel;
        }

        @Override
        public void handleMessage(Message msg){
            sel.setText((String) msg.obj);
        }

    }


    private class ActualizaGrafica implements Observer {
        Plot grafica;

        public ActualizaGrafica(Plot grafica) {
            this.grafica = grafica;
        }

        @Override
        public void update(Observable o, Object arg){
            grafica.redraw();
        }
    }


    class SampleDynamicSeries implements XYSeries {
        private Conectado fuente;
        private int indiceSerie;
        private String titulo;

        public SampleDynamicSeries(Conectado fuente, int indiceSerie, String titulo){
            this.fuente = fuente;
            this.indiceSerie = indiceSerie;
            this.titulo = titulo;
        }

        @Override
        public String getTitle(){
            return titulo;
        }

        @Override
        public int size(){
            return fuente.getItemCount(indiceSerie);
        }

        @Override
        public Number getX(int indice){
            return fuente.getX(indiceSerie, indice);
        }

        @Override
        public Number getY(int indice) {
            return fuente.getY(indiceSerie, indice);
        }
    }
}
