package nl.digitalthings.mebarista;

import android.content.Context;
import android.widget.Toast;

public class Logx  {

    Context ctxt;
    MainActivity main;
    public static final String TAG = "BT-for-STK";

    public Logx(MainActivity main, Context ctxt){
        this.ctxt = ctxt;
        this.main = main;
    }

    /** prints a msg on the UI screen **/
    public void makeToast(String msg){
        final String out = msg;
        main.runOnUiThread(new Runnable() {

            @Override
            public void run() {
                Toast.makeText(ctxt, out, Toast.LENGTH_SHORT).show();

            }
        });
    }

    public void printToConsole(String msg){
        final String out = msg;
        main.runOnUiThread(new Runnable() {

            @Override
            public void run() {

                // System.out.println(out);
                // main.printToConsole(out);
                main.log(out);

            }
        });
    }

    public void logcat(String msg, String level) {
        if (level.equals("v")) {
            android.util.Log.v(TAG, msg);
            main.log(msg);
        }
        else if (level.equals("d")){
            android.util.Log.d(TAG, msg);
            main.log(msg);
        }
        else if (level.equals("i")) {
            android.util.Log.i(TAG, msg);
            main.log(msg);
        }
        else if (level.equals("w")) {
            android.util.Log.w(TAG, msg);
            main.log(msg);
        }
        else if (level.equals("e")) {
            android.util.Log.e(TAG, msg);
            main.log(msg);
        }
    }
}