package nl.digitalthings.mebarista;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelUuid;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;

// http://stackoverflow.com/questions/825732/how-can-i-implement-an-outputstream-that-i-can-rewind
// https://github.com/robokoding/STK500
// https://github.com/ksksue/PhysicaloidLibrary

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class BLEHandler {

    private static final String TAG = "BLE";

    public class ble_os extends OutputStream {

        byte[] queue = new byte[20];
        int queued = 0;

        @Override
        public void write( int b ) throws IOException {

            queue[ queued++ ] = (byte)b;

            if( queued >= queue.length )
                flush( );

        }

        @Override
        public void flush( ) throws IOException {

            // If queue is empty, abort
            if( queued == 0 )
                return;

            // Prepare message by copying data in
            Message m = new Message();
            m.obj = Arrays.copyOfRange( queue, 0,  queued );

            // Clear queue once copied
            queued = 0;
            for( int i = 0; i < queue.length; i++ )
                queue[ i ] = 0;

            // Test or wait for the output channel to be free
            try {
                output_wait.acquire();
            } catch (InterruptedException e) {
                throw new IOException( e.getMessage( ) );
            }

            // Hand the actual sending over to the BLE owning thread
            write_handler.sendMessage( m );

        }

    }

    public class ble_is extends InputStream {

        @Override
        public int read () throws IOException {

            // Test or wait for data being available
            try {
                ble_available.acquire( );
            } catch (InterruptedException e) {
                throw new IOException( e.getMessage() );
            }

            // Fetch first byte from queue and remove it
            int res = input_sb.charAt( 0 );
            input_sb.delete( 0, 1 );

            return res;

        }

        @Override
        public int available( ) {

            return input_sb.length( );

        }

    }

    // service
    BaristaService service;

    // BLE
    BluetoothGattService mBluetoothGattService;
    BluetoothGatt mBluetoothGatt;
    BluetoothGattCharacteristic characteristic;
    BluetoothDevice bledevice;

    StringBuffer input_sb;
    BluetoothManager btmanager;
    public ble_os os;
    public ble_is is;
    Logx logx;
    byte[] fw;
    private final Semaphore ble_available = new Semaphore( 40960, false ), output_wait = new Semaphore( 1, false );
    boolean connecting = false;

    boolean start_firmware = false;

    Handler write_handler = new Handler() {

        @Override
        public void handleMessage( Message msg ) {

            if( characteristic == null || mBluetoothGatt == null )
                return;

            characteristic.setValue( ( byte[] ) msg.obj );

            if( !mBluetoothGatt.writeCharacteristic( characteristic ) )
                Log.e( TAG, "write failed" );

        }

    };

    public Handler flush_handler = new Handler() {

        @Override
        public void handleMessage( Message msg ) {
            try {
                os.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    };

    // final BluetoothManager mBluetoothManager;

    public BLEHandler( BaristaService serv, BluetoothManager ble_manager) {
        // mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);



        service = serv;
        btmanager = ble_manager;
        input_sb = new StringBuffer();
         is = new ble_is();
        os = new ble_os();

        ble_available.drainPermits( );
    }



    // https://developer.android.com/guide/topics/connectivity/bluetooth-le.html
    // http://stackoverflow.com/questions/24780714/hm-10-bluetooth-module-ble-4-0-keep-losing-connection
    // http://stackoverflow.com/questions/28018722/android-could-not-connect-to-bluetooth-device-on-lollipop

    final BluetoothGattCallback bgc = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {

            if (newState == BluetoothProfile.STATE_CONNECTED) {

//                 BLE_stop();

                Log.i(TAG, "STATE_CONNECTED");

                // mBluetoothGatt.discoverServices();

                mBluetoothAdapter.stopLeScan( mLeScanCallback );

                bledevice = gatt.getDevice();
                gatt.discoverServices();

            }

            if( newState == BluetoothProfile.STATE_DISCONNECTED ) {

                Log.i(TAG, "STATE_DISCONNECTED");

        //        gatt.close();

                if( bledevice == null ) {
                    Log.i( TAG, "STATE_DISCONNECTED: was already disconnected, skipping" );

                    return;
                }

                if( characteristic != null ) {
                gatt.setCharacteristicNotification( characteristic, false );
                    characteristic = null; }

 //gatt.close();

               mBluetoothGatt.close();

                mBluetoothGatt = null;

                bledevice = null;

                // gatt.setCharacteristicNotification( characteristic, false ); // Prevents double notifications if reconnected

                // connecting = false;
                // bledevice = null;

                service.connected( null, null );

                // TODO: moet dit wel?

                //BluetoothAdapter mBluetoothAdapter = btmanager.getAdapter(); // BluetoothAdapter.getDefaultAdapter();

                if( !mBluetoothAdapter.isDiscovering() ) {

                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    // mBluetoothAdapter.startLeScan(new UUID[]{UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")}, mLeScanCallback); // TODO, when to stop
                    setup();
                }

            }

            Log.i( TAG, "connectionstatechange");

        }

        @Override
        // Characteristic notification
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {


            String out, v = characteristic.getStringValue( 0 );
/*
            out = "";
            for(int i =0; i< v.length(); i++ )
                out += Integer.toHexString( (int)v.charAt( i ) );
            System.out.println("char:" + out);
            System.out.println(v); */


            String char_string = characteristic.getStringValue(0);
            input_sb.append(char_string);
            ble_available.release(char_string.length());

        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic char2, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS)
                Log.e( TAG, "write failed");

            output_wait.release();
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                List<BluetoothGattService> gattServices = gatt.getServices();

                for (BluetoothGattService gattService : gattServices) {

                    if ( gattService.getUuid().toString().startsWith( "0000ffe0" ) ) {
                        // if ("0000ffe0-0000-1000-8000-00805f9b34fb".equals(gattService.getUuid().toString())) {

                        //mBluetoothGattService = gattService;
                        Log.i( TAG, "Service found");

                        if( characteristic != null ) {
                            Log.i( TAG, "Chararacterics already set" );

                            gatt.setCharacteristicNotification(characteristic, false);

                            characteristic = null;
                        }

//                        if( characteristic != null )
  //                          gatt.setCharacteristicNotification(characteristic, false);

                        characteristic = gattService.getCharacteristic(UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb"));

                        if( characteristic == null ) {
                            Log.e( TAG, "Characteristic null : not found" );
                            continue;
                        }

                        //if( characteristic.getWriteType() == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE ) {
                        //    return ;
                        //}

                        gatt.setCharacteristicNotification(characteristic, true);
                        characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                        if (!gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH))
                            Log.e( TAG, "no req conn prio");

                        // now we have working input- & output-stream's
                        // let's run the firmware update if required

                        Log.i( TAG, "Connected?" );

                        //
                        // tijdelijk uit totdat FW flashing werkt. service.connected( is, os );

                        service.connected(is, os);

                    } else
                        Log.i( TAG, "more service: " + gattService.getUuid().toString());
                }
            } else {
                Log.i(TAG, "onServicesDiscovered received: " + status);
            }
        }
    };

    final BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi,
        byte[] scanRecord) {

            if( connecting ) {
                Log.i( TAG, "meLEScanCallback : backed out connecting" );

                return;
            }

            if( device.getName() == null ) {
                Log.i( TAG, "meLEScanCallback : backed out device name null" );

                return;
            }

            if( !device.getName().startsWith("meCoffee") ) {
                Log.i( TAG, "meLEScanCallback : backed out device name not meCoffee* but " + device.getName( ) );

                return;
            }

            if( device == bledevice ) {
                Log.i(TAG, "mLeScanCallBack: backed out, already connected to this device");

                return ;
            }

            BluetoothAdapter mBluetoothAdapter = btmanager.getAdapter(); // repeated everywhere because unsure if this is the right call in the right context
            mBluetoothAdapter.cancelDiscovery();

            Method connectGattMethod = null;


            try {
                Log.i( TAG, "connectGatt -> "  + device.getName() );

                // connecting = true;

                bledevice = device;

                connectGattMethod = device.getClass().getMethod("connectGatt", Context.class, boolean.class, BluetoothGattCallback.class, int.class);


            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            }

            try {

                // https://stackoverflow.com/questions/22214254/android-ble-connect-slowly : about second boolean, was true, changed to false

                mBluetoothGatt = (BluetoothGatt) connectGattMethod.invoke(device, service.getApplicationContext(), false, bgc, /* TRANSPORT_LE */ 2);
                //    mBluetoothGatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }

        }
    };

    class MyScanCallback extends ScanCallback {
//Fields and constructor omitted

        @Override
        public void onScanResult(int callbackType, ScanResult result) {

            //Will execute on the main thread!
            //Do the work below on a worker thread instead!

            //if (showldConnect(result)) {
            //    BluetoothDevice device = result.getDevice();
            //    scaner.stopScan(this);
            //    device.connectGatt(context, false, gattCallback);
           // }

            BluetoothDevice device = result.getDevice();

            Log.i( TAG, "myScanCallback::onScanResult: " + device.getName() );

            if( device != null ) {

                // https://stackoverflow.com/questions/33274009/how-to-prevent-bluetoothgattcallback-from-being-executed-multiple-times-at-a-tim

                bluetoothLeScanner.stopScan( this );
                //bluetoothLeScanner.

//                if( mBluetoothGatt == null /* || mBluetoothGatt.getDevice() != device */ )
                    mBluetoothGatt = device.connectGatt( service.getApplicationContext(), false, bgc );
//                else
//                    mBluetoothGatt.connect();
            }

        }

    }

    BluetoothAdapter mBluetoothAdapter;
    BluetoothLeScanner bluetoothLeScanner;

    //@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void setup( ) {

        Log.i( TAG, "BLE Started");

        // BLE
        if( mBluetoothAdapter == null )
            mBluetoothAdapter = btmanager.getAdapter(); // BluetoothAdapter.getDefaultAdapter();



        // Static device picking
        //byte[] address = new byte[] { (byte)0x00, (byte)0x17, (byte)0xEA, (byte)0x93, (byte)0x9F, (byte)0x75}; // V7
        //byte[] address = new byte[] { (byte)0x00, (byte)0x17, (byte)0xEA, (byte)0x93, (byte)0xA3, (byte)0x8B};
        //BluetoothDevice device = mBluetoothAdapter.getRemoteDevice( address );

        // https://intersog.com/blog/tech-tips/how-to-work-properly-with-bt-le-on-android/

        ScanSettings scanSettings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        //If name or address of peripheral is known
        ScanFilter scanFilter = new ScanFilter.Builder()
                .setServiceUuid( new ParcelUuid( UUID.fromString( "0000ffe0-0000-1000-8000-00805f9b34fb") ) )
                .build();

        // BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();

        /* BluetoothLeScanner */
        if( bluetoothLeScanner == null )
            bluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();

        if( bluetoothLeScanner != null ) {

            bluetoothLeScanner.startScan(Collections.singletonList(scanFilter), scanSettings, new MyScanCallback());

        }
        else {

                mBluetoothAdapter.startLeScan(new UUID[]{UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")}, mLeScanCallback); // TODO, when to stop ?

        }

        Log.i(TAG, "startScan()");

        // API ?? how did this work?
        // mBluetoothGatt = device.connectGatt(service, true, bgc, BluetoothDevice.TRANSPORT_LE );

        /*


        Method connectGattMethod = null;

        try {
            //connectGattMethod = device.getClass().getMethod("connectGatt", Context.class, boolean.class, BluetoothGattCallback.class, int.class);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }

        try {
            //mBluetoothGatt = (BluetoothGatt) connectGattMethod.invoke(device, service.getApplicationContext(), true, bgc,  TRANSPORT_LE  2);
         //    mBluetoothGatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }

    }
 */

    }

    public void discover( ) {
        System.out.println( "BLE discover" );

        setup( ); // TOD: is dit wel nodig?
    }

    public void close() {

        Log.i(TAG, "Close");

        BluetoothAdapter mBluetoothAdapter = btmanager.getAdapter(); // repeated everywhere because unsure if this is the right call in the right context

        mBluetoothAdapter.cancelDiscovery();

        if( mBluetoothGatt != null) {
            if( characteristic != null )
                mBluetoothGatt.setCharacteristicNotification(characteristic, false);

            mBluetoothGatt.disconnect();

            // mBluetoothGatt.close();
        }

        service.connected(null, null);

        mBluetoothAdapter.startDiscovery();

    }

}
