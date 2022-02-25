package nl.digitalthings.mebarista;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class BTHandler {

    private static final String TAG = "BT2";

    // bluetooth
    static String SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB";
    BluetoothAdapter mBluetoothAdapter;
    BluetoothSocket socket = null;
    private InputStream inStream;
    private OutputStream outStream;

    // service
    BaristaService service;

    // Discovery related state
    boolean no_connected = false;
    boolean discovery_allow = true;
    boolean discovery_running = false;
    boolean discovery_timer_running = false;
    Timer discovery_timer = new Timer();

    // Util
    SharedPreferences sharedPref;

    public BTHandler(BaristaService serv) {
        Log.i( TAG, "Constructor" );

        // mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        service = serv;
        inStream = null;
        outStream = null;
        socket = null;

        // not allowed here:
        // sharedPref =  PreferenceManager.getDefaultSharedPreferences(service);

        IntentFilter f1, f2, f3, f4, f5, f6; // Not sure if necessary

        // TODO: move over to connect or something
        service.ma.registerReceiver( mReceiver, f1 = new IntentFilter( BluetoothDevice.ACTION_FOUND ) ); // Don't forget to unregister during onDestroy
        service.ma.registerReceiver( mReceiver, f2 = new IntentFilter( BluetoothDevice.ACTION_PAIRING_REQUEST ) ); // Don't forget to unregister during onDestroy
        service.ma.registerReceiver( mReceiver, f3 = new IntentFilter( BluetoothDevice.ACTION_BOND_STATE_CHANGED ) ); // Don't forget to unregister during onDestroy
        service.ma.registerReceiver( mReceiver, f4 = new IntentFilter( BluetoothDevice.ACTION_ACL_CONNECTED ) ); // Don't forget to unregister during onDestroy
        service.ma.registerReceiver( mReceiver, f5 = new IntentFilter( BluetoothDevice.ACTION_ACL_DISCONNECTED)); // Don't forget to unregister during onDestroy
        service.ma.registerReceiver( mReceiver, f6 = new IntentFilter( BluetoothAdapter.ACTION_DISCOVERY_FINISHED)); // Don't forget to unregister during onDestroy
        service.ma.registerReceiver( mReceiver, f6 = new IntentFilter( BluetoothAdapter.ACTION_DISCOVERY_STARTED)); // Don't forget to unregister during onDestroy
    }

    // Called from BaristaService upon onDestroy
    public void close_object() {

        // TODO: or should it be done six times?
        // crasht soms : Receiver not registered service.ma.unregisterReceiver(mReceiver);

        discovery_timer.cancel();

    }

    // Connect wrappers
    private BluetoothSocket connect_secure(BluetoothDevice device) {
        BluetoothSocket socket = null;

        try {

                socket = device.createRfcommSocketToServiceRecord( UUID.fromString(SPP_UUID) );

        } catch (IOException e) {

                try { socket.close(); } catch (IOException e1) { }

                // Creating the socket failed: continue to iterate over bonded devices
                Log.e( TAG, "Secure SOCKET CREATION failed");

        }

        return socket;
    }

    private BluetoothSocket connect_insecure(BluetoothDevice device) {
        BluetoothSocket socket = null;

        try {

            socket = device.createInsecureRfcommSocketToServiceRecord(UUID.fromString(SPP_UUID));

        } catch (IOException e) {

                try { socket.close(); } catch (IOException e1) { }

                // Creating the socket failed: continue to iterate over bonded devices
                Log.e( TAG, "Insecure SOCKET CREATION failed");

        }

        return socket;
    }

    private BluetoothSocket connect_introspection(BluetoothDevice device) {
        BluetoothSocket socket = null;

        try {

                Class<?> clazz = device.getClass();
                Class<?>[] paramTypes = new Class<?>[] {Integer.TYPE};

                Method m = clazz.getMethod("createRfcommSocket", paramTypes);
                Object[] params = new Object[] {Integer.valueOf(1)};

                socket = (BluetoothSocket) m.invoke(device, params);

        } catch (InvocationTargetException e) {

            try { socket.close(); } catch (IOException e1) { }

            // Creating the socket failed: continue to iterate over bonded devices
            Log.e( TAG, "Introspection SOCKET CREATION failed");
        }

        catch (NoSuchMethodException e) {

            try { socket.close(); } catch (IOException e1) { }

            // Creating the socket failed: continue to iterate over bonded devices
            Log.e( TAG, "Introspection SOCKET CREATION failed");
        }
        catch (IllegalAccessException e) {

            try { socket.close(); } catch (IOException e1) { }

            // Creating the socket failed: continue to iterate over bonded devices
            Log.e( TAG, "Introspection SOCKET CREATION failed");
        }

        return socket;
    }

    public boolean scan_connect() {

        if( mBluetoothAdapter == null )
            return false;

        // SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(service);

        // SharedPreferences preference = service.getSharedPreferences( "preference", 0);

        // Iterate over bonded devices to find clients
        // while(!stop) {

            for(BluetoothDevice device : mBluetoothAdapter.getBondedDevices() ) {
            	String deviceName = device.getName();
            	
            	if( deviceName == null ) {
            		// getting friendly bluetooth name may fail
            		continue;
            	}
            	
                if( !service.is_meCoffee( deviceName ) )
                    continue;

                Log.i( TAG, "CONNECTING to " + deviceName);

                socket = null;

                // As per the Android documentation:
                // http://developer.android.com/reference/android/bluetooth/BluetoothSocket.html#connect()
                mBluetoothAdapter.cancelDiscovery();

                // Use the introspection variant ( for older devices ? )
                socket = connect_introspection( device );

                // Failed? Continue the loop
                if( socket == null )
                    continue;

                // Connect to socket
                if( !socket.isConnected() ) {
                    try {

                        socket.connect();

                    } catch (IOException e) {

                        System.out.println( e.getMessage() );

                        try { socket.close();  } catch(Exception ei) {  }

                        // Connecting the socket failed: continue to iterate over bonded devices
                        Log.e( TAG, "SOCKET CONNECT failed" );
                        continue;
                    }
                }

            // Create input and outputstreams
            try {

                inStream = socket.getInputStream();
                outStream = socket.getOutputStream();

            } catch (IOException e1) {

                // Getting the streams failed: continue to iterate over bonded devices
                try { socket.close();  } catch(Exception ei) {}

                Log.e( TAG, "STREAMs failed" );
                continue;
            }

            // Check for nulls
            if( inStream == null || outStream == null) {
                try { socket.close();  } catch(Exception ei) {}

                continue;
            }

            // succeeded: break out of this function by returning...
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(service);

            SharedPreferences.Editor editor = settings.edit();
            editor.putString("pref_bt_devicename", device.getName());
            editor.commit();

                if( !no_connected )
                    service.connected( inStream, outStream );

            return true;

        } // bonded_devices

        // Nothing connected
        return false;

        //    try { Thread.sleep(5000); } catch (InterruptedException e) {  }

        // } // while true

    }  // scan_connect

    // --- AUTO pairing ---

    // http://stackoverflow.com/questions/9608140/how-to-unpair-or-delete-paired-bluetooth-device-programmatically-on-android
    private void unpairDevice(BluetoothDevice device) {
        try {
            Method method = device.getClass().getMethod("removeBond", (Class[]) null);
            method.invoke(device, (Object[]) null);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // http://stackoverflow.com/questions/17168263/how-to-pair-bluetooth-device-programmatically-android
    // http://developer.android.com/guide/topics/connectivity/bluetooth.html
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            // TODO: get his out of here, should only happen once
            sharedPref =  PreferenceManager.getDefaultSharedPreferences(service);

            switch( action ) {

                case BluetoothAdapter.ACTION_DISCOVERY_STARTED:

                    // TODO:
                    Log.i( TAG, "Discovery started" );
                    discovery_running = true; // is this even useful ?

                    service.spinner( true );
                    break;

                case BluetoothAdapter.ACTION_DISCOVERY_FINISHED:
                    Log.i( TAG, "Discovery finished" );
                    discovery_running = false;

                    service.spinner( false );

                    if( inStream != null || !discovery_allow || discovery_running )
                        return;

                    if( !sharedPref.getBoolean( "pref_bt_keepdiscovering", false ) )
                        return;

                    if( !mBluetoothAdapter.isDiscovering() && !discovery_timer_running )

                        Log.i(TAG, "Restarting timer for new discovery in " + 60 + " seconds");

                        discovery_timer_running = true;

                        discovery_timer.schedule(new TimerTask() {
                            @Override
                            public void run() {

                                if( ! ( mBluetoothAdapter.isDiscovering() || discovery_running ) ) {
                                    if( inStream == null ) {
                                        Log.i("BT2", "Restarting discovery from timer" );

                                        mBluetoothAdapter.startDiscovery();
                                    }
                                }
                                else
                                    Log.i("BT2", "Discovery from timer: discovery was already running?" );

                                discovery_timer_running = false;

                            }
                        }, 60*1000 );

                    break;

                case BluetoothDevice.ACTION_ACL_CONNECTED:
                    Log.i( TAG, "ACL Connected" );

                    // TODO: set flag
                    break;

                case BluetoothDevice.ACTION_ACL_DISCONNECTED:

                    Log.i(TAG, "ACL Disconnected");

                    // TODO: if we were not connected: do nothing ( because spurious messages on app startup )

                    if( inStream != null )
                    try {
                        inStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    if( outStream != null )
                        try {
                            outStream.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                    if( socket != null )
                        try {
                            socket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                    inStream = null;
                    outStream = null;
                    service.connected( inStream, outStream );

                    if( !sharedPref.getBoolean( "pref_bt_keepdiscovering", false ) || !discovery_allow || discovery_running )
                        return;

                    if( !mBluetoothAdapter.isDiscovering() ) {

                        Log.i("BT2", "Restarting discovery from disconnect" );
                        mBluetoothAdapter.startDiscovery();

                    }
                    else
                        Log.i("BT2", "Discovery from disconnect: was already running?" );

                    break;

                case BluetoothDevice.ACTION_FOUND:
                    {
                        discovery_running = true; // apparently we are discovering

                        BluetoothDevice device_discovered = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                        Log.i( TAG, "discovered " + device_discovered.getName());

                        // Do not take action if we are currently connected
                        if( inStream != null )
                            return;

                        // Only service BT2
                        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&  device_discovered.getType() != BluetoothDevice.DEVICE_TYPE_CLASSIC )
                            return;

                        // Only service devices which name is right
                        if( !service.is_meCoffee( device_discovered.getName() ) )
                            return;

                        Log.i( TAG, "Checking device" );
                        // If we are currently connected to another device, do nothing
                        // TODO

                        // If already bonded to this device, do nothing
                        if( device_discovered.getBondState() == BluetoothDevice.BOND_BONDED ) {
                            Log.i( TAG, "Already bonded" );

                            scan_connect();

                            return;
                        }

                        // We are trying to pair to a new device, stop discovering
                        // http://stackoverflow.com/questions/16326750/cant-cancel-bluetooth-discovery-process
                        // mBluetoothAdapter.cancelDiscovery(); according to SO link, unreliable

                        // Break existing bonds
                        for(BluetoothDevice device_bonded : mBluetoothAdapter.getBondedDevices() ) {

                            if( service.is_meCoffee( device_bonded.getName() ) ) {

                                Log.i( TAG, "Unbonded " + device_bonded.getName() );
                                unpairDevice( device_bonded );

                            }

                        }

                        mBluetoothAdapter.cancelDiscovery();

                        Log.i( TAG, "Pairing" );
                        // Programmatically pair with the device

                        if( !device_discovered.setPin( "4321".getBytes() ) )
                            Log.i( TAG, "setPin failed" );

                        try {
                            if (!device_discovered.setPairingConfirmation(false))
                                Log.i(TAG, "setPairingConfirmation failed");
                        }
                        catch( Exception e ) {
                            Log.i(TAG, "setPairingConfirmation exception");
                        }

                        if ( !device_discovered.createBond() ) {

                            Log.i( TAG, "createBond failed: restarting discovery" );

                            mBluetoothAdapter.startDiscovery();
                        }

                    }
                    break;

                case BluetoothDevice.ACTION_PAIRING_REQUEST:
                    Log.i( TAG, "ACTION_PAIRING_REQUEST" );
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                    // device.setPairingConfirmation(false);
                    device.setPin( "4321".getBytes() );
                    break;

                case BluetoothDevice.ACTION_BOND_STATE_CHANGED:
                    if( intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1) == BluetoothDevice.BOND_BONDED )
                        scan_connect();
                    break;

                default:
                    Log.e( TAG, "unknown action: " + action );
                    break;
            }

        }
    };



    public void discover() {

        discovery_allow = true;

        if( mBluetoothAdapter == null )
            return;

        if( scan_connect() ) {
            Log.i( TAG, "Connected already paired device" );
            return ;
        }

        Log.i(TAG, "No connected device was able to connect or found, discovering");
        mBluetoothAdapter.startDiscovery();

    }

    public void discover_stop() {
/*
        try {
            service.ma.unregisterReceiver(mReceiver);
        }
        catch( Exception e ) {
            // TODO: do better on mReceiver
        }
*/
        discovery_allow = false;

        mBluetoothAdapter.cancelDiscovery();

        discovery_timer.cancel();

        discovery_timer = new Timer();
    }

    void close() {
        Log.i( TAG, "Closing" );
        if( inStream != null )
        try {
            inStream.close();
            inStream = null;
        } catch (Exception e) {
            e.printStackTrace();
        }
        if( outStream != null )
        try {
            outStream.close();
            outStream = null;

        } catch (Exception e) {
            e.printStackTrace();
        }

        if( socket != null )
            try { socket.close(); socket = null; } catch (IOException e1) { }

        // TODO: waarom service niet informeren?

    }

    public void write(byte[] bytes) {

        if(outStream != null) {

            try {
                outStream.write(bytes);
                outStream.flush();
            } catch (IOException e) {

                e.printStackTrace();

            }
        }
        else
            Log.i( TAG, "outStream null, not writing" );

    }

}