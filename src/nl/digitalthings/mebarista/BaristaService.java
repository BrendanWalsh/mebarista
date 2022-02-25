package nl.digitalthings.mebarista;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.SystemClock;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.util.Log;

import org.achartengine.chart.PointStyle;
import org.achartengine.model.TimeSeries;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;

public class BaristaService extends IntentService implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "BaristaService";

    static final int UPDATE = 1;

    private NotificationManager mNM = null;
    public MainActivity ma;
    public XYMultipleSeriesDataset gdataset;
    public XYMultipleSeriesRenderer renderer;
    private XYSeriesRenderer r_second_channel;

    public int teller, teller_input_lines;
    public XYSeries[] series = { null, null, null };
    BTHandler bt2 = null;
    boolean pause_updates = false, pause_prefs = false;
    boolean did_firmware = false;
    public StringWriter log;

    public boolean legacy = false;

    // BLE
    BLEHandler blehandler = null;
    byte[] fw_final;
    byte[] fw_final2;

    boolean firmware_start_stk500 = false;

    SharedPreferences settings;

    public BaristaService() {
        super( "BaristaService" );

        teller = 0;
        teller_input_lines = 0;

        log = new StringWriter();

        configure_graph();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // handle the preference change here
        if( pause_prefs )
            return ;

        Log.i( TAG, "Shared preference: " + key + "changed" );

        showNotification(key, "changed");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        super.onStartCommand(intent, flags, startId);

        return START_STICKY;
    }

    @Override
    public void onDestroy() {

        if( bt2 != null ) {
            // bt2.stop_loop();
            bt2.close_object();
            bt2.close();
            bt2 = null;
        }

        if( blehandler != null ) {

            // blehandler.mBluetoothGatt.disconnect();

            blehandler.close();

            blehandler = null;


        }

        super.onDestroy();
    }

    public class LocalBinder extends Binder {
        BaristaService getService() {
            return BaristaService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    // This is the object that receives interactions from clients.  See
    // RemoteService for a more complete example.
    private final IBinder mBinder = new LocalBinder();


    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UPDATE:

                    break;

                default:
                    super.handleMessage(msg);
            }
        }
    }

    public void scan ( ) {

        if (mHandler != null)
            mHandler.obtainMessage(MainActivity.SPINNER_ON).sendToTarget();

        if (bt2 == null && settings.getBoolean("pref_bt_bt2_enabled", true))
            bt2 = new BTHandler(this);

        if (bt2 != null && !settings.getBoolean("pref_bt_bt2_enabled", true)) {
            bt2.close();

            bt2.close_object();

            bt2 = null;
        }

        if (bt2 != null)
            bt2.discover();

        // TODO: if this is the check, all other ble sart code needs to go through here
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            return ;

        }
        if (blehandler == null && settings.getBoolean("pref_bt_ble_enabled", false)) {
            BluetoothManager mBluetoothManager =
                    (BluetoothManager) ma.getSystemService(Context.BLUETOOTH_SERVICE);

            blehandler = new BLEHandler( this, mBluetoothManager );

            //blehandler.setup(); <- wordt al door discover gedaan.

        }

        if (blehandler != null && !settings.getBoolean("pref_bt_ble_enabled", false)) {
            blehandler.close();

            blehandler = null;
        }

        if( blehandler != null )
            blehandler.discover( );

    }

    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    final Messenger mMessenger = new Messenger(new IncomingHandler());

    @Override
    protected void onHandleIntent(Intent intent) {

        // /* SharedPreferences */ settings;

        if( ma != null && settings == null )
            settings = PreferenceManager.getDefaultSharedPreferences(ma);

        String action = "";

        if( mNM == null )
            mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

        if( intent != null )
            action = intent.getStringExtra("action");

        if( action == null )
            action = "";

        if( action.equals( "send" ) ) {
            write( intent.getStringExtra( "payload" ) );
        }


        if( action.equals("scan") ) {

            //
            this.scan( );

            /*
            if (mHandler != null)
                mHandler.obtainMessage(MainActivity.SPINNER_ON).sendToTarget();

            if (bt2 == null && settings.getBoolean("pref_bt_bt2_enabled", true))
                bt2 = new BTHandler(this);

            if (bt2 != null && !settings.getBoolean("pref_bt_bt2_enabled", true)) {
                bt2.close();

                bt2 = null;
            }

            if (bt2 != null)
                bt2.discover();

            if (blehandler == null && settings.getBoolean("pref_bt_ble_enabled", false)) {
                BluetoothManager mBluetoothManager =
                        (BluetoothManager) ma.getSystemService(Context.BLUETOOTH_SERVICE);

                blehandler = new BLEHandler( this, mBluetoothManager );
            }

            if (blehandler != null && !settings.getBoolean("pref_bt_ble_enabled", false)) {
                blehandler.close();

                blehandler = null;
            }

            if( blehandler != null )
                blehandler.discover( );
                */

            // mHandler.obtainMessage(MainActivity.SPINNER_OFF ).sendToTarget();

            //

            // disable second channel depending on setting

            // TODO: get setting
            // /* SharedPreferences */settings = PreferenceManager.getDefaultSharedPreferences(ma);
            if ( !settings.getBoolean( "pref_ui_second_sensor", false ) && gdataset.getSeriesCount() > 2 ) {

                gdataset.removeSeries(2);
                renderer.removeSeriesRenderer(r_second_channel);

            }

            //    gdataset.addSeries(series[2]);
            //    renderer.addSeriesRenderer(r_second_channel);
            // }

            // teller_input_lines = 0;
            // bt2.read();
            // bt2.close();
        }

        /* Deze actie komt uit de preferences Hardware->Firmware */
        if( action.equals("firmware") ) {
            showNotification("Firmware", "Firmware @ 2%");

            do_firmware_2( intent.getStringExtra( "firmware_file" ) );

            return ;
        }

        if( action.equals("demo")) {

            // enable second schannel

            //gdataset.removeSeries( 2 );
            //renderer.removeSeriesRenderer( r_second_channel );

            // Does not work. yet.

            //if( gdataset.getSeriesCount() == 2 ) {
            //    gdataset.addSeries(series[2]);
            //    renderer.addSeriesRenderer(r_second_channel);
            // }

            fillChartWithDemoData();

            return ;
        }

        if( action.equals( "fw_reset" ) ) {

            try {
                connected_os.write( "\ncmd reset en\n".getBytes() );
            } catch (IOException e) {
                e.printStackTrace();
            }

            return ;
        }

        if( action.equals( "fw_stop" ) ) {

            stop = true;
            reader_thread.interrupt();
            return ;
        }

        if( action.equals( "fw_start" ) ) {

            reader_thread_start();
            return ;
        }

        if( action.equals( "fw_close" ) ) {

            // reader_thread_start();

            blehandler.close();

            blehandler = null;

            return ;
        }

        if( action.equals( "fw_open" ) ) {

            // blehandler . open() ?

            BluetoothManager mBluetoothManager =
                    (BluetoothManager) ma.getSystemService(Context.BLUETOOTH_SERVICE);

            blehandler = new BLEHandler( this, mBluetoothManager );

            blehandler.setup();

            return ;
        }

        if( action.equals( "fw_flash" ) ) {

            //

            Thread fw_flash = new Thread() {
                public void run() {

                    // do_firmware();
                    load_firmware( "meCoffee-V9" );

                    STK500 p0 = new STK500( connected_is, connected_os, new Logx(ma, ma.getBaseContext()) );

                    try {
                        p0.main( fw_final2 );
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
            };

            fw_flash.start();

            return ;
        }


    }

    public void spinner( boolean on ) {
        mHandler.obtainMessage( on ? MainActivity.SPINNER_ON : MainActivity.SPINNER_OFF, "" ).sendToTarget();
    }

    public boolean is_meCoffee( String devicename ) {

        if( devicename == null )
            return false;

        return devicename.startsWith( "me" ) || devicename.equals( "MissSilvia" );

    }

    public boolean stop = false;

    public void read( InputStream is ) {

        // Use the java.util scanner to break the input into lines
        boolean usescanner = false;

        if( usescanner ) {
            Scanner scanner = new Scanner(is).useDelimiter("\n");

            // Use infinite loop to read the inStream
            int errors = 0;
            while(!stop) {

                String line = "";


                try { line = scanner.next(); }
                catch(NoSuchElementException e) {

                    // Occurs regurarly, if the stream has dried up for example
                    // bad connection, large distances etc

                    // Goto sleep
                    // try
                    { SystemClock.sleep(500); } // catch (InterruptedException e1) { }

                    errors++;

                    if(errors > 5)
                        break;
                    else
                        // And try again
                        continue;
                }
                catch(Exception e) {
                    // Something unforeseen has gone wrong:
                    // Break out of this connection
                    break;
                }
                errors = 0;

                try {
                    updateDataset( line, 0 );
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }

        if( !usescanner ) {

            byte[] buffer = new byte[16];
            int bytes;
            StringBuilder readMessage = new StringBuilder();
            while ( !stop && !reader_thread.isInterrupted() ) {

                if( is == null ) // TODO: moet helemaal niet kunnen
                    break;

                try {
                    bytes = is.read( buffer, 0, 16 );
                    String readed = new String(buffer, 0, bytes);
                    readMessage.append(readed);

                    if (readed.contains("\n")) {

                        try {

                            // Get message and remove from queue before parsing
                            // otherwise we keep looping over the same message
                            // when an exception occurs

                            // int size = readMessage.indexOf("\r\n");
                            int size = readMessage.indexOf("\n");

                            String line = null;

                            if( size > 0 )
                                line = readMessage.toString().substring( 0, size - 1 ); // -1 for the /r ? ( was 'size' )

                            readMessage.delete(0, size + 1 );

                            if( line != null )
                                updateDataset( line, 0);

                        }
                        catch (RemoteException e) {
                            e.printStackTrace();
                        }
                        catch (NumberFormatException e) {
                            e.printStackTrace();
                        }
                        catch (StringIndexOutOfBoundsException e) {
                            int size = readMessage.indexOf("\r\n");

                            e.printStackTrace();
                        }

                        //readMessage.setLength(0);
                    }

                } catch (IOException e) {
                    // connectionLost();
                    Log.e( TAG, "Connection lost" );
                    break;
                }
            }

        }

        stop = false;
        connected_is = null;
        connected_os = null;

    }

    Thread reader_thread;
    InputStream connected_is;
    OutputStream connected_os;

    private void reader_thread_start() {

        reader_thread = new Thread() {
            public void run() {

                Log.i( TAG, "Read thread started" );
                stop = false;

                if( connected_is != null )
                    read( connected_is );
                else
                    Log.i(TAG, "Read not started : is null" );

                Log.i(TAG, "Read thread ended");

                stop = false;

                reader_thread = null;

            }
        };

        //fw_flash.run();
        reader_thread.start();

    }

    public void connected( final InputStream is, final OutputStream os ) {

        // TODO: ergens hier de firmware_flash_start inbouwen

        if( is == null ) {

            if( reader_thread != null ) {

                //reader_thread.join();
                // stop = true;
                reader_thread.interrupt();

                // reader_thread.interrupt();

            }

            return ;

        }

        if( firmware_start_stk500 ) {

            firmware_start_stk500 = false;

            final Logx logx = new Logx( ma, ma.getBaseContext() );

            // Firmware flash situation
            Log.i( TAG, "Starting firmware" );

            try {
                Thread.sleep( 750 );
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            Thread fw_flash = new Thread() {
                public void run() {

                    STK500 p0 = new STK500(is, os, logx);
                    try {
                        p0.main( fw_final2 );

                        if( bt2 != null ) {
                            bt2.close();

                            bt2.discover();
                        }

                        if( blehandler != null ) {
                            blehandler.close();
                        }

                    } catch (IOException e) {
                        e.printStackTrace();

                        if( bt2 != null ) {
                            bt2.close();

                            bt2.discover();
                        }

                        if( blehandler != null ) {
                            blehandler.close();
                        }

                    }
                }
            };

            fw_flash.start();

            return;

        }

        if( is != null && connected_is == is && ( reader_thread != null && !reader_thread.isInterrupted() ) ) {

            Log.i( TAG, "Connected got same descriptor" );

            return ; // Same description, nothing to do

        }

        connected_os = os;
        connected_is = is;

        if( reader_thread != null ) {

            Log.i(TAG, "Killing reader_thread for new is & os");

            reader_thread.interrupt();
            if( reader_thread != null )
            try {
                reader_thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            Log.i(TAG, "Killed reader_thread for new is & os");

            reader_thread = null;
        }

        teller_input_lines = 0; // TODO: willen we dit wel ivm sharing en zo?

        if( is != null && os != null )
            reader_thread_start();

    }

    public void showSecondSensor( boolean show ) {

        if( !show && gdataset.getSeriesCount() > 2 ) {

            gdataset.removeSeries(2);
            renderer.removeSeriesRenderer(r_second_channel);

            return ;
        }

        if( show && gdataset.getSeriesCount() < 3 ) {

            gdataset.addSeries(series[2]);
            renderer.addSeriesRenderer(r_second_channel);

            return ;
        }

    }

    /**
     * Show a notification while this service is running.
     */
    private void showNotification(CharSequence text, CharSequence text2) {

        // Set the icon, scrolling text and timestamp
        Notification notification = new Notification(R.drawable.icon3, text,
                System.currentTimeMillis());

        // The PendingIntent to launch our activity if the user selects this notification
        //PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
        //        new Intent(this, BaristaServiceActivities.Controller.class), 0);

        // Set the info for the views that show in the notification panel.
      //notification.setLatestEventInfo(this, text2, text, null);

        // Send the notification.
        //mNM.notify( 42 , notification);
    }

    public void patchup_line_out( ) {

        // suppose: cmd set pd1 3
        // new: means 0,03
        // old: means 3

        // of: 2 preferences.xml ...

    }

    public void patchup_line_in( ) {

    }

    public void updateDataset( String msg, int index ) throws RemoteException, NumberFormatException, ArrayIndexOutOfBoundsException {

        msg = msg.replace("\r", "");

        log.write( msg ); log.write("\n");

        if( !pause_updates )
            if( !msg.startsWith( "tmp ") || ( msg.startsWith( "tmp ") && teller % 10 == 0 ) )
                Log.i(TAG, "-" + msg + "-");

        if( msg.endsWith("NOT OK") )
            return;

        // kopjesteller sjit
//        if( !msg.endsWith(" OK") )
//            return;

        if( teller_input_lines == 4 ) {
            // do the initial querying

            write( "\ncmd get grndr_cnt\n" ); // TODO: remove: legacy
        }

        if( teller_input_lines++ == 3 ) {
            // Clock initialization: seconds since midnight
            Calendar c = Calendar.getInstance();
            c.set( Calendar.DST_OFFSET, 0);

            long now = c.getTimeInMillis();

            /* c.set(Calendar.HOUR_OF_DAY, 0);
            c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            c.set(Calendar.DST_OFFSET, 0);
            long passed = now - c.getTimeInMillis();
            long secondsPassed = passed / 1000; */

            long secondsPassed = c.get( Calendar.HOUR_OF_DAY ) * 3600 + c.get( Calendar.MINUTE ) * 60 + c.get( Calendar.SECOND );

            write( "\ncmd clock set " + secondsPassed + "\n" );

            write( "\ncmd uname OK\n");

            // do the initial querying
            write( "\ncmd dump\n" );
        }

        if( msg.startsWith( "dbg Finished" ) ) {

            teller_input_lines = 0;

            return;
        }

        if( msg.startsWith("pid ") || msg.startsWith( "sht ") ) {

            mHandler.obtainMessage(MainActivity.MESSAGE_READ, msg.length(), -1, msg ).sendToTarget();

            return ;
        }

        if( !( msg.startsWith("cmd ") || msg.startsWith("T ") || msg.startsWith( "tmp " ) || msg.startsWith("get ") ) )
            return ;

        if( msg.startsWith("cmd uname ") ) {
            String parts[] = msg.split(" ");

            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(ma);

            SharedPreferences.Editor editor = settings.edit();

            Map<String,?> keys = settings.getAll();

                for( Map.Entry<String,?> entry : keys.entrySet( ) ) {

                    if( entry.getKey().equals("pref_uname") ) {
                        editor.putString( "pref_uname", msg.replace( "cmd uname ", "" ).replace( " OK", "" ) );
                    }
                }
            editor.commit();

            this.legacy = msg.contains( "V4" );

        }

        if( msg.startsWith("cmd set ") || msg.startsWith("cmd get ") || msg.startsWith("get ") ) {

            // kopjes teller sjit
            if( msg.startsWith("get ") )
                msg = "cmd " + msg;

            String parts[] = msg.split(" ");

            // TODO: dit rukkiet rukkiet natuurlijk enorm
            //SharedPreferences preference = getSharedPreferences( "nl.digitalthings.mebarista.statistics", 0);
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(ma);

            SharedPreferences.Editor editor = settings.edit();

            if( parts.length < 4)
                return ;

            if( ! settings.contains( "pref_" + parts[2] ) )
                Log.i( TAG, " KEY NOT AVAILABLE " + "pref_" + parts[2]);

            if( parts[3] != "" )
                Log.i( TAG, "Setting " + parts[2] + " to " + (int)Float.parseFloat(parts[3]));

            //pause_prefs = true;
            //PreferenceManager.getDefaultSharedPreferences(ma).unregisterOnSharedPreferenceChangeListener(this);

            if( true ) {
            Map<String,?> keys = settings.getAll();

                boolean key_found = false;
            for( Map.Entry<String,?> entry : keys.entrySet( ) ) {

                if( entry.getKey().equals("pref_" + parts[2]) ) {
                    String entry_type = entry.getValue().getClass().getName();

                    // Do something
                    if( entry_type.equals("java.lang.Integer") ) {
                        editor.putInt( "pref_" + parts[2], Integer.parseInt( parts[3].replace(".00", "") ) );
                        Log.i(TAG, entry.getKey() + " set as integer");
                    }

                    if( entry_type.equals("java.lang.Float") ) {
                        editor.putFloat("pref_" + parts[2], Integer.parseInt(parts[3].replace(".00", "")));
                        Log.i(TAG, entry.getKey() + " set as float");
                    }

                    if( entry_type.equals("java.lang.Boolean") ) {
                        editor.putBoolean( "pref_" + parts[2], Integer.parseInt( parts[3] ) != 0 );
                        Log.i(TAG, entry.getKey() + " set as boolean" );
                    }

                    if( entry_type.equals("java.lang.String") ) {
                        editor.putString( "pref_" + parts[2],  parts[3] );
                        Log.i(TAG, entry.getKey() + " set as string");
                    }

                    key_found = true;

                    break;
                }

            }
                if( !key_found )
                    Log.e( TAG, "Key not found: " + parts[2] );

            }
            //editor.putInt("pref_" + parts[2], Integer.parseInt( parts[3].replace( ".00", "" ) ) );

            editor.commit();
            //PreferenceManager.getDefaultSharedPreferences(ma).registerOnSharedPreferenceChangeListener(this);
            //pause_prefs = false;

            return ;
        }



        if( !( msg.startsWith("T ") || msg.startsWith( "tmp " ) ) )
            return ;

        for(XYSeries serie : series) {
            int csize = 5000;
            if( serie.getItemCount() >= csize )
                serie.remove(0);
        }

        String[] parts = msg.replaceAll("T ","").replaceAll("tmp ", "").replaceAll("\n", "").split(" ");

        //msg.replaceAll( "tmp ", "" );

        if( parts.length < 3 )
            return ;

        double val;

        // Setpoint
        val =  Double.parseDouble(parts[1]);
        if( index == 0 && val > 0.0 )
            series[0].add( teller, val / 100.0 );

        // Boiler
        val = Double.parseDouble(parts[2]);
        if( val > 0.0 )
            series[index + 1].add( teller, val / 100.0 );

        // Second sensor
        val = Double.parseDouble(parts[3]);
        if( val > 5.0 && val < 20000)
            series[index + 2].add( teller, val / 100.0 );

        // Annotations
        String[] parts_annotation = msg.replaceAll("T ","").replaceAll("\n", "").split("\t");
        if( parts_annotation.length > 1 ) {
            double temp = Double.parseDouble(parts[2]);

            double offset = temp > 101.0 ? 7.0 : -5.0;

            series[0].addAnnotation(parts_annotation[1].replace("-", "\n").replace(" OK",""), teller, Double.parseDouble(parts[2]) / 100.0 + offset );
        }

        if( index == 0 && teller % 60 == 0 ) {
            renderer.addXTextLabel( teller, ( teller / 60 ) + ":00");
        }

        teller++;

        if( !pause_updates )
            mHandler.obtainMessage(MainActivity.MESSAGE_READ, msg.length(), -1, msg ).sendToTarget();

//        ma.update();
    }

    public void fillChartWithDemoData_File(String filename, int index) {
        AssetManager ast_mgr = getApplicationContext().getAssets();
        InputStream is_demo = null;

        //if(gdataset.getSeries()[0].getMaxX() > 0)
        //    return ;

        try {
            is_demo = ast_mgr.open(filename);
        }
        catch ( IOException e ) {
            showNotification("Not found", "Demo data");
            return ;
        }

        Scanner scanner = new Scanner(is_demo).useDelimiter("\n");
        String line = "";

        // pause the updates to the UI: would be slow
        // pause_updates = true;

        int linecnt = 0;
        while( linecnt++ < 2500 ) {
            try { line = scanner.next(); }
            catch(NoSuchElementException e) { break; }

            try {
                updateDataset( line.replace(".00", ""), index );
                if( line.startsWith( "T ") || line.startsWith( "tmp " ) ) {
                    Thread.sleep( 250);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // pause_updates = false;

        // repeat last line to trigger auto-scaling in UI
        try {
            updateDataset(line, index);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }



    Thread filldemo = new Thread()  {

        public void run() {
            String msg;

            msg = "dem 250 OK";
            mHandler.obtainMessage(MainActivity.MESSAGE_READ, msg.length(), -1, msg ).sendToTarget();

        fillChartWithDemoData_File( "56273d393cab0" /* "mecoffee-20150604-2021.txt" */, 0 );

            msg = "dem 1000 OK";
            mHandler.obtainMessage(MainActivity.MESSAGE_READ, msg.length(), -1, msg ).sendToTarget();
        }
    };

    class DemoThread implements Runnable {
        String m_filename;
        boolean running;

        public DemoThread( String filename ) {
            m_filename = filename;
        }

        public void stop_mecoffee() {
            running = false;
        }

        public void run() {

            running = true;

            String msg;

            msg = "dem 250 OK";
            mHandler.obtainMessage(MainActivity.MESSAGE_READ, msg.length(), -1, msg ).sendToTarget();

            fillChartWithDemoData_File( m_filename /* "mecoffee-20150604-2021.txt" */, 0);

            msg = "dem 1000 OK";
            mHandler.obtainMessage(MainActivity.MESSAGE_READ, msg.length(), -1, msg ).sendToTarget();
        }

        public void fillChartWithDemoData_File(String filename, int index) {
            AssetManager ast_mgr = getApplicationContext().getAssets();
            InputStream is_demo = null;

            //if(gdataset.getSeries()[0].getMaxX() > 0)
            //    return ;

            try {
                is_demo = ast_mgr.open(filename);
            }
            catch ( IOException e ) {
                showNotification("Not found", "Demo data");
                return ;
            }

            Scanner scanner = new Scanner(is_demo).useDelimiter("\n");
            String line = "";

            // pause the updates to the UI: would be slow
            // pause_updates = true;

            int linecnt = 0;
            while( /* linecnt++ < 2500 && */ running ) {
                try { line = scanner.next(); }
                catch(NoSuchElementException e) { break; }

                try {
                    updateDataset( line.replace(".00", ""), index );
                    if( line.startsWith( "T ") || line.startsWith( "tmp " ) ) {
                        Thread.sleep( 100 );
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            // pause_updates = false;

            // repeat last line to trigger auto-scaling in UI
            try {
                updateDataset(line, index);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

    }

    private Thread demo_thread;
    private DemoThread demo_runnable;

    public void fillChartWithDemoData() {
        series[0].setTitle("Setpoint");
        series[1].setTitle("meCoffee");
        series[2].setTitle("Typical");

        renderer.setLegendTextSize(28);
        renderer.setShowLegend(true);

//        renderer.setFitLegend(true);
        renderer.setLegendHeight(-200);
        renderer.setMargins(new int[] { 0, 0, 0, 0 });
        renderer.setFitLegend(true);
        renderer.setLegendHeight(-200);

        teller = 0;
        // fillChartWithDemoData_File("silvia_pid_warmup_110.txt", 0 );

        //if( filldemo.isAlive() )
        //    filldemo.stop();

        //filldemo.start();
        if( demo_thread != null ) {

            demo_runnable.stop_mecoffee();
            try {
                demo_thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        demo_runnable = new DemoThread( "56273d393cab0" );
        demo_thread = new Thread( demo_runnable );
        demo_thread.start( );

        //teller = 0;
        //fillChartWithDemoData_File("isomac_startup.txt", 1 );
    }

    public void write(String line) {

        if( connected_os == null )
            return;

        Log.i( TAG, "WRITE: " + line);


        // bt2.write(line.getBytes());
        try {

            connected_os.write(line.getBytes());

            connected_os.flush();
            // blehandler.flush_handler( new Message() );

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void updateStopped() {
        mHandler.obtainMessage(MainActivity.DEVICE_OFF, 0, -1, null ).sendToTarget();
    }

    private Handler mHandler;

    void setHandler(Handler h, MainActivity ma_ac) {

        Log.i( TAG, "setHandler" );
        mHandler = h;
        ma = ma_ac;

        // This is only here because SharedPreferences is not available at service creation
        SharedPreferences sharedPref =  PreferenceManager.getDefaultSharedPreferences( this );

        if( bt2 == null && sharedPref.getBoolean( "pref_bt_bt2_enabled", true ) ) {
            bt2 = new BTHandler(this);
        }

         if( blehandler == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && sharedPref.getBoolean( "pref_bt_ble_enabled", true ) ) {

             BluetoothManager mBluetoothManager =
                    (BluetoothManager) ma.getSystemService(Context.BLUETOOTH_SERVICE);

            blehandler = new BLEHandler(this, mBluetoothManager);
            //blehandler.setup();

            // warum?
            // bt2.ble_is = blehandler.is;
            // bt2.ble_os = blehandler.os;

            blehandler.logx = new Logx(ma, ma.getBaseContext());

            // do_firmware(); // TODO: currently only parses the firmware, move out of here

            blehandler.fw = fw_final2; // same

        }

        //if( bt2 != null )
        //    bt2.discover();

    }

    void stop() {
        //bt2.stop_loop();
    }

    private boolean write_flush_sleep( String line, int timeout ) {

        try {
            connected_os.write(line.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            connected_os.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if( timeout > 0 )
            try {
                Thread.sleep( timeout );
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        return true;

    }

    void do_firmware_2( String firmware_file ) {

        Logx logx = new Logx( ma, ma.getBaseContext() );

        if( connected_os == null || connected_is == null ) {

            Log.i(TAG, "do_firmware_2 - aborted, no connection");

            logx.logcat("No device connected.", "v");

            return;
        }

        Log.i(TAG, "do_firmware_2 - nieuwe stijl");

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(ma);
        if ( !settings.getString( "pref_fw_pin", "" ).equals( "6502" ) ) {
            logx.logcat("No soup for you: pincode not correct", "v");

            return;
        }

        if( bt2 != null )
            bt2.discover_stop( );

        load_firmware(firmware_file);

        // First reset enable might cause a reset
        Log.i( TAG, "First reset" );
        write_flush_sleep("\ncmd reset en\n", 5000);

        // So do it twice
        Log.i(TAG, "Second reset");
        write_flush_sleep("\ncmd reset en\n", 1000);

        firmware_start_stk500 = true;

        if( bt2 != null )
            bt2.close();

        if( blehandler != null )
            blehandler.close();

        if( bt2 != null )
        try {
            Thread.sleep( 1000 );
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if( bt2 != null )
            bt2.scan_connect();

        // TODO : BLE starten?
        // BLE start zichzelf via de close call ( code smell )

        // STK programmer will be started from the 'connected' callback
        return;

        /*

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(ma);
        if ( !settings.getString( "pref_fw_pin", "" ).equals( "6502" ) ) {
            logx.logcat( "No soup for you: request firmware pincode from info@mecoffee.nl", "v" );

            return;
        }

        */

        /*
        if( blehandler != null ) {
            do_firmware_2_ble( );

            return;
        }

        // stop();
        stop = true;

        try {
            Thread.sleep( 5000 );
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if( connected_is != null ) // todo: should not happen?
        try {
            while( connected_is.available() > 0 ) { // TODO: kan mogelijk weg als de STK500 impl dat al doet.
                connected_is.read();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // do_firmware(); // this only loads the hex file

        stop = false;

        // firmware_start_stk500 = true;

        try {
            bt2.upload_firmware(fw_final2, logx );
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


        */

    }

    void do_firmware_2_ble( ) {

        Log.i(TAG, "BLE Firmware situation");

        blehandler.start_firmware = true;

        blehandler.close( );

    }


    void load_firmware( String firmware_file ) {
        if( did_firmware ) {
            //showNotification("Firmware already flashed", "Firmware update");

            //return ;

        }

        did_firmware = true;

        AssetManager ast_mgr = getApplicationContext().getAssets();
        InputStream is_fw = null;
        try {
            is_fw = ast_mgr.open( firmware_file );
        }
        catch ( IOException e ) {
            showNotification("Firmware not found", "Firmware update");
        }

        byte[] fw_buffer = new byte[64000];
        byte[] fw_buffer2 = new byte[64000];

        int fw_ptr = 0, fw_ptr2 = 0;

        Scanner scanner = new Scanner(is_fw).useDelimiter("\r\n");
        String line;

        while(true) {
            try { line = scanner.next(); }
            catch(NoSuchElementException e) { break; }

            if( line.startsWith(":") ) {
                line = line.replace(":", "3A");

                for(int i=0; i < line.length() / 2; i++)
                    fw_buffer[fw_ptr + i] = Integer.decode("0x" + line.substring(2*i, 2*i+2)).byteValue();


                for(int i=0; i < line.length() / 2 - 6; i++)
                    fw_buffer2[fw_ptr2 + i] = Integer.decode("0x" + line.substring(2*(i+5), 2*(i+5)+2)).byteValue();

                fw_ptr += line.length() / 2;
                fw_ptr2 += line.length() / 2 - 6;
            }
        }

        /* byte[] */ fw_final = new byte[fw_ptr];
        for(int i=0; i<fw_ptr; i++)
            fw_final[i] = fw_buffer[i];

        /* byte[] */ fw_final2 = new byte[fw_ptr2];
        for(int i=0; i<fw_ptr2; i++)
            fw_final2[i] = fw_buffer2[i];
    }

    void configure_graph( ) {
        series[0] = new XYSeries("Setpoint");
        series[1] =  new XYSeries("Boiler");
        series[2] =  new XYSeries("Boiler #2");

        gdataset = new XYMultipleSeriesDataset();
        gdataset.addSeries( series[0] );
        gdataset.addSeries( series[1] );


        renderer = new XYMultipleSeriesRenderer();
        renderer.setAxisTitleTextSize(16);
        renderer.setChartTitleTextSize(20);
        renderer.setLabelsTextSize(20);
        // renderer.setLegendTextSize(15);
        renderer.setShowLegend(false);
        // renderer.setLegendHeight(50);
        renderer.setPointSize(5f);
        //renderer.setMargins(new int[] { 20, 30, -50, 0 });
        renderer.setMargins(new int[] { 0, 0, 0, 0 });
        // renderer.setMarginsColor(Color.TRANSPARENT);
        renderer.setMarginsColor(Color.argb(0x00, 0x01, 0x01, 0x01));

        renderer.setXAxisMin(-300);
        renderer.setXAxisMax(10);
        renderer.setYAxisMin(15);
        renderer.setYAxisMax(110);

        int graph_color = 0xff666666;

        renderer.setAxesColor( graph_color );
        renderer.setLabelsColor( graph_color );
        renderer.setAntialiasing(true);

        renderer.setYLabelsAlign(Paint.Align.LEFT);

        renderer.setXLabels(0);
        //renderer.setXLabelsColor( graph_color );
        renderer.setYLabels(10);
        //renderer.setYLabelsColor( 0, graph_color );
        renderer.setShowGrid(true);
        renderer.setGridColor(/* Color.DKGRAY 0xff666666  */graph_color);
        renderer.setShowCustomTextGridX(true);
        renderer.setXLabelsPadding(-300);

        renderer.setZoomInLimitX(60);
        renderer.setZoomInLimitY(20);
        renderer.setZoomLimits( new double[] { -30, 600, 0, 150 } );
        renderer.setPanLimits( new double[] { -300, 600, 0, 150 } );
        renderer.setZoomEnabled(true, true);

        XYSeriesRenderer r;

        // Setpoint
        r = new XYSeriesRenderer();
        r.setPointStyle(PointStyle.POINT);
        r.setColor(Color.parseColor("#4CAF50"));
        r.setLineWidth(4);
        r.setAnnotationsTextSize(50);
        r.setAnnotationsTextAlign(Paint.Align.LEFT);
        renderer.addSeriesRenderer(r);

        // Boiler
        r = new XYSeriesRenderer();
        r.setPointStyle(PointStyle.POINT);
        r.setColor(Color.parseColor("#2196F3"));
        r.setLineWidth(8);
        r.setAnnotationsTextSize(50);
        renderer.addSeriesRenderer(r);


        // Boiler #2
        // XYSeriesRenderer r_second_channel = new XYSeriesRenderer();

        gdataset.addSeries( series[2] );
        r = r_second_channel = new XYSeriesRenderer();

        r.setPointStyle(PointStyle.POINT);
        r.setColor(Color.parseColor("#d04040"));
        r.setLineWidth(8f);
        r.setAnnotationsTextSize(50);

        //
        renderer.addSeriesRenderer(r);
        //renderer.

        if(false) {
            // Group
            r = new XYSeriesRenderer();
            r.setPointStyle(PointStyle.POINT);
            r.setColor(Color.CYAN);
            r.setLineWidth(5f);
            renderer.addSeriesRenderer(r);

            // Reservoir
            r = new XYSeriesRenderer();
            r.setPointStyle(PointStyle.POINT);
            r.setColor(Color.YELLOW);
            r.setLineWidth(5f);
            renderer.addSeriesRenderer(r);

            // Probe
            r = new XYSeriesRenderer();
            r.setPointStyle(PointStyle.POINT);
            r.setColor(Color.WHITE);
            r.setLineWidth(2f);
            //renderer.
            renderer.addSeriesRenderer(r);

            // Board
            r = new XYSeriesRenderer();
            r.setPointStyle(PointStyle.POINT);
            r.setColor(Color.RED);
            r.setLineWidth(5f);
            renderer.addSeriesRenderer(r);
        }

    }
}
