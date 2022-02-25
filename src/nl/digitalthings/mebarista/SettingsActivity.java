package nl.digitalthings.mebarista;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.Map;

/**
 * Created by jan on 11/20/13.
 */


public class SettingsActivity extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "Settings";

    BaristaService mBoundService;

    private ServiceConnection mConnection = new ServiceConnection() {

        public void onServiceConnected(ComponentName className, IBinder service) {
            mBoundService = ((BaristaService.LocalBinder)service).getService();

            if( !mBoundService.legacy )
                addPreferencesFromResource(R.xml.preference);
            else
                addPreferencesFromResource(R.xml.preference_legacy);
        }

        public void onServiceDisconnected(ComponentName className) {
            mBoundService = null;
        }

    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);

        bindService(new Intent(SettingsActivity.this,
                BaristaService.class), mConnection, Context.BIND_AUTO_CREATE);

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            if( findPreference("pref_bt_ble_enabled") != null ) // .setEnabled(false);
                System.out.println( "jopiedopie" );
        }

       // this.getApplicationContext().getSharedPreferences( "zyx " ).
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        unbindService(mConnection);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // handle the preference change here
        //showNotification(key, "changed" );

        // TODO: why is this here? should move to baristaservice

        System.out.println("Changed: " + key);

        String val = "";
        Map<String,?> keys = sharedPreferences.getAll();

        for( Map.Entry<String,?> entry : keys.entrySet( ) ) {

            if( entry.getKey().equals( key ) ) {
                String entry_type = entry.getValue().getClass().getName();

                System.out.println( "type of " + key + " is " + entry_type );

                // Do something
                if( entry_type.equals("java.lang.Integer") || entry_type.equals("java.lang.Long") )
                    val = entry.getValue().toString();

                if( entry_type.equals( "java.lang.Float" ) ) {
                    Float value = (Float)entry.getValue();

                    val = "" + value.intValue();
                }

                if( entry_type.equals("java.lang.Boolean") )
                    val = (Boolean)entry.getValue() ? "1" : "0";

                if( entry_type.equals( "java.lang.String" ) )
                    val = entry.getValue( ).toString( );

                break;
            }

        }

        // publish the change to device
        String keystr = key.replace("pref_", "");

        if( ! ( keystr.startsWith( "bt_") || keystr.startsWith( "ui_" ) || keystr.startsWith( "support" ) ) ) {
            System.out.println("cmd set " + keystr + " " + val);
            // mBoundService.write(("cmd set " + keystr + " " + val + "\n")); // TODO: remove nl

            Intent i = new Intent(this, BaristaService.class);
            i.putExtra("action", "send" );
            i.putExtra("payload", "cmd set " + keystr + " " + val + "\n" );
            startService(i);
        }

        if( key.equals( "pref_bt_bt2_enabled" ) || key.equals( "pref_bt_ble_enabled" ) ) {

            //  mBoundService.scan( );
            Log.i( TAG, "BT setting changed? Start scan");

            Intent i = new Intent(this, BaristaService.class);
            i.putExtra("action", "scan" );
            startService(i);

        }
    }

}
